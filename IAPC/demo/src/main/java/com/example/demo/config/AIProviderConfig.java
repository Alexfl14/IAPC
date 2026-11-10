package com.example.demo.config;

import com.example.demo.rag.config.data.PgVectorStoreConfigData;
import com.example.demo.rag.config.data.RagConfigData;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;
import org.springframework.ai.rag.retrieval.search.VectorStoreDocumentRetriever;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;


import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

@Configuration
@EnableConfigurationProperties(PgVectorStoreConfigData.class)
public class AIProviderConfig {

    //logger
    @Bean
    SimpleLoggerAdvisor loggerAdvisor() {
        return new SimpleLoggerAdvisor();
    }
    @Bean("ollamaGeneralChatClient")
    ChatClient ollamaGeneralChatClient(OllamaChatModel ollamaChatModel) {
        return ChatClient.builder(ollamaChatModel).build();
    }

    @Bean("ollamaRAGChatClient")
    ChatClient ollamaRAGChatClient(OllamaChatModel ollamaChatModel,
                                   SimpleVectorStore simpleVectorStore,
                                   RagConfigData ragConfigData,
                                   SimpleLoggerAdvisor simpleLoggerAdvisor) {
        return ChatClient.builder(ollamaChatModel)
                .defaultAdvisors(
                        QuestionAnswerAdvisor.builder(simpleVectorStore)
                                .searchRequest(SearchRequest.builder()
                                        .topK(ragConfigData.getTopK())
                                        .similarityThreshold(ragConfigData.getSimilarityThreshold())
                                        .build())
                                .build(),
                        simpleLoggerAdvisor
                ).build();
    }

    @Bean("chatMemoryVectorStore")
    public PgVectorStore chatMemoryVectorStore(JdbcTemplate jdbcTemplate,
                                             PgVectorStoreConfigData pgVectorStoreConfigData,
                                             @Qualifier("ollamaEmbeddingModel") EmbeddingModel embeddingModel) {
        return PgVectorStore.builder(jdbcTemplate, embeddingModel)
                .vectorTableName(pgVectorStoreConfigData.getTableNameForChatMemory())
                .initializeSchema(pgVectorStoreConfigData.isInitializeSchema())
                .dimensions(pgVectorStoreConfigData.getDimensions())
                .distanceType(PgVectorStore.PgDistanceType.valueOf(pgVectorStoreConfigData.getDistanceType()))
                .indexType(PgVectorStore.PgIndexType.valueOf(pgVectorStoreConfigData.getIndexType()))
                .maxDocumentBatchSize(pgVectorStoreConfigData.getMaxDocumentBatchSize())
                .build();
    }

    @Bean("ragVectorStore")
    public PgVectorStore ragVectorStore(JdbcTemplate jdbcTemplate,
                                      PgVectorStoreConfigData pgVectorStoreConfigData,
                                      @Qualifier("ollamaEmbeddingModel") EmbeddingModel embeddingModel) {
        return PgVectorStore.builder(jdbcTemplate, embeddingModel)
                .vectorTableName(pgVectorStoreConfigData.getTableNameForRag())
                .initializeSchema(pgVectorStoreConfigData.isInitializeSchema())
                .dimensions(pgVectorStoreConfigData.getDimensions())
                .distanceType(PgVectorStore.PgDistanceType.valueOf(pgVectorStoreConfigData.getDistanceType()))
                .indexType(PgVectorStore.PgIndexType.valueOf(pgVectorStoreConfigData.getIndexType()))
                .maxDocumentBatchSize(pgVectorStoreConfigData.getMaxDocumentBatchSize())
                .build();
    }

    @Bean("traceableWatchLoopExecutor")
    public java.util.concurrent.ExecutorService traceableWatchLoopExecutor() {
        return Executors.newSingleThreadExecutor();
    }

    @Bean("traceableScheduledExecutorService")
    public ScheduledExecutorService traceableScheduledExecutorService() {
        return Executors.newSingleThreadScheduledExecutor();
    }


    @Bean
    RetrievalAugmentationAdvisor retrievalAugmentationAdvisor(@Qualifier("ragVectorStore") VectorStore pdfVectorStore,
                                                              RagConfigData ragConfigData) {
        return RetrievalAugmentationAdvisor.builder()
                .documentRetriever(VectorStoreDocumentRetriever.builder()
                        .vectorStore(pdfVectorStore)
                        .topK(ragConfigData.getTopK())
                        .similarityThreshold(ragConfigData.getSimilarityThreshold())
                        .build())
                .build();
    }

    @Bean({"ollamaAdvancedRAGChatClient", "openAIAdvancedRAGChatClient"})
    ChatClient ollamaAdvancedRAGChatClient(OllamaChatModel ollamaChatModel,
                                           RetrievalAugmentationAdvisor retrievalAugmentationAdvisor) {
        return ChatClient.builder(ollamaChatModel)
                .defaultAdvisors(retrievalAugmentationAdvisor)
                .build();
    }

    @Bean
    @Primary
    EmbeddingModel primaryEmbedding(@Qualifier("ollamaEmbeddingModel") EmbeddingModel delegateEmbeddingModel) {
        return delegateEmbeddingModel;
    }

}


