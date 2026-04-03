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
import java.util.concurrent.*;
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

    private static final int HARD_CAP = 4500;
    private static final int SYSTEM_BUDGET = 300;
    private static final int SYSTEM_BUDGET_WITH_ACTIONS = 600;  // when ACTION addendum is injected
    private static final int USER_MSG_BUDGET = 200;
    private static final int PLAN_BUDGET = 300;
    private static final int RECENT_BUDGET = 800;
    private static final int MEM0_BUDGET = 300;
    private static final int SUMMARY_BUDGET = 300;
    private static final int RECENT_MSG_COUNT = 4;

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

    /** Base system prompt — always included. Kept lean for general conversations. */
    private static final String SYSTEM_PROMPT_BASE = """
You help users with:
- Workout plans, meal plans, daily routines
- Task management and habit tracking
- Smart reminders and scheduling
- Personal growth coaching tied to their dream

FORMATTING RULES:
- NEVER output raw JSON. Always format plans as clean, readable markdown.
- For meal plans: use markdown tables with columns for Meal, Foods, Calories, Macros.
- For workout plans: use markdown tables with columns for Exercise, Sets, Reps, Rest.
- Use **bold** for section headers, bullet points for tips.
- Keep each day concise — one table per day, not walls of text.
- At the end of a plan, suggest follow-up actions on separate lines starting with ">>": \
e.g. ">> Generate shopping list" or ">> Create reminder cards"
- When the user follow a previous message or plan, \
execute the action concisely — do NOT repeat the previous plan content. \
Just do what they asked.

When the user asks about an existing plan, check their saved plans first.
Be warm, encouraging, and concise.

CRITICAL: Always check the "Known facts about user" section below. \
If it mentions an eating window, meal timing, dietary protocol (e.g. Blueprint), or schedule preferences — \
you MUST follow them. Never generate plans that contradict the user's known preferences.
""";

    /**
     * Addendum injected ONLY when intent is TASK or REMINDER.
     * Saves ~200 tokens on all general conversations.
     */
    private static final String ACTION_ADDENDUM = """

Include [ACTION]{json}[/ACTION] blocks at END of response (invisible to user, triggers backend creation).
Types: REMINDER{title,body?,scheduledAt} | TASK{name,category,steps:[{title,description}]} | PLAN{name,planType,content}
Categories: WORKOUT/MEAL/SHOPPING/HABIT/STUDY/CUSTOM. PlanTypes: WORKOUT/MEAL/ROUTINE/SHOPPING/CUSTOM.
Use ISO datetime for scheduledAt. Calculate relative dates from current date below. Multiple blocks allowed.
ALWAYS include [ACTION] — the user expects the item to be created.
""";

    /**
     * Assembles the full context for a chat request.
     *
     * @param intent The classified intent (TASK, REMINDER, GENERAL, etc.) — drives prompt chaining.
     *               When intent is TASK or REMINDER, ACTION block instructions + current datetime are injected.
     *               For all other intents, only the lean base prompt is used (saves ~200 tokens).
     */
    public AssembledContext assemble(UUID userId, UUID conversationId, String newUserMessage, String intent) {
        long assembleStart = System.currentTimeMillis();
        int usedTokens = 0;

        // Layer 1: System prompt — conditionally chain ACTION addendum based on intent
        boolean needsActions = "TASK".equals(intent) || "REMINDER".equals(intent) || "PLAN_SAVE".equals(intent);
        StringBuilder systemBuilder = new StringBuilder(SYSTEM_PROMPT_BASE);
        if (needsActions) {
            systemBuilder.append(ACTION_ADDENDUM);
            systemBuilder.append("\nCurrent date/time: ")
                .append(java.time.LocalDateTime.now().format(
                    java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm (EEEE)")));
        }
        int sysBudget = needsActions ? SYSTEM_BUDGET_WITH_ACTIONS : SYSTEM_BUDGET;
        String system = tokenCounter.truncateToFit(systemBuilder.toString(), sysBudget);
        usedTokens += tokenCounter.estimate(system);

        // Layer 2: User message (always present)
        String userMsg = tokenCounter.truncateToFit(newUserMessage, USER_MSG_BUDGET);
        usedTokens += tokenCounter.estimate(userMsg);

        // Layer 3: Saved plan context (if relevant plan exists)
        long planStart = System.currentTimeMillis();
        String planContext = "";
        if (newUserMessage != null) {
            planContext = findRelevantPlanContext(userId, newUserMessage);
            planContext = tokenCounter.truncateToFit(planContext, PLAN_BUDGET);
            usedTokens += tokenCounter.estimate(planContext);
        }
        log.info("[TIMING] Layer 3 - Saved plans query: {}ms", System.currentTimeMillis() - planStart);

        // Layer 4: Recent uncompressed messages
        long recentStart = System.currentTimeMillis();
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
        log.info("[TIMING] Layer 4 - Recent messages query: {}ms (found {} msgs)", System.currentTimeMillis() - recentStart, recentMessages.size());

        // Layer 5: Mem0 long-term memories (with 8s timeout to avoid blocking chat)
        long mem0Start = System.currentTimeMillis();
        String mem0Memories = "";
        try {
            var future = CompletableFuture.supplyAsync(
                () -> mem0Client.searchMemories(userId, newUserMessage, 8));
            var memories = future.get(8, TimeUnit.SECONDS);
            long mem0Elapsed = System.currentTimeMillis() - mem0Start;
            log.info("[TIMING] Layer 5 - Mem0 search: {}ms (found {} memories)", mem0Elapsed, memories.size());
            if (!memories.isEmpty()) {
                mem0Memories = memories.stream()
                    .map(Mem0Client.Mem0Memory::memory)
                    .collect(Collectors.joining("\n- ", "Known facts about user:\n- ", ""));
                mem0Memories = tokenCounter.truncateToFit(mem0Memories, MEM0_BUDGET);
                usedTokens += tokenCounter.estimate(mem0Memories);
            }
        } catch (TimeoutException e) {
            log.warn("[TIMING] Layer 5 - Mem0 search TIMED OUT after {}ms", System.currentTimeMillis() - mem0Start);
        } catch (Exception e) {
            log.warn("[TIMING] Layer 5 - Mem0 FAILED after {}ms: {}", System.currentTimeMillis() - mem0Start, e.getMessage());
        }

        // Layer 6: Session summary (compressed older messages)
        long summaryStart = System.currentTimeMillis();
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
        log.info("[TIMING] Layer 6 - Session summary query: {}ms", System.currentTimeMillis() - summaryStart);

        long totalElapsed = System.currentTimeMillis() - assembleStart;
        log.info("[TIMING] Context assembly TOTAL: {}ms | {} tokens (cap: {})", totalElapsed, usedTokens, HARD_CAP);

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
