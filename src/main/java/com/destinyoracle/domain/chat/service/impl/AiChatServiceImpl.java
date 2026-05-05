package com.destinyoracle.domain.chat.service.impl;

import com.destinyoracle.domain.chat.entity.AiConversation;
import com.destinyoracle.domain.chat.entity.AiMessage;
import com.destinyoracle.domain.chat.repository.AiConversationRepository;
import com.destinyoracle.domain.chat.repository.AiMessageRepository;
import com.destinyoracle.dto.response.ConversationResponse;
import com.destinyoracle.domain.notification.entity.Reminder;
import com.destinyoracle.domain.notification.repository.ReminderRepository;
import com.destinyoracle.domain.task.entity.Task;
import com.destinyoracle.integration.Mem0Client;
import com.destinyoracle.config.AiRoutingConfig;
import com.destinyoracle.domain.plan.entity.SavedPlan;
import com.destinyoracle.dto.request.SavePlanRequest;
import com.destinyoracle.domain.chat.service.AiChatService;
import com.destinyoracle.domain.plan.service.SavedPlanService;
import com.destinyoracle.domain.dailyplan.service.DailyPlanAiService;
import com.destinyoracle.domain.dailyplan.service.DailyPlanService;
import com.destinyoracle.domain.task.service.TaskService;
import com.destinyoracle.domain.task.service.TaskService.StepInput;
import com.destinyoracle.shared.ai.AiContextRouter;
import com.destinyoracle.shared.ai.ConversationCompressor;
import com.destinyoracle.shared.ai.IntentClassifier;
import com.destinyoracle.shared.context.AssembledContext;
import com.destinyoracle.shared.context.ContextAssembler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Service
public class AiChatServiceImpl implements AiChatService {

    private static final Logger log = LoggerFactory.getLogger(AiChatServiceImpl.class);

    private final ChatClient anthropicClient;
    private final ChatClient ollamaClient;
    private final AiRoutingConfig routingConfig;
    private final AiConversationRepository conversationRepo;
    private final AiMessageRepository messageRepo;
    private final ContextAssembler contextAssembler;
    private final AiContextRouter contextRouter;
    private final IntentClassifier intentClassifier;
    private final ConversationCompressor compressor;
    private final Mem0Client mem0Client;
    private final com.destinyoracle.shared.context.TokenCounter tokenCounter;
    private final TaskService taskService;
    private final SavedPlanService savedPlanService;
    private final DailyPlanAiService dailyPlanAiService;
    private final DailyPlanService dailyPlanService;
    private final ReminderRepository reminderRepo;

    public AiChatServiceImpl(
        @Qualifier("anthropicChatClient") ChatClient.Builder anthropicBuilder,
        @Qualifier("ollamaChatClient")    ChatClient.Builder ollamaBuilder,
        AiRoutingConfig routingConfig,
        AiConversationRepository conversationRepo,
        AiMessageRepository messageRepo,
        ContextAssembler contextAssembler,
        AiContextRouter contextRouter,
        IntentClassifier intentClassifier,
        ConversationCompressor compressor,
        Mem0Client mem0Client,
        com.destinyoracle.shared.context.TokenCounter tokenCounter,
        TaskService taskService,
        SavedPlanService savedPlanService,
        DailyPlanAiService dailyPlanAiService,
        DailyPlanService dailyPlanService,
        ReminderRepository reminderRepo
    ) {
        this.anthropicClient = anthropicBuilder.build();
        this.ollamaClient    = ollamaBuilder.build();
        this.routingConfig   = routingConfig;
        this.conversationRepo = conversationRepo;
        this.messageRepo = messageRepo;
        this.contextAssembler = contextAssembler;
        this.contextRouter = contextRouter;
        this.intentClassifier = intentClassifier;
        this.compressor = compressor;
        this.mem0Client = mem0Client;
        this.tokenCounter = tokenCounter;
        this.taskService = taskService;
        this.savedPlanService = savedPlanService;
        this.dailyPlanAiService = dailyPlanAiService;
        this.dailyPlanService = dailyPlanService;
        this.reminderRepo = reminderRepo;
    }

    private ChatClient activeChatClient() {
        return "anthropic".equals(routingConfig.getChatProvider()) ? anthropicClient : ollamaClient;
    }

