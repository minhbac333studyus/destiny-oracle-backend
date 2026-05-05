package com.destinyoracle.domain.dailyplan.service.impl;

import com.destinyoracle.config.AiRoutingConfig;
import com.destinyoracle.domain.dailyplan.entity.*;
import com.destinyoracle.domain.dailyplan.repository.*;
import com.destinyoracle.domain.dailyplan.service.DailyPlanAiService;
import com.destinyoracle.dto.response.DailyPlanResponse;
import com.destinyoracle.integration.Mem0Client;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Slf4j
@Service
public class DailyPlanAiServiceImpl implements DailyPlanAiService {

    private final ChatClient anthropicClient;
    private final ChatClient ollamaClient;
    private final AiRoutingConfig routingConfig;
    private final ScheduleTemplateRepository templateRepo;
    private final DailyPlanRepository planRepo;
    private final PlanItemRepository itemRepo;
    private final Mem0Client mem0Client;
    private final DailyPlanServiceImpl planService;
    private final ObjectMapper mapper;

    public DailyPlanAiServiceImpl(
        @Qualifier("anthropicChatClient") ChatClient.Builder anthropicBuilder,
        @Qualifier("ollamaChatClient") ChatClient.Builder ollamaBuilder,
        AiRoutingConfig routingConfig,
        ScheduleTemplateRepository templateRepo,
        DailyPlanRepository planRepo,
        PlanItemRepository itemRepo,
        Mem0Client mem0Client,
        DailyPlanServiceImpl planService
    ) {
        this.anthropicClient = anthropicBuilder.build();
        this.ollamaClient = ollamaBuilder.build();
        this.routingConfig = routingConfig;
        this.templateRepo = templateRepo;
        this.planRepo = planRepo;
        this.itemRepo = itemRepo;
        this.mem0Client = mem0Client;
        this.planService = planService;
        this.mapper = new ObjectMapper();
        this.mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    private ChatClient activeChatClient() {
        return "anthropic".equals(routingConfig.getChatProvider()) ? anthropicClient : ollamaClient;
    }

    private static final String BACKWARD_PLANNING_PROMPT = """
You are a backward daily planner. Generate a structured daily plan by working \
BACKWARD from the terminal goal.

CURRENT DATE: %s
CURRENT TIME: %s

TERMINAL GOAL: %s at %s

SCHEDULE TEMPLATE:
- Fixed blocks: %s
- Meal times: %s
- Recurring reminders: %s

YESTERDAY'S PLAN STATUS:
%s

USER PREFERENCES (from memory):
%s

RULES:
1. Start from terminal_goal_time and work backward to fill the day.
2. Every meal needs prep time. If prep was NOT done yesterday, schedule it today.
3. Insert hydration reminders based on recurring_reminders during waking hours.
4. NEVER schedule over fixed_blocks (work hours, etc.).
5. Buffer 15 min between tasks for transitions.
6. For shopping trips: create a parent item with children for each thing to buy. \
   Children have NO scheduledTime and NO reminder — just title + category SHOPPING.
7. Parent items that need a reminder: set reminderOffsetMinutes (minutes BEFORE scheduledTime).
8. Sort all top-level items by scheduledTime ascending.

OUTPUT: Return ONLY a valid JSON array (no markdown, no explanation). Each element:
{
  "title": "string",
  "description": "string or null",
  "category": "MEAL_PREP|MEAL|EXERCISE|WORK|HYDRATION|CHORE|SELF_CARE|SHOPPING|OTHER",
  "scheduledTime": "HH:mm",
  "estimatedDurationMinutes": number,
  "isPrep": false,
  "reminderOffsetMinutes": number_or_null,
  "children": [
    {"title": "string", "category": "SHOPPING", "description": null}
  ]
}
""";

    @Override
    @Transactional
    public DailyPlanResponse generatePlan(UUID userId, LocalDate date) {
        log.info("Generating daily plan for user {} on {}", userId, date);

        // 1. Get schedule template
        boolean isWeekend = date.getDayOfWeek().getValue() >= 6;
        var dayType = isWeekend ? ScheduleTemplate.DayType.WEEKEND : ScheduleTemplate.DayType.WEEKDAY;
        var template = templateRepo.findByUserIdAndDayType(userId, dayType)
            .or(() -> templateRepo.findByUserIdAndDayType(userId, ScheduleTemplate.DayType.WEEKDAY))
            .orElse(null);

        if (template == null) {
            log.warn("No schedule template found for user {}, using defaults", userId);
            template = buildDefaultTemplate(userId);
        }

        // 2. Get yesterday's plan status
        String yesterdayStatus = getYesterdayStatus(userId, date.minusDays(1));

        // 3. Get user preferences from Mem0
        String userPrefs = getUserPreferences(userId);

        // 4. Build prompt
        String prompt = String.format(BACKWARD_PLANNING_PROMPT,
            date,
            LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm")),
            template.getTerminalGoal() != null ? template.getTerminalGoal() : "Sleep",
            template.getTerminalGoalTime() != null ? template.getTerminalGoalTime().toString() : "21:00",
            template.getFixedBlocks() != null ? template.getFixedBlocks() : "[]",
            template.getMealTimes() != null ? template.getMealTimes() : "[]",
            template.getRecurringReminders() != null ? template.getRecurringReminders() : "[]",
            yesterdayStatus,
            userPrefs
        );

        // 5. Call AI
        String aiResponse;
        try {
            aiResponse = activeChatClient().prompt()
                .user(prompt)
                .call()
                .content();
        } catch (Exception e) {
            log.error("AI call failed for daily plan generation: {}", e.getMessage());
            throw new RuntimeException("Failed to generate daily plan: " + e.getMessage());
        }

        // 6. Parse response and create plan
        return parsePlanFromAiResponse(userId, date, template, aiResponse, prompt);
    }

    @Override
    @Transactional
    public DailyPlanResponse replan(UUID userId, UUID planId, String reason) {
        DailyPlan existingPlan = planRepo.findById(planId)
            .orElseThrow(() -> new RuntimeException("Plan not found: " + planId));

        if (!existingPlan.getUserId().equals(userId)) {
            throw new RuntimeException("Access denied");
        }

        // Collect completed items to keep
        List<PlanItem> completedItems = existingPlan.getItems().stream()
            .filter(i -> i.getParentItem() == null)
            .filter(i -> i.getStatus() == PlanItem.ItemStatus.DONE)
            .toList();

        // For remaining items, regenerate
        log.info("Re-planning plan {} for user {}, reason: {}", planId, userId, reason);

        // Mark old plan as COMPLETED
        existingPlan.setStatus(DailyPlan.PlanStatus.COMPLETED);
        planRepo.save(existingPlan);

        // Generate new version
        DailyPlanResponse newPlan = generatePlan(userId, existingPlan.getPlanDate());

        // TODO: merge completed items from old plan into new plan

        return newPlan;
    }

    // ========== Private helpers ==========

    @SuppressWarnings("unchecked")
    private DailyPlanResponse parsePlanFromAiResponse(
            UUID userId, LocalDate date, ScheduleTemplate template,
            String aiResponse, String promptUsed) {

        try {
            // Strip markdown code fences if present
            String json = aiResponse.trim();
            if (json.startsWith("```")) {
                json = json.replaceFirst("```(?:json)?\\s*", "").replaceFirst("\\s*```$", "");
            }

            List<Map<String, Object>> items = mapper.readValue(json, new TypeReference<>() {});

            // Create DailyPlan
            int nextVersion = planRepo.findLatestByUserIdAndDate(userId, date)
                .map(p -> p.getVersion() + 1).orElse(1);

            DailyPlan plan = DailyPlan.builder()
                .userId(userId)
                .planDate(date)
                .terminalGoal(template.getTerminalGoal())
                .terminalGoalTime(template.getTerminalGoalTime())
                .status(DailyPlan.PlanStatus.ACTIVE)
                .version(nextVersion)
                .aiGenerationContext(promptUsed.length() > 5000 ? promptUsed.substring(0, 5000) : promptUsed)
                .build();
            plan = planRepo.save(plan);

            // Create PlanItems
            int sortOrder = 0;
            for (Map<String, Object> itemMap : items) {
                PlanItem parentItem = createItemFromMap(itemMap, plan, null, sortOrder++);
                parentItem = itemRepo.save(parentItem);

                // Create children
                var children = (List<Map<String, Object>>) itemMap.get("children");
                if (children != null) {
                    int childSort = 0;
                    for (Map<String, Object> childMap : children) {
                        PlanItem child = createItemFromMap(childMap, plan, parentItem, childSort++);
                        itemRepo.save(child);
                    }
                }
            }

            log.info("Created daily plan v{} with {} top-level items for user {} on {}",
                nextVersion, items.size(), userId, date);

            // Reload to get full tree
            DailyPlan saved = planRepo.findById(plan.getId()).orElse(plan);
            return planService.toPlanResponse(saved);

        } catch (Exception e) {
            log.error("Failed to parse AI response for daily plan: {} — response: {}",
                e.getMessage(), aiResponse.substring(0, Math.min(500, aiResponse.length())));
            throw new RuntimeException("Failed to parse daily plan from AI response: " + e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private PlanItem createItemFromMap(Map<String, Object> map, DailyPlan plan,
                                       PlanItem parent, int sortOrder) {
        String title = (String) map.getOrDefault("title", "Untitled");
        String description = (String) map.get("description");
        String categoryStr = ((String) map.getOrDefault("category", "OTHER")).toUpperCase();
        String scheduledTimeStr = (String) map.get("scheduledTime");

        PlanItem.ItemCategory category;
        try {
            category = PlanItem.ItemCategory.valueOf(categoryStr);
        } catch (IllegalArgumentException e) {
            category = PlanItem.ItemCategory.OTHER;
        }

        LocalTime scheduledTime = null;
        if (scheduledTimeStr != null && !scheduledTimeStr.isBlank()) {
            try {
                scheduledTime = LocalTime.parse(scheduledTimeStr);
            } catch (Exception e) {
                log.warn("Could not parse scheduledTime '{}', skipping", scheduledTimeStr);
            }
        }

        Integer duration = map.get("estimatedDurationMinutes") instanceof Number n ? n.intValue() : null;
        Boolean isPrep = map.get("isPrep") instanceof Boolean b ? b : false;

        // Calculate reminder time from offset
        LocalTime reminderTime = null;
        if (scheduledTime != null && map.get("reminderOffsetMinutes") instanceof Number offset) {
            int offsetMins = offset.intValue();
            if (offsetMins > 0) {
                reminderTime = scheduledTime.minusMinutes(offsetMins);
            }
        }

        return PlanItem.builder()
            .dailyPlan(plan)
            .parentItem(parent)
            .title(title)
            .description(description)
            .category(category)
            .scheduledTime(scheduledTime)
            .estimatedDurationMinutes(duration)
            .isPrep(isPrep)
            .reminderTime(reminderTime)
            .sortOrder(sortOrder)
            .aiGenerated(true)
            .build();
    }

    private String getYesterdayStatus(UUID userId, LocalDate yesterday) {
        return planRepo.findLatestByUserIdAndDate(userId, yesterday)
            .map(plan -> {
                StringBuilder sb = new StringBuilder();
                plan.getItems().stream()
                    .filter(i -> i.getParentItem() == null)
                    .forEach(item -> sb.append(String.format("- %s [%s] %s\n",
                        item.getStatus(), item.getCategory(),
                        item.getTitle())));
                return sb.length() > 0 ? sb.toString() : "No plan yesterday.";
            })
            .orElse("No plan yesterday.");
    }

    private String getUserPreferences(UUID userId) {
        try {
            var memories = mem0Client.searchMemories(userId.toString(), "daily routine schedule preferences diet exercise");
//            if (memories != null && !memories.isEmpty()) {
//                return memories.stream()
//                    .limit(5)
//                    .map(m -> "- " + m.getOrDefault("memory", "").toString())
//                    .collect(java.util.stream.Collectors.joining("\n"));
//            }
        } catch (Exception e) {
            log.warn("Failed to fetch Mem0 memories for daily plan: {}", e.getMessage());
        }
        return "No preferences stored yet.";
    }

    private ScheduleTemplate buildDefaultTemplate(UUID userId) {
        return ScheduleTemplate.builder()
            .userId(userId)
            .dayType(ScheduleTemplate.DayType.WEEKDAY)
            .terminalGoal("Sleep")
            .terminalGoalTime(LocalTime.of(21, 0))
            .fixedBlocks("[{\"name\":\"Work\",\"start\":\"09:00\",\"end\":\"17:00\"}]")
            .mealTimes("[{\"name\":\"Lunch\",\"time\":\"12:00\"},{\"name\":\"Dinner\",\"time\":\"18:00\"}]")
            .recurringReminders("[{\"name\":\"Water\",\"intervalHours\":2}]")
            .build();
    }
}
