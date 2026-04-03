package com.destinyoracle.domain.chat.controller;

import com.destinyoracle.domain.chat.entity.DailyInsight;
import com.destinyoracle.domain.chat.repository.DailyInsightRepository;
import com.destinyoracle.dto.response.DailyInsightResponse;
import com.destinyoracle.integration.Mem0Client;
import com.destinyoracle.dto.response.Mem0MemoryResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Insights & Memory", description = "Daily insights and Mem0 memory management")
public class InsightController {

    private final DailyInsightRepository insightRepo;
    private final Mem0Client mem0Client;

    public InsightController(DailyInsightRepository insightRepo, Mem0Client mem0Client) {
        this.insightRepo = insightRepo;
        this.mem0Client = mem0Client;
    }

    // ── Insights ─────────────────────────────────────

    @GetMapping("/insights/today")
    @Operation(summary = "Get today's daily insight")
    public ResponseEntity<DailyInsightResponse> getTodayInsight(
        @RequestHeader("X-User-Id") UUID userId
    ) {
        return insightRepo.findByUserIdAndInsightDate(userId, LocalDate.now())
            .map(this::toInsightResponse)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.noContent().build());
    }

    @GetMapping("/insights")
    @Operation(summary = "List all daily insights")
    public ResponseEntity<List<DailyInsightResponse>> listInsights(
        @RequestHeader("X-User-Id") UUID userId
    ) {
        return ResponseEntity.ok(
            insightRepo.findByUserIdOrderByInsightDateDesc(userId).stream()
                .map(this::toInsightResponse)
                .toList()
        );
    }

    // ── Memory admin ─────────────────────────────────

    @GetMapping("/memories")
    @Operation(summary = "Get all Mem0 long-term memories")
    public ResponseEntity<Mem0MemoryResponse> getMemories(
        @RequestHeader("X-User-Id") UUID userId
    ) {
        var memories = mem0Client.getAllMemories(userId);
        var response = new Mem0MemoryResponse(
            memories.stream()
                .map(m -> new Mem0MemoryResponse.Memory(m.id(), m.memory(), m.hash(), m.score()))
                .toList()
        );
        return ResponseEntity.ok(response);
    }

    @PostMapping("/memories/search")
    @Operation(summary = "Search Mem0 memories semantically")
    public ResponseEntity<Mem0MemoryResponse> searchMemories(
        @RequestHeader("X-User-Id") UUID userId,
        @RequestBody java.util.Map<String, Object> body
    ) {
        String query = (String) body.getOrDefault("query", "");
        int limit = body.containsKey("limit") ? ((Number) body.get("limit")).intValue() : 5;
        var memories = mem0Client.searchMemories(userId, query, limit);
        var response = new Mem0MemoryResponse(
            memories.stream()
                .map(m -> new Mem0MemoryResponse.Memory(m.id(), m.memory(), m.hash(), m.score()))
                .toList()
        );
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/memories/{memoryId}")
    @Operation(summary = "Delete a specific Mem0 memory")
    public ResponseEntity<Void> deleteMemory(@PathVariable String memoryId) {
        mem0Client.deleteMemory(memoryId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/memories")
    @Operation(summary = "Delete all Mem0 memories for user")
    public ResponseEntity<Void> deleteAllMemories(
        @RequestHeader("X-User-Id") UUID userId
    ) {
        mem0Client.deleteAllMemories(userId);
        return ResponseEntity.noContent().build();
    }

    private DailyInsightResponse toInsightResponse(DailyInsight i) {
        return new DailyInsightResponse(
            i.getId(), i.getInsightDate(), i.getContent(),
            i.getSuggestions(), i.getTasksCompleted(), i.getCreatedAt()
        );
    }
}
