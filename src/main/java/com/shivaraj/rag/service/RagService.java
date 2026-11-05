package com.shivaraj.rag.service;

import com.shivaraj.rag.config.RagProperties;
import com.shivaraj.rag.web.dto.AskResponse;
import com.shivaraj.rag.web.dto.SourceChunk;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * RAG steps 5-6: RETRIEVE the chunks most similar to the question, then GENERATE an answer
 * with the LLM using only those chunks.
 *
 * Retrieval is written out step by step here so every part is visible. Spring AI can also do
 * it in one line with an advisor (dependency spring-ai-advisors-vector-store):
 *
 *     chatClient.prompt()
 *         .advisors(QuestionAnswerAdvisor.builder(vectorStore).build())
 *         .user(question).call().content();
 */
@Service
public class RagService {

    private static final String SYSTEM_PROMPT = """
            You are a helpful assistant that answers questions using ONLY the context provided.
            If the answer is not in the context, reply exactly: "I don't know based on the documents."
            Keep answers clear and concise. Cite the context passages you used like [1], [2].
            """;

    private static final int EXCERPT_LENGTH = 200;

    private final ChatClient chatClient;
    private final VectorStore vectorStore;
    private final RagProperties props;

    public RagService(ChatClient.Builder chatClientBuilder, VectorStore vectorStore, RagProperties props) {
        this.chatClient = chatClientBuilder.defaultSystem(SYSTEM_PROMPT).build();
        this.vectorStore = vectorStore;
        this.props = props;
    }

    public AskResponse ask(String question) {
        // 5. RETRIEVE - embed the question and find the nearest chunks in pgvector
        List<Document> hits = vectorStore.similaritySearch(SearchRequest.builder()
                .query(question)
                .topK(props.topK())
                .similarityThreshold(props.similarityThreshold())
                .build());

        if (hits == null || hits.isEmpty()) {
            return new AskResponse("I couldn't find anything relevant in the uploaded documents.", List.of());
        }

        // Build the numbered context block and the source list shown to the user
        StringBuilder context = new StringBuilder();
        List<SourceChunk> sources = new ArrayList<>();
        for (int i = 0; i < hits.size(); i++) {
            Document doc = hits.get(i);
            String source = String.valueOf(doc.getMetadata().getOrDefault(IngestionService.SOURCE, "unknown"));
            String text = doc.getText() == null ? "" : doc.getText();
            context.append("[").append(i + 1).append("] (source: ").append(source).append(")\n")
                   .append(text).append("\n\n");
            sources.add(new SourceChunk(i + 1, source, doc.getScore(), excerpt(text)));
        }

        // 6. GENERATE - plain string (no template params), so '{' '}' in Java code can't break the prompt
        String userMessage = "Context:\n" + context + "Question: " + question;
        String answer = chatClient.prompt()
                .user(userMessage)
                .call()
                .content();

        return new AskResponse(answer, sources);
    }

    private static String excerpt(String text) {
        String oneLine = text.replaceAll("\\s+", " ").trim();
        return oneLine.length() <= EXCERPT_LENGTH ? oneLine : oneLine.substring(0, EXCERPT_LENGTH) + "...";
    }
}
