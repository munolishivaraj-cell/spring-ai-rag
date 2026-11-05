package com.shivaraj.rag;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end RAG test with real containers (needs Docker):
 * upload -> Tika -> split -> Ollama embeddings -> pgvector -> retrieval -> Ollama llama3.2 answer.
 * Runs in `mvnw verify`.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfig.class)
class RagApiIT {

    private static final String HANDBOOK = """
            Acme Robotics Employee Handbook
            Acme Robotics was founded in 2014 in Pune by Meera Kulkarni.
            Employees get 24 days of paid leave per year. Unused leave up to 10 days carries over to the next year.
            The office opens at 9:30 AM and the core collaboration hours are 11 AM to 4 PM.
            The flagship product is the Kestrel X2, a warehouse robot that carries up to 150 kg.
            """;

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void uploadThenAskAnswersFromTheDocument() throws Exception {
        var file = new MockMultipartFile("file", "acme-handbook.txt", "text/plain",
                HANDBOOK.getBytes(StandardCharsets.UTF_8));

        mvc.perform(multipart("/api/documents").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.chunksPerFile['acme-handbook.txt']").value(1))
                .andExpect(jsonPath("$.totalChunks").value(1));

        // nomic-embed-text vectors are stored with the configured 768 dimensions
        Integer dims = jdbc.queryForObject("SELECT vector_dims(embedding) FROM vector_store LIMIT 1", Integer.class);
        assertThat(dims).isEqualTo(768);

        mvc.perform(post("/api/chat").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"How many days of paid leave do employees get per year?\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer", containsString("24")))
                .andExpect(jsonPath("$.sources[0].source").value("acme-handbook.txt"));
    }

    @Test
    void emptyQuestionIsRejected() throws Exception {
        mvc.perform(post("/api/chat").contentType(MediaType.APPLICATION_JSON).content("{\"question\":\"\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void emptyUploadIsRejected() throws Exception {
        var empty = new MockMultipartFile("file", "empty.txt", "text/plain", new byte[0]);
        mvc.perform(multipart("/api/documents").file(empty))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Uploaded file is empty"));
    }
}
