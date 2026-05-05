package com.destinyoracle.domain.dailyplan.service.impl;

import com.destinyoracle.domain.dailyplan.entity.*;
import com.destinyoracle.domain.dailyplan.repository.*;
import com.destinyoracle.domain.dailyplan.service.DailyPlanService;
import com.destinyoracle.dto.request.AddPlanItemRequest;
import com.destinyoracle.dto.request.SaveScheduleTemplateRequest;
import com.destinyoracle.dto.request.UpdatePlanItemRequest;
import com.destinyoracle.dto.response.DailyPlanResponse;
import com.destinyoracle.dto.response.DailyPlanResponse.PlanItemResponse;
import com.destinyoracle.dto.response.ScheduleTemplateResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class DailyPlanServiceImpl implements DailyPlanService {

    private final ScheduleTemplateRepository templateRepo;
    private final DailyPlanRepository planRepo;
    private final PlanItemRepository itemRepo;
    private final PlanHistoryRepository historyRepo;

    // ========== Schedule Templates ==========

    @Override
    @Transactional
    public ScheduleTemplateResponse saveTemplate(UUID userId, SaveScheduleTemplateRequest req) {
        var existing = templateRepo.findByUserIdAndDayType(userId, req.dayType());

        ScheduleTemplate template;
        if (existing.isPresent()) {
            template = existing.get();
            template.setTerminalGoal(req.terminalGoal());
            template.setTerminalGoalTime(req.terminalGoalTime());
            template.setFixedBlocks(req.fixedBlocks());
            template.setMealTimes(req.mealTimes());
            template.setRecurringReminders(req.recurringReminders());
        } else {
            template = ScheduleTemplate.builder()
                .userId(userId)
                .dayType(req.dayType())
                .terminalGoal(req.terminalGoal())
                .terminalGoalTime(req.terminalGoalTime())
                .fixedBlocks(req.fixedBlocks())
                .mealTimes(req.mealTimes())
                .recurringReminders(req.recurringReminders())
                .build();
        }

        template = templateRepo.save(template);
        log.info("Saved schedule template for user {} dayType={}", userId, req.dayType());
        return toTemplateResponse(template);
    }

    @Override
    public List<ScheduleTemplateResponse> getTemplates(UUID userId) {
        return templateRepo.findByUserId(userId).stream()
            .map(this::toTemplateResponse).toList();
    }

    @Override
    public ScheduleTemplateResponse getTemplate(UUID userId, ScheduleTemplate.DayType dayType) {
        return templateRepo.findByUserIdAndDayType(userId, dayType)
            .map(this::toTemplateResponse)
            .orElse(null);
    }

    // ========== Daily Plans ==========

    @Override
    public DailyPlanResponse getTodayPlan(UUID userId) {
        return getPlanByDate(userId, LocalDate.now());
    }

    @Override
    public DailyPlanResponse getPlanByDate(UUID userId, LocalDate date) {
        return planRepo.findActivePlan(userId, date)
            .or(() -> planRepo.findLatestByUserIdAndDate(userId, date))
            .map(this::toPlanResponse)
            .orElse(null);
    }

    // ========== Plan Items ==========

    @Override
    @Transactional
    public PlanItemResponse updateItemStatus(UUID userId, UUID itemId, UpdatePlanItemRequest req) {
        PlanItem item = itemRepo.findById(itemId)
            .orElseThrow(() -> new RuntimeException("Plan item not found: " + itemId));

        // Access control via plan's userId
        if (!item.getDailyPlan().getUserId().equals(userId)) {
            throw new RuntimeException("Access denied to plan item: " + itemId);
        }

        LocalTime originalTime = item.getScheduledTime();
        item.setStatus(req.status());
        item.setUserModified(true);

        if (req.status() == PlanItem.ItemStatus.RESCHEDULED && req.newScheduledTime() != null) {
            item.setScheduledTime(req.newScheduledTime());
        }

        // Dismiss reminder when item is done/skipped
        if (req.status() == PlanItem.ItemStatus.DONE || req.status() == PlanItem.ItemStatus.SKIPPED) {
            item.setReminderDismissed(true);
        }

        itemRepo.save(item);

        // Log history
        PlanHistory.HistoryAction action = switch (req.status()) {
            case DONE -> PlanHistory.HistoryAction.COMPLETED;
            case SKIPPED -> PlanHistory.HistoryAction.SKIPPED;
            case RESCHEDULED -> PlanHistory.HistoryAction.RESCHEDULED;
            default -> null;
        };
        if (action != null) {
            historyRepo.save(PlanHistory.builder()
                .planItemId(itemId)
                .action(action)
                .originalTime(originalTime)
                .newTime(req.newScheduledTime())
                .build());
        }

        log.info("Updated plan item {} to {} for user {}", itemId, req.status(), userId);
        return toItemResponse(item);
    }

    @Override
    @Transactional
    public PlanItemResponse addItem(UUID userId, UUID planId, AddPlanItemRequest req) {
        DailyPlan plan = planRepo.findById(planId)
            .orElseThrow(() -> new RuntimeException("Plan not found: " + planId));
        if (!plan.getUserId().equals(userId)) {
            throw new RuntimeException("Access denied to plan: " + planId);
        }

        PlanItem parent = null;
        int sortOrder = 0;
        if (req.parentItemId() != null) {
            parent = itemRepo.findById(req.parentItemId()).orElse(null);
            if (parent != null) {
                sortOrder = parent.getChildren().size();
            }
        } else {
            sortOrder = plan.getItems().stream()
                .filter(i -> i.getParentItem() == null)
                .mapToInt(PlanItem::getSortOrder).max().orElse(-1) + 1;
        }

        PlanItem item = PlanItem.builder()
            .dailyPlan(plan)
            .parentItem(parent)
            .title(req.title())
            .description(req.description())
            .category(req.category() != null ? req.category() : PlanItem.ItemCategory.OTHER)
            .scheduledTime(req.scheduledTime())
            .estimatedDurationMinutes(req.estimatedDurationMinutes())
            .sortOrder(sortOrder)
            .aiGenerated(false)
            .userModified(false)
            .build();

        item = itemRepo.save(item);

        // Log history
        historyRepo.save(PlanHistory.builder()
            .planItemId(item.getId())
            .action(PlanHistory.HistoryAction.ADDED)
            .originalTime(req.scheduledTime())
            .build());

        log.info("Added plan item '{}' to plan {} for user {}", req.title(), planId, userId);
        return toItemResponse(item);
    }

    // ========== From chat ACTION block ==========

    @Override
    @Transactional
    @SuppressWarnings("unchecked")
    public DailyPlanResponse savePlanFromActionBlock(UUID userId, LocalDate date, List<Map<String, Object>> items) {
        int nextVersion = planRepo.findLatestByUserIdAndDate(userId, date)
            .map(p -> p.getVersion() + 1).orElse(1);

        DailyPlan plan = DailyPlan.builder()
            .userId(userId)
            .planDate(date)
            .status(DailyPlan.PlanStatus.ACTIVE)
            .version(nextVersion)
            .build();
        plan = planRepo.save(plan);

        int sortOrder = 0;
        for (Map<String, Object> itemMap : items) {
            PlanItem parent = buildItemFromMap(itemMap, plan, null, sortOrder++);
            parent = itemRepo.save(parent);

            var children = (List<Map<String, Object>>) itemMap.get("children");
            if (children != null) {
                int childSort = 0;
                for (Map<String, Object> childMap : children) {
                    PlanItem child = buildItemFromMap(childMap, plan, parent, childSort++);
                    itemRepo.save(child);
                }
            }
        }

        log.info("Saved daily plan v{} with {} items from chat ACTION for user {} on {}", nextVersion, items.size(), userId, date);

        DailyPlan saved = planRepo.findById(plan.getId()).orElse(plan);
        return toPlanResponse(saved);
    }

    private PlanItem buildItemFromMap(Map<String, Object> map, DailyPlan plan, PlanItem parent, int sortOrder) {
        String title = (String) map.getOrDefault("title", "Untitled");
        String categoryStr = ((String) map.getOrDefault("category", "OTHER")).toUpperCase();
        String scheduledTimeStr = (String) map.get("scheduledTime");

        PlanItem.ItemCategory category;
        try { category = PlanItem.ItemCategory.valueOf(categoryStr); }
        catch (IllegalArgumentException e) { category = PlanItem.ItemCategory.OTHER; }

        LocalTime scheduledTime = null;
        if (scheduledTimeStr != null && !scheduledTimeStr.isBlank()) {
            try { scheduledTime = LocalTime.parse(scheduledTimeStr); }
            catch (Exception e) { /* skip */ }
        }

        Integer duration = map.get("estimatedDurationMinutes") instanceof Number n ? n.intValue() : null;

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
            .category(category)
            .scheduledTime(scheduledTime)
            .estimatedDurationMinutes(duration)
            .reminderTime(reminderTime)
            .sortOrder(sortOrder)
            .aiGenerated(true)
            .build();
    }

    @Override
    @Transactional
    public void deletePlan(UUID userId, UUID planId) {
        DailyPlan plan = planRepo.findById(planId)
            .orElseThrow(() -> new RuntimeException("Plan not found: " + planId));
        if (!plan.getUserId().equals(userId)) {
            throw new RuntimeException("Access denied to plan: " + planId);
        }
        planRepo.delete(plan);
        log.info("Deleted plan {} for user {}", planId, userId);
    }

    // ========== Mappers ==========

    private ScheduleTemplateResponse toTemplateResponse(ScheduleTemplate t) {
        return new ScheduleTemplateResponse(
            t.getId(), t.getDayType(), t.getTerminalGoal(), t.getTerminalGoalTime(),
            t.getFixedBlocks(), t.getMealTimes(), t.getRecurringReminders()
        );
    }

    public DailyPlanResponse toPlanResponse(DailyPlan p) {
        List<PlanItemResponse> topLevel = p.getItems().stream()
            .filter(i -> i.getParentItem() == null)
            .sorted((a, b) -> Integer.compare(a.getSortOrder(), b.getSortOrder()))
            .map(this::toItemResponse)
            .toList();

        return new DailyPlanResponse(
            p.getId(), p.getPlanDate(), p.getTerminalGoal(), p.getTerminalGoalTime(),
            p.getStatus(), p.getVersion(), topLevel, p.getCreatedAt()
        );
    }

    private PlanItemResponse toItemResponse(PlanItem item) {
        List<PlanItemResponse> childResponses = item.getChildren().stream()
            .sorted((a, b) -> Integer.compare(a.getSortOrder(), b.getSortOrder()))
            .map(this::toItemResponse)
            .toList();

        return new PlanItemResponse(
            item.getId(), item.getTitle(), item.getDescription(),
            item.getCategory(), item.getScheduledTime(),
            item.getEstimatedDurationMinutes(), item.getStatus(),
            item.getIsPrep(), item.getReminderTime(), item.getReminderDismissed(),
            item.getSortOrder(), item.getAiGenerated(), item.getUserModified(),
            childResponses
        );
    }
}
