package com.genai.first.demo.rag.config.data;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "spring.ai.vectorstore.pgvector")
public class PgVectorStoreConfigData {

    private String tableNameForChatMemory = "vector_store";

    private String tableNameForRag = "rag_vector_store";

    /** Enable / initialize the pgvector schema (default: false) */
    private boolean initializeSchema = true;

    /** Index type, e.g. HNSW */
    private String indexType = "HNSW";

    /** Distance type, e.g. COSINE_DISTANCE */
    private String distanceType = "COSINE_DISTANCE";

    /** Vector dimensionality (optional; can be autodetected) */
    private Integer dimensions = 1536;

    /** Maximum number of documents to add in a single batch */
    private Integer maxDocumentBatchSize = 10000;
}
