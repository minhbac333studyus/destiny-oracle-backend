package com.destinyoracle.controller;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/monitor")
@RequiredArgsConstructor
public class MonitorController {

    private final EntityManager em;

    @GetMapping("/db-stats")
    public Map<String, Object> dbStats() {
        return Map.of(
            "cards", countSafe("destiny_cards"),
            "users", countSafe("app_users"),
            "conversations", countSafe("ai_conversations"),
            "messages", countSafe("ai_messages"),
            "plans", countSafe("saved_plans"),
            "tasks", countSafe("tasks"),
            "reminders", countSafe("reminders"),
            "insights", countSafe("daily_insights")
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
