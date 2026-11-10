package com.example.demo;

import com.example.demo.rag.config.service.RagIngestionService;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.font.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.file.*;
import java.time.Duration;
import java.util.UUID;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.*;

/** Integration tests: start Ollama and the project's pgvector container first. */
@SpringBootTest
class DemoApplicationTests {
    @Autowired JdbcTemplate jdbc;
    @Autowired RagIngestionService ingestion;

    @Test
    void contextLoads() {
        assertNotNull(jdbc.queryForObject("select extversion from pg_extension where extname='vector'", String.class));
    }

    @Test
    void watchesPdfCreationModificationAndDeletionWithoutDuplicates() throws Exception {
        String name = "integration-" + UUID.randomUUID() + ".pdf";
        Path file = Path.of("data", "pdfs", name);
        Path staging = Files.createTempFile("rag-test-", ".pdf");
        try {
            writePdf(staging, "The laboratory access code is ORCHID-742. Employees must use this code to enter the laboratory.");
            Files.copy(staging, file);
            await(() -> count(name) > 0);
            int initial = count(name);
            assertTrue(content(name).contains("ORCHID-742"));
            ingestion.upsertOneByPath(file);
            assertEquals(initial, count(name), "Unchanged PDF must not create duplicates");

            writePdf(staging, "The laboratory access code is MAPLE-853. Employees must use this new code to enter the laboratory.");
            Files.copy(staging, file, StandardCopyOption.REPLACE_EXISTING);
            await(() -> content(name).contains("MAPLE-853"));
            assertFalse(content(name).contains("ORCHID-742"), "Old chunks must be replaced");
            Files.delete(file);
            await(() -> count(name) == 0);
        } finally {
            Files.deleteIfExists(file);
            Files.deleteIfExists(staging);
            ingestion.deleteBySource(name);
        }
    }

    private int count(String source) {
        return jdbc.queryForObject("select count(*) from rag_vector_store where metadata->>'source'=?", Integer.class, source);
    }

    private String content(String source) {
        return String.join(" ", jdbc.queryForList("select content from rag_vector_store where metadata->>'source'=?", String.class, source));
    }

    private static void await(BooleanSupplier condition) throws InterruptedException {
        long deadline = System.nanoTime() + Duration.ofSeconds(60).toNanos();
        while (System.nanoTime() < deadline) {
            if (condition.getAsBoolean()) return;
            Thread.sleep(250);
        }
        fail("PDF watcher did not update the vector store within 60 seconds");
    }

    private static void writePdf(Path path, String text) throws Exception {
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage();
            document.addPage(page);
            try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
                stream.beginText();
                stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 10);
                stream.newLineAtOffset(40, 700);
                stream.showText(text);
                stream.endText();
            }
            document.save(path.toFile());
        }
    }
}
