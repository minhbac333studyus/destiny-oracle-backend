package com.destinyoracle.shared.context;

import com.destinyoracle.domain.chat.entity.AiMessage;
import com.destinyoracle.domain.chat.entity.ConversationMemory;
import com.destinyoracle.domain.chat.repository.AiMessageRepository;
import com.destinyoracle.domain.chat.repository.ConversationMemoryRepository;
import com.destinyoracle.domain.nutrition.entity.NutritionGoal;
import com.destinyoracle.domain.nutrition.repository.NutritionGoalRepository;
import com.destinyoracle.domain.plan.entity.SavedPlan;
import com.destinyoracle.domain.plan.repository.SavedPlanRepository;
import com.destinyoracle.integration.Mem0Client;
import com.destinyoracle.shared.ai.AiContextRouter.ContextLayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

/**
 * Dynamic context assembly — loads only the layers requested by AiContextRouter.
 *
 * Always included: System prompt (300-600t) + User message (200t)
 * Conditionally loaded based on router decision:
 *   RECENT_2/RECENT_4, PLAN, MEM0, SUMMARY, ACTION
 */
@Component
public class ContextAssembler {

    private static final Logger log = LoggerFactory.getLogger(ContextAssembler.class);

    private static final int HARD_CAP = 4500;
    private static final int SYSTEM_BUDGET = 300;
    private static final int SYSTEM_BUDGET_WITH_ACTIONS = 600;
    private static final int USER_MSG_BUDGET = 200;
    private static final int PLAN_BUDGET = 300;
    private static final int RECENT_BUDGET = 800;
    private static final int RECENT_BUDGET_LITE = 400;
    private static final int MEM0_BUDGET = 300;
    private static final int NUTRITION_BUDGET = 100;
    private static final int SUMMARY_BUDGET = 300;

    private final TokenCounter tokenCounter;
    private final AiMessageRepository messageRepo;
    private final ConversationMemoryRepository memoryRepo;
    private final SavedPlanRepository planRepo;
    private final NutritionGoalRepository nutritionGoalRepo;
    private final Mem0Client mem0Client;

    public ContextAssembler(
        TokenCounter tokenCounter,
        AiMessageRepository messageRepo,
        ConversationMemoryRepository memoryRepo,
        SavedPlanRepository planRepo,
        NutritionGoalRepository nutritionGoalRepo,
        Mem0Client mem0Client
    ) {
        this.tokenCounter = tokenCounter;
        this.messageRepo = messageRepo;
        this.memoryRepo = memoryRepo;
        this.planRepo = planRepo;
        this.nutritionGoalRepo = nutritionGoalRepo;
        this.mem0Client = mem0Client;
    }

    /** Base system prompt — always included. */
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

    /** Addendum injected ONLY when ACTION layer is requested. */
    private static final String ACTION_ADDENDUM = """

Include [ACTION]{json}[/ACTION] blocks at END of response (invisible to user, triggers backend creation).
Types: REMINDER{title,body?,scheduledAt} | TASK{name,category,steps:[{title,description}]} | PLAN{name,planType,content} | DAILY_PLAN{date,items:[...]}
Categories: WORKOUT/MEAL/SHOPPING/HABIT/STUDY/CUSTOM. PlanTypes: WORKOUT/MEAL/ROUTINE/SHOPPING/CUSTOM.
Use ISO datetime for scheduledAt. Calculate relative dates from current date below. Multiple blocks allowed.
For DAILY_PLAN: include date + items array with {title,category,scheduledTime("HH:mm"),estimatedDurationMinutes,reminderOffsetMinutes|null,children:[{title,category}]}.
ALWAYS include [ACTION] — the user expects the item to be created.
""";

