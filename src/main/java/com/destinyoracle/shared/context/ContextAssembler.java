package com.destinyoracle.shared.context;

import com.destinyoracle.domain.chat.entity.AiMessage;
import com.destinyoracle.domain.chat.entity.ConversationMemory;
import com.destinyoracle.domain.chat.repository.AiMessageRepository;
import com.destinyoracle.domain.chat.repository.ConversationMemoryRepository;
import com.destinyoracle.domain.plan.entity.SavedPlan;
import com.destinyoracle.domain.plan.repository.SavedPlanRepository;
import com.destinyoracle.integration.Mem0Client;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 4-layer context assembly with hard budget of 4000 tokens.
 *
 * Priority 1: System prompt               300 tokens
 * Priority 2: New user message             200 tokens
 * Priority 3: Saved plan context           300 tokens (if relevant)
 * Priority 4: Recent 10 raw messages      1500 tokens
 * Priority 5: Mem0 long-term memories      400 tokens
 * Priority 6: Session summary              300 tokens
 * ──────────────────────────────────────────────────────
 * Hard cap:                               4000 tokens
 */
@Component
public class ContextAssembler {

    private static final Logger log = LoggerFactory.getLogger(ContextAssembler.class);

    private static final int HARD_CAP = 4000;
    private static final int SYSTEM_BUDGET = 300;
    private static final int USER_MSG_BUDGET = 200;
    private static final int PLAN_BUDGET = 300;
    private static final int RECENT_BUDGET = 1500;
    private static final int MEM0_BUDGET = 400;
    private static final int SUMMARY_BUDGET = 300;
    private static final int RECENT_MSG_COUNT = 10;

    private final TokenCounter tokenCounter;
    private final AiMessageRepository messageRepo;
    private final ConversationMemoryRepository memoryRepo;
    private final SavedPlanRepository planRepo;
    private final Mem0Client mem0Client;

    public ContextAssembler(
        TokenCounter tokenCounter,
        AiMessageRepository messageRepo,
        ConversationMemoryRepository memoryRepo,
        SavedPlanRepository planRepo,
        Mem0Client mem0Client
    ) {
        this.tokenCounter = tokenCounter;
        this.messageRepo = messageRepo;
        this.memoryRepo = memoryRepo;
        this.planRepo = planRepo;
        this.mem0Client = mem0Client;
    }

    private static final String SYSTEM_PROMPT = """
        You are Destiny Oracle, an AI personal growth assistant. You help users with:
        - Workout plans, meal plans, daily routines
        - Task management and habit tracking
        - Smart reminders and scheduling
        - Personal growth coaching tied to their Destiny Cards

        When the user asks for a plan (workout, meal, routine), generate it in structured JSON.
        When the user wants a reminder, extract the time and create it.
        When the user asks about an existing plan, check their saved plans first.

        Be warm, encouraging, and concise. Use emoji sparingly.
        Always consider the user's known preferences and physical limitations from memory.
        """;

