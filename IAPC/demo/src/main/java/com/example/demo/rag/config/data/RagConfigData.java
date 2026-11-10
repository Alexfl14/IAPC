package com.example.demo.rag.config.data;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.rag")
public class RagConfigData {
    private boolean forceRebuild;
    private String filesPath = "classpath:rag/mini-employee-handbook/files/*.md";
    private String vectorStorePath = "./data/vector-store/vectorstore.json";
    public String getFilesPath() { return filesPath; }
    public void setFilesPath(String filesPath) { this.filesPath = filesPath; }
    public String getVectorStorePath() { return vectorStorePath; }
    public void setVectorStorePath(String vectorStorePath) { this.vectorStorePath = vectorStorePath; }
    private int topK = 4;
    private double similarityThreshold = 0.7;
    private ChunkProperties chunk = new ChunkProperties();
    private PdfProperties pdf = new PdfProperties();

    public static class ChunkProperties {
        private int size;
        private int minChunkSize;
        private int minChunkToEmbed;
        private int maxNumChunks;
        private boolean keepSeparator;

        public int getSize() { return size; }
        public void setSize(int size) { this.size = size; }
        public int getMinChunkSize() { return minChunkSize; }
        public void setMinChunkSize(int minChunkSize) { this.minChunkSize = minChunkSize; }
        public int getMinChunkToEmbed() { return minChunkToEmbed; }
        public void setMinChunkToEmbed(int minChunkToEmbed) { this.minChunkToEmbed = minChunkToEmbed; }
        public int getMaxNumChunks() { return maxNumChunks; }
        public void setMaxNumChunks(int maxNumChunks) { this.maxNumChunks = maxNumChunks; }
        public boolean isKeepSeparator() { return keepSeparator; }
        public void setKeepSeparator(boolean keepSeparator) { this.keepSeparator = keepSeparator; }
    }

    public static class PdfProperties {
        private boolean watchEnabled = true;
        public boolean isWatchEnabled() { return watchEnabled; }
        public void setWatchEnabled(boolean watchEnabled) { this.watchEnabled = watchEnabled; }
        private String mode;
        private String path;
        private int pagesPerDocument;
        private boolean leftAlignment;
        private int numberOfTopTextLinesToDelete;
        private int numberOfBottomTextLinesToDelete;

        public String getMode() { return mode; }
        public void setMode(String mode) { this.mode = mode; }
        public String getPath() { return path; }
        public void setPath(String path) { this.path = path; }
        public int getPagesPerDocument() { return pagesPerDocument; }
        public void setPagesPerDocument(int pagesPerDocument) { this.pagesPerDocument = pagesPerDocument; }
        public boolean isLeftAlignment() { return leftAlignment; }
        public void setLeftAlignment(boolean leftAlignment) { this.leftAlignment = leftAlignment; }
        public int getNumberOfTopTextLinesToDelete() { return numberOfTopTextLinesToDelete; }
        public void setNumberOfTopTextLinesToDelete(int numberOfTopTextLinesToDelete) {
            this.numberOfTopTextLinesToDelete = numberOfTopTextLinesToDelete;
        }
        public int getNumberOfBottomTextLinesToDelete() { return numberOfBottomTextLinesToDelete; }
        public void setNumberOfBottomTextLinesToDelete(int numberOfBottomTextLinesToDelete) {
            this.numberOfBottomTextLinesToDelete = numberOfBottomTextLinesToDelete;
        }
    }

    public boolean isForceRebuild() { return forceRebuild; }
    public void setForceRebuild(boolean forceRebuild) { this.forceRebuild = forceRebuild; }
    public int getTopK() { return topK; }
    public void setTopK(int topK) { this.topK = topK; }
    public double getSimilarityThreshold() { return similarityThreshold; }
    public void setSimilarityThreshold(double similarityThreshold) { this.similarityThreshold = similarityThreshold; }
    public ChunkProperties getChunk() { return chunk; }
    public void setChunk(ChunkProperties chunk) { this.chunk = chunk; }
    public PdfProperties getPdf() { return pdf; }
    public void setPdf(PdfProperties pdf) { this.pdf = pdf; }
}
