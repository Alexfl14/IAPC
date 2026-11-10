package com.example.demo.rag.config.service;

import com.example.demo.rag.config.data.PgVectorStoreConfigData;
import com.example.demo.rag.config.data.RagConfigData;
import com.example.demo.rag.config.data.RagConstants;
import com.example.demo.rag.config.exception.RagException;

import org.springframework.ai.document.Document;
import org.springframework.ai.reader.ExtractedTextFormatter;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.ai.reader.pdf.ParagraphPdfDocumentReader;
import org.springframework.ai.reader.pdf.config.PdfDocumentReaderConfig;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;


@Component
public class RagIngestionService {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(RagIngestionService.class);

    private final VectorStore vectorStore;
    private final JdbcTemplate jdbcTemplate;
    private final RagConfigData ragConfigData;
    private final PdfDocumentReaderConfig pdfDocumentReaderConfig;
    private final TokenTextSplitter tokenTextSplitter;
    private final String ragVectorStoreTableName;

    public RagIngestionService(@Qualifier("ragVectorStore") VectorStore vectorStore,
                               JdbcTemplate jdbcTemplate,
                               RagConfigData ragConfigData,
                               TokenTextSplitter tokenTextSplitter,
                               PgVectorStoreConfigData pgVectorStoreConfigData) {
        this.vectorStore = vectorStore;
        this.jdbcTemplate = jdbcTemplate;
        this.ragConfigData = ragConfigData;
        //Build a simple PDF reading config
        RagConfigData.PdfProperties pdfProperties = ragConfigData.getPdf();
        this.pdfDocumentReaderConfig = PdfDocumentReaderConfig.builder()
                .withPagesPerDocument(pdfProperties.getPagesPerDocument())
                .withPageExtractedTextFormatter(
                        ExtractedTextFormatter.builder()
                                .withLeftAlignment(pdfProperties.isLeftAlignment())
                                .withNumberOfTopTextLinesToDelete(pdfProperties.getNumberOfTopTextLinesToDelete())
                                .withNumberOfBottomTextLinesToDelete(pdfProperties.getNumberOfBottomTextLinesToDelete())
                                .build()
                )
                .build();
        this.tokenTextSplitter = tokenTextSplitter;
        this.ragVectorStoreTableName = pgVectorStoreConfigData.getTableNameForRag();
    }

    @org.springframework.transaction.annotation.Transactional
    public synchronized void initializePgVectorStore() {
        if (ragConfigData.isForceRebuild()) {
            jdbcTemplate.update("TRUNCATE TABLE " + ragVectorStoreTableName);
        }
        var resources = getResources();
        var currentSources = new java.util.HashSet<String>();
        if (resources != null) {
            for (Resource resource : resources) {
                currentSources.add(resource.getFilename());
                upsertResource(resource);
            }
        }
        // Also remove PDFs deleted while the application was stopped.
        for (String source : jdbcTemplate.queryForList(
                "SELECT DISTINCT metadata->>'source' FROM " + ragVectorStoreTableName, String.class)) {
            if (!currentSources.contains(source)) deleteBySource(source);
        }
    }
    @org.springframework.transaction.annotation.Transactional
    public synchronized void upsertOneByPath(Path path) {
        log.info("Processing new path for ingestion: {}", path);
        upsertResource(new FileSystemResource(path.toFile()));
    }

    private void upsertResource(Resource resource) {
        String source = resource.getFilename();
        String checksum = sha256Hex(resource);
        // If same checksum already in DB, skip
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + ragVectorStoreTableName + " WHERE metadata->>'source'=? AND metadata->>'checksum'=?",
                Integer.class, source, checksum);

