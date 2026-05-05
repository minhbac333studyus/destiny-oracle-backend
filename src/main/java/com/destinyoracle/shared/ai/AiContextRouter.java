package com.destinyoracle.shared.ai;

import com.destinyoracle.config.AiRoutingConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.EnumSet;
import java.util.Set;

/**
 * AI-powered context router. Uses a fast Haiku call (~50 tokens) to decide
 * which context layers are needed for each user message.
 *
 * Replaces fixed regex-based IntentClassifier with dynamic, intelligent routing.
 */
@Component
public class AiContextRouter {

    private static final Logger log = LoggerFactory.getLogger(AiContextRouter.class);

    /**
     * Context layers that can be selectively loaded.
     * System prompt + user message are ALWAYS included (not listed here).
     */
    public enum ContextLayer {
        NONE,       // no extra context — greetings, general knowledge
        RECENT_2,   // last 2 messages — lightweight continuity
        RECENT_4,   // last 4 messages — full conversation context
        PLAN,       // user's saved workout/meal/routine plans
        MEM0,       // long-term user preferences (diet, schedule, goals)
        NUTRITION,  // user's calorie/macro targets and fitness goals
        SUMMARY,    // compressed old conversation history
        ACTION      // enable [ACTION] block creation (tasks/reminders/plans)
    }

    private static final String ROUTER_PROMPT = """
Which context layers are needed to answer this message?

Layers:
NONE      — no extra context needed (greetings, general knowledge, simple facts)
RECENT_2  — last 2 messages (follow-up on recent topic)
RECENT_4  — last 4 messages (complex multi-turn conversation)
PLAN      — user's saved workout/meal/routine plans
MEM0      — user's personal preferences (diet, schedule, habits, goals)
NUTRITION — user's calorie target, macro targets (protein/fat/carbs), fitness goal
SUMMARY   — compressed older conversation history
ACTION    — enable creating tasks, reminders, daily plans

Rules:
- "hi", "hello", general knowledge questions → NONE
- Follow-up referencing previous message → RECENT_2
- Questions about user's body, diet, habits → MEM0
- "show my plan", "what's my workout" → RECENT_2, PLAN
- Meal plan, diet, food, nutrition, calories, macros → RECENT_2, MEM0, NUTRITION, ACTION
- Daily plan / schedule (includes meals) → RECENT_4, MEM0, NUTRITION, PLAN, SUMMARY, ACTION
- Complex multi-turn with context → RECENT_4, SUMMARY
- Create/generate task, reminder, workout plan → RECENT_4, MEM0, PLAN, ACTION

Reply ONLY layer names comma-separated. No explanation.

Message: "%s"
""";

    private final ChatClient anthropicClient;
    private final AiRoutingConfig routingConfig;

    public AiContextRouter(
        @Qualifier("anthropicChatClient") ChatClient.Builder anthropicBuilder,
        AiRoutingConfig routingConfig
    ) {
        this.anthropicClient = anthropicBuilder.build();
        this.routingConfig = routingConfig;
    }

    /**
     * Route a user message to the appropriate context layers.
     * Uses a fast AI call (~50 input tokens, ~5 output tokens).
     * Falls back to ALL layers if the router call fails.
     */
    public Set<ContextLayer> route(String userMessage) {
        long start = System.currentTimeMillis();
        try {
            String truncated = userMessage.length() > 200 ? userMessage.substring(0, 200) : userMessage;
            String prompt = String.format(ROUTER_PROMPT, truncated.replace("\"", "'"));

            String response = anthropicClient.prompt()
                .user(prompt)
                .call()
                .content();

            Set<ContextLayer> layers = parseLayers(response);
            long elapsed = System.currentTimeMillis() - start;
            log.info("[ROUTER] {}ms — message: '{}' → layers: {}", elapsed, truncate(userMessage, 50), layers);
            return layers;

        } catch (Exception e) {
            log.warn("[ROUTER] Failed ({}ms), falling back to ALL layers: {}", System.currentTimeMillis() - start, e.getMessage());
            return EnumSet.allOf(ContextLayer.class);
        }
    }

    /**
     * Parse AI response like "RECENT_2, MEM0, ACTION" into EnumSet.
     */
    private Set<ContextLayer> parseLayers(String response) {
        if (response == null || response.isBlank()) {
            return EnumSet.allOf(ContextLayer.class);
        }

        Set<ContextLayer> layers = EnumSet.noneOf(ContextLayer.class);
        for (String part : response.toUpperCase().split("[,\\s]+")) {
            String trimmed = part.trim();
            if (trimmed.isEmpty()) continue;
            try {
                layers.add(ContextLayer.valueOf(trimmed));
            } catch (IllegalArgumentException e) {
                // AI returned unknown layer name — skip
            }
        }

        // Safety: if AI returned nothing parseable, use all
        if (layers.isEmpty()) {
            return EnumSet.allOf(ContextLayer.class);
        }

        // NONE means no extra context — just system prompt + user message
        if (layers.contains(ContextLayer.NONE)) {
            return EnumSet.of(ContextLayer.NONE);
        }

        // RECENT_4 supersedes RECENT_2
        if (layers.contains(ContextLayer.RECENT_4)) {
            layers.remove(ContextLayer.RECENT_2);
        }

        return layers;
    }

    private String truncate(String s, int max) {
        return s.length() > max ? s.substring(0, max) + "..." : s;
    }
}
