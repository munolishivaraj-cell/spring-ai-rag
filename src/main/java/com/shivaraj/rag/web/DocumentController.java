package com.shivaraj.rag.web;

import com.shivaraj.rag.service.IngestionService;
import com.shivaraj.rag.web.dto.IngestResponse;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/api/documents")
public class DocumentController {

    private final IngestionService ingestionService;

    public DocumentController(IngestionService ingestionService) {
        this.ingestionService = ingestionService;
    }

    /** Upload one file (PDF, DOCX, TXT, MD, Java, ...) and add it to the knowledge base. */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public IngestResponse upload(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("Uploaded file is empty");
        }
        int chunks = ingestionService.ingest(file);
        return IngestResponse.of(Map.of(String.valueOf(file.getOriginalFilename()), chunks));
    }

    /** Add every file in the docs/ folder to the knowledge base. */
    @PostMapping("/ingest-folder")
    public IngestResponse ingestFolder() {
        return IngestResponse.of(ingestionService.ingestFolder());
    }
}
