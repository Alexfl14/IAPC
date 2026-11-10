package com.example.demo.rag.config.data;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix="spring.ai.vectorstore.pgvector")
public class PgVectorStoreConfigData {
    private String tableNameForChatMemory;
    private String tableNameForRag;
    private boolean initializeSchema;
    private String indexType;
    private String distanceType;
    private int dimensions;
    private int maxDocumentBatchSize;

    public String getTableNameForChatMemory() { return tableNameForChatMemory; }
    public void setTableNameForChatMemory(String tableNameForChatMemory) {
        this.tableNameForChatMemory = tableNameForChatMemory;
    }
    public String getTableNameForRag() { return tableNameForRag; }
    public void setTableNameForRag(String tableNameForRag) { this.tableNameForRag = tableNameForRag; }
    public boolean isInitializeSchema() { return initializeSchema; }
    public void setInitializeSchema(boolean initializeSchema) { this.initializeSchema = initializeSchema; }
    public String getIndexType() { return indexType; }
    public void setIndexType(String indexType) { this.indexType = indexType; }
    public String getDistanceType() { return distanceType; }
    public void setDistanceType(String distanceType) { this.distanceType = distanceType; }
    public int getDimensions() { return dimensions; }
    public void setDimensions(int dimensions) { this.dimensions = dimensions; }
    public int getMaxDocumentBatchSize() { return maxDocumentBatchSize; }
    public void setMaxDocumentBatchSize(int maxDocumentBatchSize) {
        this.maxDocumentBatchSize = maxDocumentBatchSize;
    }
}