    /**
     * Assembles context using only the layers requested by AiContextRouter.
     * System prompt + user message are always included.
     *
     * @param layers The set of context layers to load (from AiContextRouter.route())
     */
    public AssembledContext assemble(UUID userId, UUID conversationId, String newUserMessage, Set<ContextLayer> layers) {
        long assembleStart = System.currentTimeMillis();
        int usedTokens = 0;

        // ── Always: System prompt ──
        boolean needsActions = layers.contains(ContextLayer.ACTION);
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

        // ── Always: User message ──
        String userMsg = tokenCounter.truncateToFit(newUserMessage, USER_MSG_BUDGET);
        usedTokens += tokenCounter.estimate(userMsg);

        // ── Conditional: Saved plan context (PLAN layer) ──
        String planContext = "";
        if (layers.contains(ContextLayer.PLAN) && newUserMessage != null) {
            long planStart = System.currentTimeMillis();
            planContext = findRelevantPlanContext(userId, newUserMessage);
            planContext = tokenCounter.truncateToFit(planContext, PLAN_BUDGET);
            usedTokens += tokenCounter.estimate(planContext);
            log.info("[CTX] PLAN layer: {}ms", System.currentTimeMillis() - planStart);
        }

        // ── Conditional: Recent messages (RECENT_2 or RECENT_4) ──
        List<AssembledContext.MessagePair> recentMessages = new ArrayList<>();
        if (conversationId != null && (layers.contains(ContextLayer.RECENT_2) || layers.contains(ContextLayer.RECENT_4))) {
            long recentStart = System.currentTimeMillis();
            int count = layers.contains(ContextLayer.RECENT_4) ? 4 : 2;
            int budget = layers.contains(ContextLayer.RECENT_4) ? RECENT_BUDGET : RECENT_BUDGET_LITE;

            List<AiMessage> recent = messageRepo.findRecentUncompressed(conversationId, count);
            Collections.reverse(recent);
            int recentTokens = 0;
            for (AiMessage msg : recent) {
                int msgTokens = tokenCounter.estimate(msg.getContent());
                if (recentTokens + msgTokens > budget) break;
                recentMessages.add(new AssembledContext.MessagePair(msg.getRole(), msg.getContent()));
                recentTokens += msgTokens;
            }
            usedTokens += recentTokens;
            log.info("[CTX] RECENT_{} layer: {}ms ({} msgs)", count, System.currentTimeMillis() - recentStart, recentMessages.size());
        }

        // ── Conditional: Mem0 long-term memories (MEM0 layer) ──
        String mem0Memories = "";
        if (layers.contains(ContextLayer.MEM0)) {
            long mem0Start = System.currentTimeMillis();
            try {
                var future = CompletableFuture.supplyAsync(
                    () -> mem0Client.searchMemories(userId, newUserMessage, 8));
                var memories = future.get(2, TimeUnit.SECONDS);  // reduced from 8s
                if (!memories.isEmpty()) {
                    mem0Memories = memories.stream()
                        .map(Mem0Client.Mem0Memory::memory)
                        .collect(Collectors.joining("\n- ", "Known facts about user:\n- ", ""));
                    mem0Memories = tokenCounter.truncateToFit(mem0Memories, MEM0_BUDGET);
                    usedTokens += tokenCounter.estimate(mem0Memories);
                }
                log.info("[CTX] MEM0 layer: {}ms ({} memories)", System.currentTimeMillis() - mem0Start, memories.size());
            } catch (TimeoutException e) {
                log.warn("[CTX] MEM0 TIMED OUT after {}ms", System.currentTimeMillis() - mem0Start);
            } catch (Exception e) {
                log.warn("[CTX] MEM0 FAILED: {}", e.getMessage());
            }
        }

        // ── Conditional: Nutrition goals (NUTRITION layer) ──
        String nutritionContext = "";
        if (layers.contains(ContextLayer.NUTRITION)) {
            try {
                var goal = nutritionGoalRepo.findByUserId(userId);
                if (goal.isPresent()) {
                    NutritionGoal g = goal.get();
                    StringBuilder nb = new StringBuilder("User nutrition targets:\n");
                    nb.append("- Calories: ").append(g.getCalorieTarget()).append(" kcal/day\n");
                    nb.append("- Protein: ").append(g.getProteinGrams()).append("g | Fat: ").append(g.getFatGrams()).append("g | Carbs: ").append(g.getCarbGrams()).append("g\n");
                    if (g.getFitnessGoal() != null) nb.append("- Fitness goal: ").append(g.getFitnessGoal()).append("\n");
                    if (g.getActivityLevel() != null) nb.append("- Activity level: ").append(g.getActivityLevel()).append("\n");
                    nb.append("ALL meal plans MUST match these targets exactly.");
                    nutritionContext = tokenCounter.truncateToFit(nb.toString(), NUTRITION_BUDGET);
                    usedTokens += tokenCounter.estimate(nutritionContext);
                    log.info("[CTX] NUTRITION layer: {}cal target, {}g protein", g.getCalorieTarget(), g.getProteinGrams());
                }
            } catch (Exception e) {
                log.warn("[CTX] NUTRITION layer failed: {}", e.getMessage());
            }
        }

        // ── Conditional: Session summary (SUMMARY layer) ──
        String sessionSummary = "";
        if (layers.contains(ContextLayer.SUMMARY) && conversationId != null) {
            long summaryStart = System.currentTimeMillis();
            var summaries = memoryRepo.findByConversationIdOrderByCompressionRound(conversationId);
            if (!summaries.isEmpty()) {
                sessionSummary = summaries.stream()
                    .map(ConversationMemory::getSummary)
                    .collect(Collectors.joining("\n\n"));
                int remainingBudget = Math.min(SUMMARY_BUDGET, HARD_CAP - usedTokens);
                sessionSummary = tokenCounter.truncateToFit(sessionSummary, Math.max(0, remainingBudget));
                usedTokens += tokenCounter.estimate(sessionSummary);
            }
            log.info("[CTX] SUMMARY layer: {}ms", System.currentTimeMillis() - summaryStart);
        }

        long totalElapsed = System.currentTimeMillis() - assembleStart;
        log.info("[CTX] Assembly TOTAL: {}ms | {}t (cap: {}) | layers: {}", totalElapsed, usedTokens, HARD_CAP, layers);

        return new AssembledContext(
            system, userMsg, planContext, recentMessages,
            mem0Memories, nutritionContext, sessionSummary, usedTokens
        );
    }

    /**
     * Legacy method — still used by existing code that passes intent string.
     * Routes intent to layers internally.
     */
    public AssembledContext assemble(UUID userId, UUID conversationId, String newUserMessage, String intent) {
        Set<ContextLayer> layers = legacyIntentToLayers(intent);
        return assemble(userId, conversationId, newUserMessage, layers);
    }

    private Set<ContextLayer> legacyIntentToLayers(String intent) {
        return switch (intent) {
            case "TASK", "REMINDER", "DAILY_PLAN", "PLAN_SAVE" ->
                EnumSet.of(ContextLayer.RECENT_4, ContextLayer.MEM0, ContextLayer.PLAN, ContextLayer.SUMMARY, ContextLayer.ACTION);
            case "PLAN_QUERY" ->
                EnumSet.of(ContextLayer.RECENT_2, ContextLayer.PLAN);
            case "INSIGHT" ->
                EnumSet.of(ContextLayer.RECENT_4, ContextLayer.SUMMARY);
            default ->
                EnumSet.of(ContextLayer.RECENT_2);
        };
    }

    /**
     * Search user's saved plans for relevance to the message.
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
