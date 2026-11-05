package com.shivaraj.rag.service;

import com.shivaraj.rag.config.RagProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * RAG steps 1-4: READ a document, SPLIT it into chunks, EMBED each chunk, STORE the vectors.
 * The embedding (step 3) happens inside vectorStore.add(), which calls the Ollama embedding model.
 */
@Service
public class IngestionService {

    private static final Logger log = LoggerFactory.getLogger(IngestionService.class);
    public static final String SOURCE = "source";

    private final VectorStore vectorStore;
    private final RagProperties props;
    private final TokenTextSplitter splitter = new TokenTextSplitter(); // ~800-token chunks by default

    public IngestionService(VectorStore vectorStore, RagProperties props) {
        this.vectorStore = vectorStore;
        this.props = props;
    }

    /** Ingest one uploaded file. Returns the number of chunks stored. */
    public int ingest(MultipartFile file) {
        String name = file.getOriginalFilename() != null ? file.getOriginalFilename() : "upload";
        try {
            Resource resource = new ByteArrayResource(file.getBytes()) {
                @Override
                public String getFilename() {
                    return name; // lets Tika detect the file type from the extension
                }
            };
            return ingest(resource, name);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read uploaded file " + name, e);
        }
    }

    /** Ingest every file in the configured docs folder (and sub-folders). */
    public Map<String, Integer> ingestFolder() {
        Path folder = Path.of(props.docsFolder());
        if (!Files.isDirectory(folder)) {
            throw new IllegalArgumentException("Folder not found: " + folder.toAbsolutePath());
        }
        Map<String, Integer> result = new LinkedHashMap<>();
        try (Stream<Path> files = Files.walk(folder)) {
            for (Path path : files.filter(Files::isRegularFile).toList()) {
                String name = folder.relativize(path).toString();
                result.put(name, ingest(new FileSystemResource(path), name));
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read folder " + folder, e);
        }
        return result;
    }

    private int ingest(Resource resource, String sourceName) {
        // 1. READ - Tika extracts plain text from PDF, DOCX, TXT, MD, HTML, Java source, ...
        List<Document> documents = new TikaDocumentReader(resource).get();
        documents.forEach(doc -> doc.getMetadata().put(SOURCE, sourceName));

        // 2. SPLIT - small chunks make search precise and fit in the LLM's context window
        List<Document> chunks = splitter.apply(documents);
        if (chunks.isEmpty()) {
            log.warn("No text found in {}", sourceName);
            return 0;
        }

        // 3 + 4. EMBED and STORE - pgvector store calls the embedding model, then saves the vectors
        vectorStore.add(chunks);
        log.info("Ingested {} -> {} chunks", sourceName, chunks.size());
        return chunks.size();
    }
}
