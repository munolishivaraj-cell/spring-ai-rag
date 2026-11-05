package com.shivaraj.rag;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.BindMode;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.ollama.OllamaContainer;
import org.testcontainers.utility.DockerImageName;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Starts the two things the app talks to, each in its own Docker container:
 * - PostgreSQL with pgvector (the vector database)
 * - Ollama (the local LLM server for chat + embeddings)
 * {@code @ServiceConnection} points the app's datasource and Ollama base URL at them automatically.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfig {

    @Bean
    @ServiceConnection
    PostgreSQLContainer<?> pgvector() {
        return new PostgreSQLContainer<>(
                DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"));
    }

    @Bean
    @ServiceConnection
    OllamaContainer ollama() {
        OllamaContainer ollama = new OllamaContainer("ollama/ollama:0.40.14");
        // Reuse models already pulled on this machine (~2.3 GB) instead of downloading them again.
        // Without them (e.g. in CI) the app pulls the models into the container on startup.
        Path localModels = Path.of(System.getProperty("user.home"), ".ollama", "models");
        if (Files.isDirectory(localModels)) {
            ollama.withFileSystemBind(localModels.toString(), "/root/.ollama/models", BindMode.READ_ONLY)
                  .withEnv("OLLAMA_NOPRUNE", "1");
        }
        return ollama;
    }
}