    public AssembledContext assemble(UUID userId, UUID conversationId, String newUserMessage) {
        int usedTokens = 0;

        // Layer 1: System prompt (always present)
        String system = tokenCounter.truncateToFit(SYSTEM_PROMPT, SYSTEM_BUDGET);
        usedTokens += tokenCounter.estimate(system);

        // Layer 2: User message (always present)
        String userMsg = tokenCounter.truncateToFit(newUserMessage, USER_MSG_BUDGET);
        usedTokens += tokenCounter.estimate(userMsg);

        // Layer 3: Saved plan context (if relevant plan exists)
        String planContext = "";
        if (newUserMessage != null) {
            planContext = findRelevantPlanContext(userId, newUserMessage);
            planContext = tokenCounter.truncateToFit(planContext, PLAN_BUDGET);
            usedTokens += tokenCounter.estimate(planContext);
        }

        // Layer 4: Recent uncompressed messages
        List<AssembledContext.MessagePair> recentMessages = new ArrayList<>();
        if (conversationId != null) {
            List<AiMessage> recent = messageRepo.findRecentUncompressed(conversationId, RECENT_MSG_COUNT);
            Collections.reverse(recent);  // ASC order for conversation flow
            int recentTokens = 0;
            for (AiMessage msg : recent) {
                int msgTokens = tokenCounter.estimate(msg.getContent());
                if (recentTokens + msgTokens > RECENT_BUDGET) break;
                recentMessages.add(new AssembledContext.MessagePair(msg.getRole(), msg.getContent()));
                recentTokens += msgTokens;
            }
            usedTokens += recentTokens;
        }

        // Layer 5: Mem0 long-term memories
        String mem0Memories = "";
        try {
            var memories = mem0Client.searchMemories(userId, newUserMessage, 5);
            if (!memories.isEmpty()) {
                mem0Memories = memories.stream()
                    .map(Mem0Client.Mem0Memory::memory)
                    .collect(Collectors.joining("\n- ", "Known facts about user:\n- ", ""));
                mem0Memories = tokenCounter.truncateToFit(mem0Memories, MEM0_BUDGET);
                usedTokens += tokenCounter.estimate(mem0Memories);
            }
        } catch (Exception e) {
            log.debug("Mem0 unavailable, skipping long-term memory: {}", e.getMessage());
        }

        // Layer 6: Session summary (compressed older messages)
        String sessionSummary = "";
        if (conversationId != null) {
            var summaries = memoryRepo.findByConversationIdOrderByCompressionRound(conversationId);
            if (!summaries.isEmpty()) {
                sessionSummary = summaries.stream()
                    .map(ConversationMemory::getSummary)
                    .collect(Collectors.joining("\n\n"));
                int remainingBudget = Math.min(SUMMARY_BUDGET, HARD_CAP - usedTokens);
                sessionSummary = tokenCounter.truncateToFit(sessionSummary, Math.max(0, remainingBudget));
                usedTokens += tokenCounter.estimate(sessionSummary);
            }
        }

        log.debug("Context assembled: {} tokens (cap: {})", usedTokens, HARD_CAP);

        return new AssembledContext(
            system, userMsg, planContext, recentMessages,
            mem0Memories, sessionSummary, usedTokens
        );
    }

    /**
     * Search user's saved plans for relevance to the message.
     * Simple keyword matching — could be upgraded to semantic search.
     */
    private String findRelevantPlanContext(UUID userId, String message) {
        String lower = message.toLowerCase();
        List<SavedPlan> plans = planRepo.findByUserIdAndActiveTrue(userId);

        for (SavedPlan plan : plans) {
            if (lower.contains(plan.getSlug().replace("-", " "))
                || lower.contains(plan.getName().toLowerCase())) {
                return "User has a saved plan: \"" + plan.getName() + "\" (" + plan.getType()
                    + ")\nContent: " + plan.getContent();
            }
        }

        // Check by type keywords
        if (lower.contains("workout") || lower.contains("exercise") || lower.contains("leg") || lower.contains("arm")) {
            var workouts = planRepo.findByUserIdAndTypeActive(userId, SavedPlan.PlanType.WORKOUT);
            if (!workouts.isEmpty()) {
                return workouts.stream()
                    .map(p -> "- " + p.getName() + " (slug: " + p.getSlug() + ")")
                    .collect(Collectors.joining("\n", "User's saved workout plans:\n", ""));
            }
        }

        if (lower.contains("meal") || lower.contains("food") || lower.contains("eat") || lower.contains("recipe")) {
            var meals = planRepo.findByUserIdAndTypeActive(userId, SavedPlan.PlanType.MEAL);
            if (!meals.isEmpty()) {
                return meals.stream()
                    .map(p -> "- " + p.getName() + " (slug: " + p.getSlug() + ")")
                    .collect(Collectors.joining("\n", "User's saved meal plans:\n", ""));
            }
        }

        return "";
    }
}
