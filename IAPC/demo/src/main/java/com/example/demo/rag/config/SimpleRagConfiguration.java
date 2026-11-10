package com.example.demo.rag.config;

import com.example.demo.rag.config.data.RagConfigData;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.reader.TextReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

@Configuration
public class SimpleRagConfiguration {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(SimpleRagConfiguration.class);
    private final RagConfigData config;

    public SimpleRagConfiguration(RagConfigData config) {
        this.config = config;
    }

    @Bean
    public TokenTextSplitter tokenTextSplitter() {
        var chunk = config.getChunk();
        return TokenTextSplitter.builder()
                .withChunkSize(chunk.getSize())
                .withMinChunkSizeChars(chunk.getMinChunkSize())
                .withMinChunkLengthToEmbed(chunk.getMinChunkToEmbed())
                .withMaxNumChunks(chunk.getMaxNumChunks())
                .withKeepSeparator(chunk.isKeepSeparator())
                .build();
    }

    @Bean
    public SimpleVectorStore simpleVectorStore(EmbeddingModel embeddingModel,
                                               TokenTextSplitter splitter,
            @org.springframework.beans.factory.annotation.Value("${spring.ai.ollama.embedding.options.model}") String model) throws IOException {
        var store = SimpleVectorStore.builder(embeddingModel).build();
        var resources = new PathMatchingResourcePatternResolver().getResources(config.getFilesPath());
        Arrays.sort(resources, Comparator.comparing(Resource::getDescription));
        Path index = Path.of(config.getVectorStorePath()).toAbsolutePath();
        Path stamp = index.resolveSibling(index.getFileName() + ".sha256");
        String fingerprint = fingerprint(resources, model);
        if (!config.isForceRebuild() && Files.exists(index) && Files.exists(stamp)
                && Files.readString(stamp).equals(fingerprint)) {
            try {
                store.load(index.toFile());
                log.info("Loaded handbook vector store from {}", index);
                return store;
            } catch (RuntimeException error) {
                log.warn("Cannot load cached index; rebuilding", error);
            }
        }
        List<Document> documents = new ArrayList<>();
        for (Resource resource : resources) {
            TextReader reader = new TextReader(resource);
            String filename = Objects.requireNonNullElse(resource.getFilename(), "unknown.md");
            reader.getCustomMetadata().put("category", filename.replaceFirst("\\.md$", ""));
            reader.getCustomMetadata().put("source", filename);
            reader.getCustomMetadata().put("access_level", "public");
            reader.getCustomMetadata().put("version", "2026.01");
            documents.addAll(reader.read());
        }
        if (documents.isEmpty()) {
            throw new IllegalStateException("No handbook documents found: " + config.getFilesPath());
        }
        store.add(splitter.apply(documents));
        Files.createDirectories(index.getParent());
        store.save(index.toFile());
        Files.writeString(stamp, fingerprint);
        log.info("Built handbook vector store from {} documents", documents.size());
        return store;
    }

    private String fingerprint(Resource[] resources, String model) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            var chunk = config.getChunk();
            String settings = model + ":" + chunk.getSize() + ":" + chunk.getMinChunkSize()
                    + ":" + chunk.getMinChunkToEmbed() + ":" + chunk.getMaxNumChunks() + ":" + chunk.isKeepSeparator();
            digest.update(settings.getBytes(StandardCharsets.UTF_8));
            for (Resource resource : resources) {
                digest.update(resource.getDescription().getBytes(StandardCharsets.UTF_8));
                try (var input = resource.getInputStream()) { digest.update(input.readAllBytes()); }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException(error);
        }
    }
}
