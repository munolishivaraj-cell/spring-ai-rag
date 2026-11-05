package com.shivaraj.rag.service;

import com.shivaraj.rag.config.RagProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

class IngestionServiceTest {

    private final VectorStore vectorStore = mock(VectorStore.class);

    private IngestionService service(String docsFolder) {
        return new IngestionService(vectorStore, new RagProperties(4, 0.4, docsFolder));
    }

    @SuppressWarnings("unchecked")
    private List<Document> storedChunks() {
        ArgumentCaptor<List<Document>> captor = ArgumentCaptor.forClass(List.class);
        verify(vectorStore, atLeastOnce()).add(captor.capture());
        return captor.getAllValues().stream().flatMap(List::stream).toList();
    }

    @Test
    void uploadedFileIsChunkedTaggedWithSourceAndStored() {
        var file = new MockMultipartFile("file", "handbook.txt", "text/plain",
                "Employees get 24 days of paid leave per year.".getBytes(StandardCharsets.UTF_8));

        int chunks = service("docs").ingest(file);

        assertThat(chunks).isEqualTo(1);
        List<Document> stored = storedChunks();
        assertThat(stored).hasSize(1);
        assertThat(stored.get(0).getText()).contains("24 days of paid leave");
        assertThat(stored.get(0).getMetadata()).containsEntry(IngestionService.SOURCE, "handbook.txt");
    }

    @Test
    void longDocumentIsSplitIntoSeveralChunks() {
        String text = "Spring Boot makes it easy to build production-ready applications. ".repeat(400);
        var file = new MockMultipartFile("file", "long.txt", "text/plain", text.getBytes(StandardCharsets.UTF_8));

        int chunks = service("docs").ingest(file);

        assertThat(chunks).isGreaterThan(1);
        assertThat(storedChunks()).hasSize(chunks);
    }

    @Test
    void fileWithoutTextStoresNothing() {
        var file = new MockMultipartFile("file", "blank.txt", "text/plain", "   \n  ".getBytes(StandardCharsets.UTF_8));

        int chunks = service("docs").ingest(file);

        assertThat(chunks).isZero();
        verify(vectorStore, never()).add(anyList());
    }

    @Test
    void ingestFolderReadsEveryFileIncludingSubFolders(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("a.md"), "# Notes\nActuator exposes health endpoints.");
        Files.createDirectories(dir.resolve("sub"));
        Files.writeString(dir.resolve("sub").resolve("b.txt"), "Flyway manages database migrations.");

        Map<String, Integer> result = service(dir.toString()).ingestFolder();

        assertThat(result).containsOnlyKeys("a.md", Path.of("sub", "b.txt").toString());
        assertThat(result.values()).allMatch(n -> n == 1);
        verify(vectorStore, times(2)).add(any());
    }

    @Test
    void missingFolderIsRejected() {
        assertThatThrownBy(() -> service("does-not-exist").ingestFolder())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Folder not found");
    }
}
