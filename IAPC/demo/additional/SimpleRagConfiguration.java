package com.genai.first.demo.rag.config;

import com.genai.first.demo.rag.config.data.RagConfigData;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.reader.TextReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class SimpleRagConfiguration {

    private static final String DEFAULT_FILES_PATH = "classpath:rag/mini-employee-handbook/files/*.md";
    private static final String DEFAULT_VECTOR_STORE_PATH = "src/main/resources/rag/mini-employee-handbook/vector-store/vectorstore.json";

    private final RagConfigData ragConfigData;
    private final ResourcePatternResolver resourcePatternResolver = new PathMatchingResourcePatternResolver();

    @Bean
    public SimpleVectorStore simpleVectorStore(@Qualifier("openAiEmbeddingModel") EmbeddingModel embeddingModel,
                                               TokenTextSplitter tokenTextSplitter) throws IOException {
        var simpleVectorStore = SimpleVectorStore.builder(embeddingModel).build();
        var vectorStoreFile = new File(DEFAULT_VECTOR_STORE_PATH);

        if (vectorStoreFile.exists() && !ragConfigData.isForceRebuild()) {
            log.info("Vector store file exists, loading from: {}", vectorStoreFile.getAbsolutePath());
            simpleVectorStore.load(vectorStoreFile);
            return simpleVectorStore;
        }

        log.info("Building vector store and saving to: {}", vectorStoreFile.getAbsolutePath());
        ensureParentDirectoryExists(vectorStoreFile);

        var documents = loadDocuments();
        var chunks = tokenTextSplitter.apply(documents);
        simpleVectorStore.add(chunks);
        simpleVectorStore.save(vectorStoreFile);

        return simpleVectorStore;
    }

    private List<Document> loadDocuments() throws IOException {
        Resource[] resources = resourcePatternResolver.getResources(DEFAULT_FILES_PATH);
        List<Document> documents = new ArrayList<>();

        for (Resource resource : resources) {
            TextReader reader = new TextReader(resource);
            String category = extractCategory(resource);
            reader.getCustomMetadata().put("category", category);
            reader.getCustomMetadata().put("access_level", "public");
            reader.getCustomMetadata().put("version", "2026.01");
            documents.addAll(reader.read());
        }

        return documents;
    }

    private String extractCategory(Resource resource) {
        String filename = resource.getFilename();
        if (filename != null && filename.endsWith(".md")) {
            return filename.substring(0, filename.length() - 3);
        }
        return filename != null ? filename : "unknown";
    }

    private void ensureParentDirectoryExists(File file) {
        File parent = file.getParentFile();
        if (parent != null && !parent.exists()) {
            if (parent.mkdirs()) {
                log.debug("Created parent directories for vector store: {}", parent.getAbsolutePath());
            }
        }
    }
}