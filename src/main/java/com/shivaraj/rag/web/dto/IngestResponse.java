package com.shivaraj.rag.web.dto;

import java.util.Map;

/** Chunks stored per file, plus the total. */
public record IngestResponse(Map<String, Integer> chunksPerFile, int totalChunks) {

    public static IngestResponse of(Map<String, Integer> chunksPerFile) {
        int total = chunksPerFile.values().stream().mapToInt(Integer::intValue).sum();
        return new IngestResponse(chunksPerFile, total);
    }
}
