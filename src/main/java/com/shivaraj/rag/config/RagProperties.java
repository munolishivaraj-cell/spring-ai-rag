package com.shivaraj.rag.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * RAG settings from application.yml (prefix "rag").
 *
 * @param topK                number of chunks retrieved for each question
 * @param similarityThreshold minimum similarity (0..1) for a chunk to count as relevant
 * @param docsFolder          folder ingested by POST /api/documents/ingest-folder
 */
@ConfigurationProperties(prefix = "rag")
public record RagProperties(
        @DefaultValue("4") int topK,
        @DefaultValue("0.4") double similarityThreshold,
        @DefaultValue("docs") String docsFolder) {
}
