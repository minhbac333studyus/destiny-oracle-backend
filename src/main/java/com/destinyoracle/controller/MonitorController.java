package com.destinyoracle.controller;

import com.destinyoracle.config.AiRoutingConfig;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/monitor")
@RequiredArgsConstructor
public class MonitorController {

    private final EntityManager em;
    private final AiRoutingConfig routingConfig;

    @GetMapping("/db-stats")
    public Map<String, Object> dbStats() {
        return Map.of(
            "cards", countSafe("destiny_cards"),
            "users", countSafe("users"),
            "conversations", countSafe("ai_conversations"),
            "messages", countSafe("ai_messages"),
            "plans", countSafe("saved_plans"),
            "tasks", countSafe("tasks"),
            "reminders", countSafe("reminders"),
            "insights", countSafe("daily_insights")
        );
    }

    /** Returns current AI routing table for all services */
    @GetMapping("/ai-routing")
    public Map<String, Object> getAiRouting() {
        var result = new LinkedHashMap<String, Object>();
        result.put("anthropicModel", routingConfig.getAnthropicModel());
        result.put("ollamaModel",    routingConfig.getOllamaModel());
        result.put("services", Map.of(
            "chat",        serviceInfo("User Chat (AiChatService)",         routingConfig.getChatProvider(),        routingConfig),
            "insights",    serviceInfo("Daily Insights (InsightScheduler)", routingConfig.getInsightProvider(),    routingConfig),
            "compression", serviceInfo("Conversation Compressor",           routingConfig.getCompressionProvider(), routingConfig)
        ));
        // Fixed services (not switchable at runtime)
        result.put("fixed", Map.of(
            "imagePrompt",   Map.of("service", "Image Prompt Generation", "provider", "anthropic", "fixed", true),
            "stageContent",  Map.of("service", "Stage Content Generation", "provider", "anthropic", "fixed", true)
        ));
        return result;
    }

    /** Updates provider for a specific service. Body: { "service": "chat", "provider": "ollama" } */
    @PostMapping("/ai-routing")
    public ResponseEntity<Map<String, Object>> updateAiRouting(@RequestBody Map<String, String> body) {
        String service  = body.get("service");
        String provider = body.get("provider");

        switch (service) {
            case "chat"        -> routingConfig.setChatProvider(provider);
            case "insights"    -> routingConfig.setInsightProvider(provider);
            case "compression" -> routingConfig.setCompressionProvider(provider);
            default -> { return ResponseEntity.badRequest().body(Map.of("error", "Unknown service: " + service)); }
        }

        return ResponseEntity.ok(Map.of(
            "updated", service,
            "provider", provider,
            "model", "anthropic".equals(provider) ? routingConfig.getAnthropicModel() : routingConfig.getOllamaModel()
        ));
    }

    private Map<String, Object> serviceInfo(String name, String provider, AiRoutingConfig cfg) {
        return Map.of(
            "service",  name,
            "provider", provider,
            "model",    "anthropic".equals(provider) ? cfg.getAnthropicModel() : cfg.getOllamaModel(),
            "fixed",    false
        );
    }

    private long countSafe(String table) {
        try {
            return ((Number) em.createNativeQuery("SELECT count(*) FROM " + table)
                .getSingleResult()).longValue();
        } catch (Exception e) {
            return -1;
        }
    }
}
