package com.shivaraj.rag.web;

import com.shivaraj.rag.service.IngestionService;
import com.shivaraj.rag.service.RagService;
import com.shivaraj.rag.web.dto.AskResponse;
import com.shivaraj.rag.web.dto.SourceChunk;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Web layer only: controllers, validation and error handling, with the services mocked. */
@WebMvcTest({ChatController.class, DocumentController.class})
class ApiControllerTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    RagService ragService;

    @MockitoBean
    IngestionService ingestionService;

    @Test
    void askReturnsAnswerWithSources() throws Exception {
        when(ragService.ask("How many leave days?")).thenReturn(new AskResponse("24 days [1]",
                List.of(new SourceChunk(1, "handbook.md", 0.82, "Employees get 24 days..."))));

        mvc.perform(post("/api/chat").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"How many leave days?\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer").value("24 days [1]"))
                .andExpect(jsonPath("$.sources[0].source").value("handbook.md"))
                .andExpect(jsonPath("$.sources[0].index").value(1));
    }

    @Test
    void blankQuestionIsRejectedWith400() throws Exception {
        mvc.perform(post("/api/chat").contentType(MediaType.APPLICATION_JSON).content("{\"question\":\"  \"}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(ragService);
    }

    @Test
    void tooLongQuestionIsRejectedWith400() throws Exception {
        String question = "a".repeat(2001);
        mvc.perform(post("/api/chat").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"" + question + "\"}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(ragService);
    }

    @Test
    void uploadReturnsChunkCounts() throws Exception {
        when(ingestionService.ingest(any())).thenReturn(3);
        var file = new MockMultipartFile("file", "notes.pdf", "application/pdf", new byte[]{1, 2, 3});

        mvc.perform(multipart("/api/documents").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.chunksPerFile['notes.pdf']").value(3))
                .andExpect(jsonPath("$.totalChunks").value(3));
    }

    @Test
    void emptyUploadIsRejectedWithProblemDetail() throws Exception {
        var empty = new MockMultipartFile("file", "empty.txt", "text/plain", new byte[0]);

        mvc.perform(multipart("/api/documents").file(empty))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Uploaded file is empty"));
        verifyNoInteractions(ingestionService);
    }

    @Test
    void ingestFolderSumsChunksAcrossFiles() throws Exception {
        Map<String, Integer> result = new LinkedHashMap<>();
        result.put("a.md", 2);
        result.put("b.txt", 1);
        when(ingestionService.ingestFolder()).thenReturn(result);

        mvc.perform(post("/api/documents/ingest-folder"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalChunks").value(3));
    }

    @Test
    void unexpectedErrorsBecome500ProblemDetail() throws Exception {
        when(ragService.ask(any())).thenThrow(new IllegalStateException("Ollama is down"));

        mvc.perform(post("/api/chat").contentType(MediaType.APPLICATION_JSON).content("{\"question\":\"hi\"}"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.detail", org.hamcrest.Matchers.containsString("Ollama is down")));
    }
}
