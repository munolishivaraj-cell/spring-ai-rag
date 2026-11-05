package com.shivaraj.rag.web.dto;

import java.util.List;

public record AskResponse(String answer, List<SourceChunk> sources) {
}