    @Override
    public Flux<String> chat(UUID userId, UUID conversationId, String message) {
        long chatStart = System.currentTimeMillis();
        log.info("[TIMING] ========== CHAT REQUEST START ==========");

        // 1. Get or create conversation
        long convStart = System.currentTimeMillis();
        AiConversation conversation;
        if (conversationId != null) {
            conversation = conversationRepo.findById(conversationId)
                .orElseThrow(() -> new RuntimeException("Conversation not found: " + conversationId));
        } else {
            conversation = AiConversation.builder()
                .userId(userId)
                .title(message.length() > 50 ? message.substring(0, 50) + "..." : message)
                .build();
            conversation = conversationRepo.save(conversation);
        }
        log.info("[TIMING] Step 1 - Get/create conversation: {}ms", System.currentTimeMillis() - convStart);

        final UUID convId = conversation.getId();

        // 2. Save user message
        long saveStart = System.currentTimeMillis();
        AiMessage userMsg = AiMessage.builder()
            .conversation(conversation)
            .role("USER")
            .content(message)
            .build();
        messageRepo.save(userMsg);
        log.info("[TIMING] Step 2 - Save user message: {}ms", System.currentTimeMillis() - saveStart);

        // 3. AI Router — decide which context layers are needed
        long routerStart = System.currentTimeMillis();
        var layers = contextRouter.route(message);
        log.info("[TIMING] Step 3 - AI context routing: {}ms (layers: {})", System.currentTimeMillis() - routerStart, layers);

        // 3b. Also classify intent (still needed for ACTION block post-processing)
        IntentClassifier.Intent intent = intentClassifier.classify(message);

        // 4. Assemble context — only load layers requested by router
        long ctxStart = System.currentTimeMillis();
        AssembledContext ctx = contextAssembler.assemble(userId, convId, message, layers);
        log.info("[TIMING] Step 4 - Context assembly: {}ms", System.currentTimeMillis() - ctxStart);

        // 5. Build messages for Claude (single merged system message to reduce framing overhead)
        List<Message> messages = new ArrayList<>();
        StringBuilder systemBlock = new StringBuilder(ctx.systemPrompt());
        if (!ctx.sessionSummary().isEmpty()) {
            systemBlock.append("\n\nPrevious conversation summary:\n").append(ctx.sessionSummary());
        }
        if (!ctx.mem0Memories().isEmpty()) {
            systemBlock.append("\n\n").append(ctx.mem0Memories());
        }
        if (!ctx.nutritionContext().isEmpty()) {
            systemBlock.append("\n\n").append(ctx.nutritionContext());
        }
        if (!ctx.savedPlanContext().isEmpty()) {
            systemBlock.append("\n\nRelevant saved plan:\n").append(ctx.savedPlanContext());
        }
        messages.add(new SystemMessage(systemBlock.toString()));

        // Add recent messages for continuity
        for (AssembledContext.MessagePair pair : ctx.recentMessages()) {
            if ("USER".equals(pair.role())) {
                messages.add(new UserMessage(pair.content()));
            } else {
                messages.add(new AssistantMessage(pair.content()));
            }
        }

        // Add the new user message
        messages.add(new UserMessage(ctx.userMessage()));

        long preStreamElapsed = System.currentTimeMillis() - chatStart;
        log.info("[TIMING] Steps 1-5 TOTAL (before stream): {}ms", preStreamElapsed);

        // 5b. Emit routing info to client as first SSE event
        Sinks.Many<String> sink = Sinks.many().unicast().onBackpressureBuffer();
        try {
            long routerMs = System.currentTimeMillis() - routerStart;
            String routeJson = String.format(
                "[ROUTE]{\"layers\":[%s],\"routerMs\":%d,\"contextTokens\":%d}",
                layers.stream().map(l -> "\"" + l.name() + "\"").collect(java.util.stream.Collectors.joining(",")),
                routerMs, ctx.totalTokenEstimate());
            sink.tryEmitNext(routeJson);
        } catch (Exception e) {
            log.debug("Failed to emit route data: {}", e.getMessage());
        }

        // 6. Stream response from Claude (with prompt caching for Anthropic)

        StringBuilder fullResponse = new StringBuilder();
        final long streamStart = System.currentTimeMillis();
        final long[] firstChunkTime = {0};

        var promptBuilder = activeChatClient().prompt().messages(messages);

        // Enable Anthropic prompt caching — system prompt is cached (90% cheaper on repeat calls)
        if ("anthropic".equals(routingConfig.getChatProvider())) {
            promptBuilder = promptBuilder.options(
                org.springframework.ai.anthropic.AnthropicChatOptions.builder()
                    .cacheOptions(org.springframework.ai.anthropic.api.AnthropicCacheOptions.builder()
                        .strategy(org.springframework.ai.anthropic.api.AnthropicCacheStrategy.SYSTEM_ONLY)
                        .build())
                    .build()
            );
        }

        Flux<String> stream = promptBuilder.stream().content();

        stream.subscribe(
            chunk -> {
                if (firstChunkTime[0] == 0) {
                    firstChunkTime[0] = System.currentTimeMillis();
                    log.info("[TIMING] Step 6 - First chunk from Claude: {}ms after stream start, {}ms total from request",
                        firstChunkTime[0] - streamStart, firstChunkTime[0] - chatStart);
                }
                fullResponse.append(chunk);
                sink.tryEmitNext(chunk);
            },
            error -> {
                log.error("[TIMING] Stream ERROR after {}ms: {}", System.currentTimeMillis() - chatStart, error.getMessage());
                sink.tryEmitError(error);
            },
            () -> {
                long streamEnd = System.currentTimeMillis();
                log.info("[TIMING] Step 6 - Stream complete: {}ms streaming, {}ms total from request",
                    streamEnd - streamStart, streamEnd - chatStart);

                // Save assistant response (strip [ACTION] blocks from visible content)
                long postStart = System.currentTimeMillis();
                String response = fullResponse.toString();
                String visibleResponse = response.replaceAll("\\[ACTION].*?\\[/ACTION]", "").trim();
                AiMessage assistantMsg = AiMessage.builder()
                    .conversation(conversationRepo.getReferenceById(convId))
                    .role("ASSISTANT")
                    .content(visibleResponse)
                    .actionType(intent != IntentClassifier.Intent.GENERAL ? intent.name() : null)
                    .build();
                messageRepo.save(assistantMsg);
                log.info("[TIMING] Step 7 - Save assistant message: {}ms", System.currentTimeMillis() - postStart);

                // Send token usage as final SSE event before completing
                try {
                    int inputTokens = ctx.totalTokenEstimate();
                    int outputTokens = tokenCounter.estimate(response);
                    int totalTokens = inputTokens + outputTokens;
                    String model = routingConfig.getChatProvider().equals("anthropic") ? "claude-haiku-4-5" : routingConfig.getOllamaModel();
                    // Haiku 4.5: $0.80/M input, $4.00/M output; Ollama: free
                    double inputCost = model.startsWith("claude") ? inputTokens / 1_000_000.0 * 0.80 : 0;
                    double outputCost = model.startsWith("claude") ? outputTokens / 1_000_000.0 * 4.00 : 0;
                    double totalCost = inputCost + outputCost;
                    String usageJson = String.format(
                        "[USAGE]{\"inputTokens\":%d,\"outputTokens\":%d,\"totalTokens\":%d,\"cost\":%.6f,\"model\":\"%s\"}",
                        inputTokens, outputTokens, totalTokens, totalCost, model);
                    sink.tryEmitNext(usageJson);
                    log.info("[TIMING] Usage: {} input + {} output = {} tokens, ${} ({})",
                        inputTokens, outputTokens, totalTokens, String.format("%.6f", totalCost), model);
                } catch (Exception e) {
                    log.debug("Failed to emit usage data: {}", e.getMessage());
                }

                // Complete the stream FIRST so frontend gets action buttons immediately
                sink.tryEmitComplete();
                log.info("[TIMING] ========== STREAM COMPLETE sent to client: {}ms total ==========", System.currentTimeMillis() - chatStart);

                // THEN fire-and-forget: extract user preferences and add to Mem0
                // Only extract when user revealed something substantive (skip casual messages)
                final String rawUserMsg = message;
                final String rawAssistantResp = response;
                if (rawUserMsg.length() > 30 && intent != IntentClassifier.Intent.GENERAL) {
                    CompletableFuture.runAsync(() -> extractAndStoreMemories(userId, rawUserMsg, rawAssistantResp));
                }

                // Fire-and-forget: check if compression is needed
                CompletableFuture.runAsync(() -> compressor.compressIfNeeded(convId));

                // Fire-and-forget: execute [ACTION] blocks (create tasks/reminders)
                CompletableFuture.runAsync(() -> executeActionBlocks(userId, response, convId));
            }
        );

        return sink.asFlux();
    }

