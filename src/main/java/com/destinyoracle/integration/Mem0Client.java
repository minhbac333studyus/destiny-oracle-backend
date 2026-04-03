package com.destinyoracle.integration;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * REST client for Mem0 long-term memory sidecar.
 * All methods are resilient — failures log but never throw to callers.
 */
@Component
public class Mem0Client {

    private static final Logger log = LoggerFactory.getLogger(Mem0Client.class);

    private final RestClient restClient;

    private final String baseUrl;

    /** Primary constructor for Spring injection. */
    @Autowired
    public Mem0Client(
        @Value("${mem0.base-url:http://localhost:8888}") String baseUrl
    ) {
        this.baseUrl = baseUrl;
        var factory = new org.springframework.http.client.SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(java.time.Duration.ofSeconds(5));
        factory.setReadTimeout(java.time.Duration.ofSeconds(10));

        // Mem0 sometimes returns application/octet-stream instead of application/json.
        // Add a StringHttpMessageConverter that accepts ALL content types so RestClient never rejects.
        var allTypesStringConverter = new org.springframework.http.converter.StringHttpMessageConverter();
        allTypesStringConverter.setSupportedMediaTypes(List.of(
            org.springframework.http.MediaType.ALL
        ));

        this.restClient = RestClient.builder()
            .baseUrl(baseUrl)
            .requestFactory(factory)
            .messageConverters(converters -> {
                converters.addFirst(allTypesStringConverter);
            })
            .build();
    }

    /** Constructor accepting a pre-built RestClient (for tests with WireMock). */
    public Mem0Client(RestClient restClient) {
        this.baseUrl = "";
        this.restClient = restClient;
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

    // ── Public API ─────────────────────────────────────────

    /** Add memory — accepts String userId for convenience. */
    public List<Mem0Memory> addMemory(String userId, String userMessage, String assistantResponse) {
        return addMemoryInternal(userId, userMessage, assistantResponse);
    }

    /** Add memory — accepts UUID userId. */
    public List<Mem0Memory> addMemory(UUID userId, String userMessage, String assistantResponse) {
        return addMemoryInternal(userId.toString(), userMessage, assistantResponse);
    }

    private List<Mem0Memory> addMemoryInternal(String userId, String userMessage, String assistantResponse) {
        try {
            var body = Map.of(
                "messages", List.of(
                    Map.of("role", "user", "content", userMessage),
                    Map.of("role", "assistant", "content", assistantResponse)
                ),
                "user_id", userId
            );

            var rawResponse = restClient.post()
                .uri("/v1/memories/")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(String.class);

            if (rawResponse == null || rawResponse.isBlank()) return Collections.emptyList();

            var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            var parsed = mapper.readValue(rawResponse, Mem0AddResponse.class);
            log.debug("Mem0 addMemory success for user {}: {} results", userId,
                parsed != null && parsed.results() != null ? parsed.results().size() : 0);
            return parsed != null && parsed.results() != null
                ? parsed.results()
                : Collections.emptyList();
        } catch (Exception e) {
            log.warn("Mem0 addMemory failed for user {}: {}", userId, e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * Semantic search returning formatted string — used by ContextAssembler and tests.
     */
    public String searchMemories(String userId, String query) {
        var memories = searchMemoriesRaw(userId, query, 5);
        if (memories.isEmpty()) return "";
        return memories.stream()
            .map(Mem0Memory::memory)
            .collect(Collectors.joining("\n- ", "KNOWN FACTS:\n- ", ""));
    }

    /**
     * Semantic search for relevant memories — returns raw list.
     */
    public List<Mem0Memory> searchMemories(UUID userId, String query, int limit) {
        return searchMemoriesRaw(userId.toString(), query, limit);
    }

    @SuppressWarnings("unchecked")
    private List<Mem0Memory> searchMemoriesRaw(String userId, String query, int limit) {
        try {
            var body = Map.of(
                "query", query,
                "user_id", userId,
                "limit", limit
            );

            // /v1/memories/search/ returns a raw JSON array (not wrapped in {"results": [...]})
            var raw = restClient.post()
                .uri("/v1/memories/search/")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(List.class);

            if (raw == null) return Collections.emptyList();
            return ((List<Object>) raw).stream()
                .filter(item -> item instanceof Map)
                .map(item -> {
                    @SuppressWarnings("unchecked")
                    var m = (Map<String, Object>) item;
                    return new Mem0Memory(
                        String.valueOf(m.getOrDefault("id", "")),
                        String.valueOf(m.getOrDefault("memory", "")),
                        m.get("hash") != null ? String.valueOf(m.get("hash")) : null,
                        m.get("score") instanceof Number n ? n.doubleValue() : null
                    );
                })
                .collect(Collectors.toList());
        } catch (Exception e) {
            log.warn("Mem0 searchMemories failed for user {}: {}", userId, e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * Get all memories for a user.
     */
    @SuppressWarnings("unchecked")
    public List<Mem0Memory> getAllMemories(UUID userId) {
        try {
            // Mem0 GET /v1/memories/ may return a raw JSON array or {"results": [...]}
            var rawResponse = restClient.get()
                .uri("/v1/memories/?user_id={userId}", userId.toString())
                .retrieve()
                .body(String.class);

            if (rawResponse == null || rawResponse.isBlank()) return Collections.emptyList();

            var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            var tree = mapper.readTree(rawResponse);

            // Handle both formats: raw array [...] or wrapped {"results": [...]}
            var arrayNode = tree.isArray() ? tree : (tree.has("results") ? tree.get("results") : null);
            if (arrayNode == null || !arrayNode.isArray()) return Collections.emptyList();

            List<Mem0Memory> result = new java.util.ArrayList<>();
            for (var node : arrayNode) {
                result.add(new Mem0Memory(
                    node.has("id") ? node.get("id").asText() : "",
                    node.has("memory") ? node.get("memory").asText() : "",
                    node.has("hash") ? node.get("hash").asText() : null,
                    node.has("score") ? node.get("score").asDouble() : null
                ));
            }
            return result;
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
                .uri("/v1/memories/{memoryId}/", memoryId)
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
                .uri("/v1/memories/?user_id={userId}", userId.toString())
                .retrieve()
                .toBodilessEntity();
        } catch (Exception e) {
            log.warn("Mem0 deleteAllMemories failed for user {}: {}", userId, e.getMessage());
        }
    }
}
