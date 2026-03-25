package com.destinyoracle.integration;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * REST client for Mem0 long-term memory sidecar.
 * All methods are resilient — failures log but never throw to callers.
 */
@Component
public class Mem0Client {

    private static final Logger log = LoggerFactory.getLogger(Mem0Client.class);

    private final RestClient restClient;

    public Mem0Client(
        @Value("${mem0.base-url:http://localhost:8888}") String baseUrl,
        RestClient.Builder builder
    ) {
        this.restClient = builder.baseUrl(baseUrl).build();
    }

    // ── Data classes ──────────────────────────────────────

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Mem0Memory(
        String id,
        String memory,
        String hash,
        Double score
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Mem0AddResponse(
        @JsonProperty("results") List<Mem0Memory> results
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Mem0SearchResponse(
        @JsonProperty("results") List<Mem0Memory> results
    ) {}

    // ── Public API ────────────────────────────────────────

    /**
     * Add memories from a conversation exchange.
     * Mem0 auto-extracts facts ("user is vegetarian", "user has bad knee").
     */
    public List<Mem0Memory> addMemory(UUID userId, String userMessage, String assistantResponse) {
        try {
            var body = Map.of(
                "messages", List.of(
                    Map.of("role", "user", "content", userMessage),
                    Map.of("role", "assistant", "content", assistantResponse)
                ),
                "user_id", userId.toString()
            );

            var response = restClient.post()
                .uri("/memories")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(Mem0AddResponse.class);

            return response != null && response.results() != null
                ? response.results()
                : Collections.emptyList();
        } catch (Exception e) {
            log.warn("Mem0 addMemory failed for user {}: {}", userId, e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * Semantic search for relevant memories based on user's new message.
     */
    public List<Mem0Memory> searchMemories(UUID userId, String query, int limit) {
        try {
            var body = Map.of(
                "query", query,
                "user_id", userId.toString(),
                "limit", limit
            );

            var response = restClient.post()
                .uri("/memories/search")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(Mem0SearchResponse.class);

            return response != null && response.results() != null
                ? response.results()
                : Collections.emptyList();
        } catch (Exception e) {
            log.warn("Mem0 searchMemories failed for user {}: {}", userId, e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * Get all memories for a user.
     */
    public List<Mem0Memory> getAllMemories(UUID userId) {
        try {
            var response = restClient.get()
                .uri("/memories?user_id={userId}", userId.toString())
                .retrieve()
                .body(Mem0SearchResponse.class);

            return response != null && response.results() != null
                ? response.results()
                : Collections.emptyList();
        } catch (Exception e) {
            log.warn("Mem0 getAllMemories failed for user {}: {}", userId, e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * Delete a specific memory by ID.
     */
    public void deleteMemory(String memoryId) {
        try {
            restClient.delete()
                .uri("/memories/{memoryId}", memoryId)
                .retrieve()
                .toBodilessEntity();
        } catch (Exception e) {
            log.warn("Mem0 deleteMemory failed for {}: {}", memoryId, e.getMessage());
        }
    }

    /**
     * Delete all memories for a user.
     */
    public void deleteAllMemories(UUID userId) {
        try {
            restClient.delete()
                .uri("/memories?user_id={userId}", userId.toString())
                .retrieve()
                .toBodilessEntity();
        } catch (Exception e) {
            log.warn("Mem0 deleteAllMemories failed for user {}: {}", userId, e.getMessage());
        }
    }
}