    // ── ACTION block parser & executor ──────────────────────────────────

    /**
     * Parses [ACTION]{...json...}[/ACTION] blocks from AI response and creates
     * actual Task or Reminder entities. Runs async after stream completes.
     */
    @SuppressWarnings("unchecked")
    private void executeActionBlocks(UUID userId, String response, UUID convId) {
        try {
            var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            mapper.configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

            int searchFrom = 0;
            int actionsExecuted = 0;

            while (true) {
                int start = response.indexOf("[ACTION]", searchFrom);
                if (start < 0) break;
                int jsonStart = start + "[ACTION]".length();
                int end = response.indexOf("[/ACTION]", jsonStart);
                if (end < 0) break;

                String jsonStr = response.substring(jsonStart, end).trim();
                searchFrom = end + "[/ACTION]".length();

                try {
                    var json = mapper.readValue(jsonStr, java.util.Map.class);
                    String type = ((String) json.getOrDefault("type", "")).toUpperCase();

                    if ("REMINDER".equals(type)) {
                        createReminderFromAction(userId, json, convId);
                        actionsExecuted++;
                    } else if ("TASK".equals(type)) {
                        createTaskFromAction(userId, json, convId);
                        actionsExecuted++;
                    } else if ("PLAN".equals(type)) {
                        createPlanFromAction(userId, json);
                        actionsExecuted++;
                    } else if ("DAILY_PLAN".equals(type)) {
                        createDailyPlanFromAction(userId, json);
                        actionsExecuted++;
                    } else {
                        log.warn("Unknown ACTION type '{}' in chat response", type);
                    }
                } catch (Exception e) {
                    log.warn("Failed to parse ACTION block: {} — json: {}", e.getMessage(), jsonStr);
                }
            }

            if (actionsExecuted > 0) {
                log.info("Executed {} ACTION block(s) from chat response for user {}", actionsExecuted, userId);
            }
        } catch (Exception e) {
            log.warn("ACTION block processing failed: {}", e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private void createReminderFromAction(UUID userId, java.util.Map<String, Object> json, UUID convId) {
        String title = (String) json.getOrDefault("title", "Reminder");
        String body = (String) json.get("body");
        String scheduledAtStr = (String) json.get("scheduledAt");

        java.time.LocalDateTime scheduledAt;
        if (scheduledAtStr != null) {
            try {
                scheduledAt = java.time.LocalDateTime.parse(scheduledAtStr);
            } catch (Exception e) {
                // Try with date only
                try {
                    scheduledAt = java.time.LocalDate.parse(scheduledAtStr.substring(0, 10))
                        .atTime(9, 0); // default 9 AM
                } catch (Exception e2) {
                    scheduledAt = java.time.LocalDateTime.now().plusHours(1);
                    log.warn("Could not parse scheduledAt '{}', defaulting to 1h from now", scheduledAtStr);
                }
            }
        } else {
            scheduledAt = java.time.LocalDateTime.now().plusHours(1);
        }

        Reminder reminder = Reminder.builder()
            .userId(userId)
            .title(title)
            .body(body)
            .scheduledAt(scheduledAt)
            .conversationId(convId)
            .build();
        reminderRepo.save(reminder);
        log.info("Created reminder '{}' scheduled at {} for user {}", title, scheduledAt, userId);
    }

    @SuppressWarnings("unchecked")
    private void createTaskFromAction(UUID userId, java.util.Map<String, Object> json, UUID convId) {
        String name = (String) json.getOrDefault("name", "Task");
        String categoryStr = ((String) json.getOrDefault("category", "CUSTOM")).toUpperCase();

        Task.TaskCategory category;
        try {
            category = Task.TaskCategory.valueOf(categoryStr);
        } catch (IllegalArgumentException e) {
            category = Task.TaskCategory.CUSTOM;
        }

        List<StepInput> steps = new ArrayList<>();
        var rawSteps = (List<java.util.Map<String, String>>) json.get("steps");
        if (rawSteps != null) {
            for (var s : rawSteps) {
                steps.add(new StepInput(
                    s.getOrDefault("title", "Step"),
                    s.get("description"),
                    null));
            }
        }
        if (steps.isEmpty()) {
            steps.add(new StepInput(name, (String) json.get("body"), null));
        }

        taskService.createTask(userId, name, category, steps);
        log.info("Created task '{}' ({}) with {} steps for user {}", name, category, steps.size(), userId);
    }

    private void createPlanFromAction(UUID userId, java.util.Map<String, Object> json) {
        String name = (String) json.getOrDefault("name", "Untitled Plan");
        String planTypeStr = ((String) json.getOrDefault("planType", "CUSTOM")).toUpperCase();
        String content = (String) json.getOrDefault("content", "");

        SavedPlan.PlanType planType;
        try {
            planType = SavedPlan.PlanType.valueOf(planTypeStr);
        } catch (IllegalArgumentException e) {
            planType = SavedPlan.PlanType.CUSTOM;
        }

        if (content.isBlank()) {
            log.warn("Skipping PLAN action with empty content for user {}", userId);
            return;
        }

        var request = new SavePlanRequest(name, planType, null, content, null);
        savedPlanService.savePlan(userId, request);
        log.info("Created plan '{}' ({}) for user {}", name, planType, userId);
    }

    @SuppressWarnings("unchecked")
    private void createDailyPlanFromAction(UUID userId, java.util.Map<String, Object> json) {
        String dateStr = (String) json.get("date");
        java.time.LocalDate date;
        try {
            date = dateStr != null ? java.time.LocalDate.parse(dateStr) : java.time.LocalDate.now();
        } catch (Exception e) {
            date = java.time.LocalDate.now();
        }

        // Parse items directly from the ACTION block — no second AI call
        var items = (List<java.util.Map<String, Object>>) json.get("items");
        if (items != null && !items.isEmpty()) {
            try {
                dailyPlanService.savePlanFromActionBlock(userId, date, items);
                log.info("Created daily plan with {} items for {} on {} via chat action", items.size(), userId, date);
            } catch (Exception e) {
                log.warn("Failed to save daily plan from chat action: {}", e.getMessage());
            }
        } else {
            log.warn("DAILY_PLAN action had no items for user {} on {}", userId, date);
        }
    }


    // ── Smart memory extraction ─────────────────────────────────────────

    private static final String EXTRACT_PROMPT = """
Extract user facts as bullets (times, diet, goals, health, workout preferences).
Return "NONE" if no new facts. Be specific with numbers/times.
""";

    private void extractAndStoreMemories(UUID userId, String userMessage, String assistantResponse) {
        try {
            long start = System.currentTimeMillis();

            // Ask Claude to extract structured facts from the conversation
            String extraction = activeChatClient().prompt()
                .system(EXTRACT_PROMPT)
                .user("User said: " + userMessage.substring(0, Math.min(500, userMessage.length()))
                    + "\n\nAssistant replied: " + assistantResponse.substring(0, Math.min(1000, assistantResponse.length())))
                .call()
                .content();

            if (extraction == null || extraction.isBlank() || extraction.trim().equalsIgnoreCase("NONE")) {
                log.debug("No new user facts extracted from conversation");
                return;
            }

            // Send the structured extraction to Mem0 (much better than raw conversation)
            mem0Client.addMemory(userId, userMessage, extraction);
            log.info("[TIMING] Smart memory extraction + store: {}ms — extracted: {}",
                System.currentTimeMillis() - start,
                extraction.substring(0, Math.min(200, extraction.length())));
        } catch (Exception e) {
            // Fallback: send raw conversation to Mem0
            try {
                mem0Client.addMemory(userId, userMessage, assistantResponse);
            } catch (Exception e2) {
                log.debug("Mem0 memory add failed: {}", e2.getMessage());
            }
        }
    }

    @Override
    public List<ConversationResponse> listConversations(UUID userId) {
        return conversationRepo.findByUserIdOrderByUpdatedAtDesc(userId).stream()
            .map(conv -> new ConversationResponse(
                conv.getId(),
                conv.getTitle(),
                conv.getCreatedAt(),
                conv.getUpdatedAt(),
                List.of()  // Don't load messages for list view
            ))
            .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public ConversationResponse getConversation(UUID userId, UUID conversationId) {
        AiConversation conv = conversationRepo.findById(conversationId)
            .orElseThrow(() -> new RuntimeException("Conversation not found"));

        if (!conv.getUserId().equals(userId)) {
            throw new RuntimeException("Access denied");
        }

        List<ConversationResponse.MessageResponse> messages = conv.getMessages().stream()
            .filter(m -> !m.getCompressed())
            .map(m -> new ConversationResponse.MessageResponse(
                m.getId(), m.getRole(), m.getContent(),
                m.getActionType(), m.getActionPayload(), m.getCreatedAt()
            ))
            .toList();

        return new ConversationResponse(
            conv.getId(), conv.getTitle(),
            conv.getCreatedAt(), conv.getUpdatedAt(), messages
        );
    }

    @Override
    @Transactional
    public void deleteConversation(UUID userId, UUID conversationId) {
        AiConversation conv = conversationRepo.findById(conversationId)
            .orElseThrow(() -> new RuntimeException("Conversation not found"));

        if (!conv.getUserId().equals(userId)) {
            throw new RuntimeException("Access denied");
        }

        conversationRepo.delete(conv);
    }
}
