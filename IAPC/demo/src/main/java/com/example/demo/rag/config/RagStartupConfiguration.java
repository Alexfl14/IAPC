package com.example.demo.rag.config;

import com.example.demo.rag.config.service.RagIngestionService;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RagStartupConfiguration {
    @Bean
    ApplicationRunner ingestPdfDocuments(RagIngestionService ingestion) {
        return args -> ingestion.initializePgVectorStore();
    }
}
