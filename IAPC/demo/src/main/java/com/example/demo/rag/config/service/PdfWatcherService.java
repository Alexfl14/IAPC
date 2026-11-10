package com.example.demo.rag.config.service;

import com.example.demo.rag.config.data.RagConfigData;
import com.example.demo.rag.config.exception.RagException;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;


@Component
public class PdfWatcherService implements SmartLifecycle {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(PdfWatcherService.class);

    private final RagIngestionService ingestion;
    private final RagConfigData ragConfig;
    private final Executor watchLoop;
    private final ScheduledExecutorService debounce;
    private final ObservationRegistry registry;

    private volatile boolean running = false;
    private java.nio.file.WatchService watchService;
    private final Map<Path, ScheduledFuture<?>> scheduled = new java.util.concurrent.ConcurrentHashMap<>();


    public PdfWatcherService(RagIngestionService ingestion,
                             RagConfigData ragConfig,
                             @Qualifier("traceableWatchLoopExecutor") Executor watchLoop,
                             @Qualifier("traceableScheduledExecutorService") ScheduledExecutorService debounce,
                             ObjectProvider<ObservationRegistry> observationRegistryProvider) {
        this.ingestion = ingestion;
        this.ragConfig = ragConfig;
        this.watchLoop = watchLoop;
        this.debounce = debounce;
        this.registry = observationRegistryProvider.getIfAvailable(() -> ObservationRegistry.NOOP);
    }

    @Override
    public boolean isAutoStartup() {
        return ragConfig.getPdf().isWatchEnabled();
    }

    @Override
    public void start() {
        Observation pdfWatcherObservability = Observation.start("rag.pdf.dir.watcher", registry);
        try (Observation.Scope scope = pdfWatcherObservability.openScope()) {
            doStart();
        } finally {
            pdfWatcherObservability.stop();
        }
    }

    private void doStart() {
        try {
            Path dir = resolveWatchedDir();       // the directory, not the *.pdf glob
            log.info("Starting PDF folder watcher for dir: {}", dir);
            this.watchService = dir.getFileSystem().newWatchService();
            dir.register(watchService,
                    java.nio.file.StandardWatchEventKinds.ENTRY_CREATE,
                    java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY,
                    java.nio.file.StandardWatchEventKinds.ENTRY_DELETE);
            running = true;
            watchLoop.execute(() -> {
                try {
                    log.info("PDF watcher loop started for dir: {}", dir);
                    this.watchDir();
                } catch (Exception e) {
                    log.error("PDF watcher loop terminated with error", e);
                }
            });
        } catch (Exception e) {
            throw new RagException("Failed to start PDF watcher", e);
        }
    }

    private Path resolveWatchedDir() {
        String configuredPath = ragConfig.getPdf().getPath();
        if (configuredPath == null || configuredPath.isBlank()) {
            throw new RagException("PDF watcher path must be configured");
        }
        if (configuredPath.startsWith("classpath:")) {
            throw new RagException("PDF watcher requires a filesystem path; classpath resources cannot be watched");
        }

        // Strip the glob before constructing a Path: Windows rejects '*' and '?'.
        String pattern = configuredPath.replaceFirst("^file:", "").replace('\\', '/');
        int glob = pattern.indexOf('*');
        int question = pattern.indexOf('?');
        if (glob < 0 || (question >= 0 && question < glob)) glob = question;
        String directoryName = glob >= 0
                ? pattern.substring(0, Math.max(0, pattern.lastIndexOf('/', glob)))
                : pattern;
        if (directoryName.isBlank()) directoryName = ".";
        Path directory = java.nio.file.Paths.get(directoryName).toAbsolutePath().normalize();
        try {
            java.nio.file.Files.createDirectories(directory);
        } catch (IOException e) {
            throw new RagException("Cannot create PDF directory: " + directory, e);
        }
        return directory;
    }

    private void watchDir() {
        while (running) {
            try {
                var key = watchService.take();
                Path dir = (Path) key.watchable();
                for (var event : key.pollEvents()) {
                    var kind = event.kind();
                    if (kind == java.nio.file.StandardWatchEventKinds.OVERFLOW) {
                        continue;
                    }

                    @SuppressWarnings("unchecked")
                    Path relative = ((java.nio.file.WatchEvent<Path>) event).context();
                    Path path = dir.resolve(relative);

                    // Only care about .pdf files
                    if (!relative.toString().toLowerCase().endsWith(".pdf")) {
                        continue;
                    }

                    // Debounce per file (e.g., 1s)
                    scheduled.compute(path, (p, pending) -> {
                        if (pending != null) pending.cancel(false);  // cancel any timer already queued for this file
                        return debounce.schedule(() -> handleEvent(kind, p), 1, java.util.concurrent.TimeUnit.SECONDS); // schedule a new one and run only if no new events for 1s
                    });
                }
                boolean valid = key.reset();
                if (!valid) {
                    log.warn("WatchKey no longer valid for {}", dir);
                    break; // or try to re-register the directory
                }
            } catch (java.nio.file.ClosedWatchServiceException closed) { break; } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                log.warn("Watcher is interrupted!");
                break;
            } catch (Exception e) {
                log.warn("Error in watcher loop!", e);
            }
        }
    }

    private void handleEvent(java.nio.file.WatchEvent.Kind<?> kind, Path path) {
        try {
            log.info("Received event:{} -> for file:{}", kind.name(), path.getFileName());
            if (kind == java.nio.file.StandardWatchEventKinds.ENTRY_DELETE) {
                // remove from store
                ingestion.deleteBySource(path.getFileName().toString());
            } else {
                // create/modify → upsert this one file
                ingestion.upsertOneByPath(path);
            }
        } catch (Exception e) {
            log.error("Could not handle event {} for file {}", kind.name(), path.getFileName(), e);
        }
    }

    @Override
    public void stop() {
        running = false;
        try {
            if (watchService != null) {
                watchService.close();
            }
        } catch (IOException e) {
            log.warn("Could not close PDF watch service cleanly", e);
        } finally {
            debounce.shutdownNow();
        }
    }

    @Override
    public boolean isRunning() {
        return running;
    }
}


