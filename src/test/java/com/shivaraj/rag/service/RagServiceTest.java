package com.shivaraj.rag.service;

import com.shivaraj.rag.config.RagProperties;
import com.shivaraj.rag.web.dto.AskResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class RagServiceTest {

    private final VectorStore vectorStore = mock(VectorStore.class);
    private final ChatClient chatClient = mock(ChatClient.class, RETURNS_DEEP_STUBS);
    private final RagProperties props = new RagProperties(3, 0.5, "docs");
    private RagService ragService;

    @BeforeEach
    void setUp() {
        ChatClient.Builder builder = mock(ChatClient.Builder.class);
        when(builder.defaultSystem(anyString())).thenReturn(builder);
        when(builder.build()).thenReturn(chatClient);
        ragService = new RagService(builder, vectorStore, props);
    }

    private static Document chunk(String text, String source, double score) {
        return Document.builder().text(text).metadata(Map.of(IngestionService.SOURCE, source)).score(score).build();
    }

    @Test
    void searchesWithConfiguredTopKAndThreshold() {
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());

        ragService.ask("What is the leave policy?");

        ArgumentCaptor<SearchRequest> request = ArgumentCaptor.forClass(SearchRequest.class);
        verify(vectorStore).similaritySearch(request.capture());
        assertThat(request.getValue().getQuery()).isEqualTo("What is the leave policy?");
        assertThat(request.getValue().getTopK()).isEqualTo(3);
        assertThat(request.getValue().getSimilarityThreshold()).isEqualTo(0.5);
    }

    @Test
    void noRelevantChunksSkipsTheLlm() {
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());

        AskResponse response = ragService.ask("Unrelated question");

        assertThat(response.answer()).contains("couldn't find anything relevant");
        assertThat(response.sources()).isEmpty();
        verify(chatClient, never()).prompt();
    }

    @Test
    void sendsNumberedContextToTheLlmAndReturnsSources() {
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(
                chunk("Employees get 24 days of paid leave.", "handbook.md", 0.82),
                chunk("The office opens at 9:30 AM.", "faq.txt", 0.61)));
        when(chatClient.prompt().user(anyString()).call().content()).thenReturn("24 days [1]");

        AskResponse response = ragService.ask("How many leave days?");

        assertThat(response.answer()).isEqualTo("24 days [1]");
        assertThat(response.sources()).hasSize(2);
        assertThat(response.sources().get(0).index()).isEqualTo(1);
        assertThat(response.sources().get(0).source()).isEqualTo("handbook.md");
        assertThat(response.sources().get(0).score()).isEqualTo(0.82);
        assertThat(response.sources().get(1).source()).isEqualTo("faq.txt");

        ArgumentCaptor<String> prompt = ArgumentCaptor.forClass(String.class);
        verify(chatClient.prompt(), atLeastOnce()).user(prompt.capture());
        String userMessage = prompt.getAllValues().get(prompt.getAllValues().size() - 1);
        assertThat(userMessage)
                .contains("[1] (source: handbook.md)", "Employees get 24 days of paid leave.")
                .contains("[2] (source: faq.txt)", "The office opens at 9:30 AM.")
                .endsWith("Question: How many leave days?");
    }

    @Test
    void longChunksAreShortenedToAnExcerpt() {
        String longText = "word ".repeat(100);
        when(vectorStore.similaritySearch(any(SearchRequest.class)))
                .thenReturn(List.of(chunk(longText, "long.txt", 0.7)));
        when(chatClient.prompt().user(anyString()).call().content()).thenReturn("answer");

        AskResponse response = ragService.ask("question");

        String excerpt = response.sources().get(0).excerpt();
        assertThat(excerpt).endsWith("...");
        assertThat(excerpt).hasSize(203); // 200 characters + "..."
    }
}
