package com.shivaraj.rag.web.dto;

/**
 * One retrieved chunk shown under the answer.
 *
 * @param index   number used in the answer's citations, e.g. [1]
 * @param source  file the chunk came from
 * @param score   similarity to the question (0..1, higher = more similar)
 * @param excerpt first part of the chunk text
 */
public record SourceChunk(int index, String source, Double score, String excerpt) {
}
