package com.destinyoracle.shared.context;

import java.util.List;

/**
 * The assembled context sent to Claude for a chat message.
 * Each layer is pre-truncated to fit the 4000-token hard cap.
 */
public record AssembledContext(
    String systemPrompt,          // Layer 1: system instructions
    String userMessage,           // Layer 2: the new user message
    String savedPlanContext,      // Layer 3: relevant saved plan (if any)
    List<MessagePair> recentMessages,  // Layer 4: last N raw messages
    String mem0Memories,          // Layer 5: long-term memories from Mem0
    String nutritionContext,      // Layer 6: calorie/macro targets
    String sessionSummary,        // Layer 7: compressed older messages
    int totalTokenEstimate
) {
    public record MessagePair(String role, String content) {}
}