        if (count != null && count > 0) {
            log.warn("Skipping new entry: Checksum already in DB!");
            return;
        }
        deleteBySource(source);
        ingestDocumentChunksToVectorStore(new Resource[] {resource});
    }

    public void deleteBySource(String source) {
        try {
            jdbcTemplate.update("DELETE FROM " + ragVectorStoreTableName + " WHERE metadata->>'source' = ?", source);
            log.info("Deleted source: {} from vector store", source);
        } catch (DataAccessException e) {
            throw new RagException("Error deleting source: " + source + " from vector store!", e);
        }
    }

    private Resource[] getResources() {
        try {
            var resolver = new PathMatchingResourcePatternResolver();
            String pdfPath = ragConfigData.getPdf().getPath();
            Resource[] pdfResources = resolver.getResources(pdfPath);
            if (pdfResources.length == 0) {
                log.warn("No PDFs found at {}", pdfPath);
                return null;
            }
            return pdfResources;
        } catch (IOException e) {
            throw new RagException("Could not get pdf resources!", e);
        }
    }

    private void ingestDocumentChunksToVectorStore(Resource[] pdfResources) {
        var documents = getDocuments(pdfResources);
        var chunks = getChunks(documents);
        addChunkIndex(chunks);
        vectorStore.add(chunks);
        log.info("Ingested {} chunks into PgVector", chunks.size());
    }

    private List<Document> getDocuments(Resource[] pdfResources) {
        List<Document> documents = new ArrayList<>();
        for (Resource pdfResource: pdfResources) {
            List<Document> parts = getDocumentParts(pdfResource);
            addMetadata(pdfResource, parts);
            documents.addAll(parts);
        }
        return documents;
    }

    private List<Document> getDocumentParts(Resource pdfResource) {
        // The library does not close the original Resource InputStream. Snapshot it
        // through our own try-with-resources before handing it to either reader.
        final byte[] bytes;
        try (InputStream input = pdfResource.getInputStream()) {
            bytes = input.readAllBytes();
        } catch (IOException error) {
            throw new RagException("Cannot read PDF: " + pdfResource.getFilename(), error);
        }
        Resource snapshot = new org.springframework.core.io.ByteArrayResource(bytes) {
            @Override public String getFilename() { return pdfResource.getFilename(); }
        };
        // Spring AI readers retain a PDFBox document; explicitly close it to release Windows file locks.
        try {
            if (RagConstants.PARAGRAPH.equalsIgnoreCase(ragConfigData.getPdf().getMode())) {
                try (var reader = new ClosingParagraphReader(snapshot, pdfDocumentReaderConfig)) {
                    return reader.read();
                }
            }
            try (var reader = new ClosingPageReader(snapshot, pdfDocumentReaderConfig)) {
                return reader.read();
            }
        } catch (IOException error) {
            throw new RagException("Could not close PDF: " + pdfResource.getFilename(), error);
        }
    }

    private static final class ClosingPageReader extends PagePdfDocumentReader implements AutoCloseable {
        ClosingPageReader(Resource resource, PdfDocumentReaderConfig config) { super(resource, config); }
        @Override public void close() throws IOException { document.close(); }
    }

    private static final class ClosingParagraphReader extends ParagraphPdfDocumentReader implements AutoCloseable {
        ClosingParagraphReader(Resource resource, PdfDocumentReaderConfig config) { super(resource, config); }
        @Override public void close() throws IOException { document.close(); }
    }
    private void addMetadata(Resource pdfResource, List<Document> parts) {
        String checksum = sha256Hex(pdfResource);
        for (var part : parts) {
            String filename = Objects.requireNonNullElse(pdfResource.getFilename(), RagConstants.UNKNOWN);
            part.getMetadata().put(RagConstants.SOURCE, filename);
            part.getMetadata().putIfAbsent(RagConstants.DOC_TYPE,
                    filename.contains(".") ? filename.substring(0, filename.lastIndexOf('.')) : filename);
            part.getMetadata().putIfAbsent(RagConstants.UPDATED_AT, ZonedDateTime.now().toLocalDate().toString());

            part.getMetadata().put(RagConstants.CHECKSUM, checksum);
        }
    }

    private List<Document> getChunks(List<Document> documents) {
        return tokenTextSplitter.apply(documents);
    }

    private void addChunkIndex(List<Document> chunks) {
        Map<String, Integer> counters = new HashMap<>();
        for (var chunk : chunks) {
            var source = String.valueOf(chunk.getMetadata().getOrDefault(RagConstants.SOURCE, RagConstants.UNKNOWN));
            var index = counters.merge(source, 1, Integer::sum) - 1;
            chunk.getMetadata().put(RagConstants.CHUNK_INDEX, index);
        }
    }

    private String sha256Hex(Resource resource) {
        try {
            final MessageDigest messageDigest = newMessageDigest("SHA-256");

            try (InputStream inputStream = resource.getInputStream();
                 DigestInputStream digestInputStream = new DigestInputStream(inputStream, messageDigest)) {
                digestInputStream.transferTo(java.io.OutputStream.nullOutputStream());
            }

            return HexFormat.of().formatHex(messageDigest.digest());
        } catch (IOException e) {
            throw new RagException("Error creating sha256Hex for resource " + resource.getFilename(), e);
        }

    }

    private MessageDigest newMessageDigest(String algorithm) {
        try {
            return MessageDigest.getInstance(algorithm);
        } catch (NoSuchAlgorithmException e) {
            throw new RagException("Missing JCA provider for " + algorithm, e);
        }
    }

}




