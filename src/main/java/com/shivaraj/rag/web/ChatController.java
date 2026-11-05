package com.shivaraj.rag.web;

import com.shivaraj.rag.service.RagService;
import com.shivaraj.rag.web.dto.AskRequest;
import com.shivaraj.rag.web.dto.AskResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final RagService ragService;

    public ChatController(RagService ragService) {
        this.ragService = ragService;
    }

    /** Ask a question; the answer comes from the uploaded documents, with sources. */
    @PostMapping
    public AskResponse ask(@Valid @RequestBody AskRequest request) {
        return ragService.ask(request.question());
    }
}
