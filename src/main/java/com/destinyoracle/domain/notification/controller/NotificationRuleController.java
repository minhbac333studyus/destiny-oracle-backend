package com.destinyoracle.domain.notification.controller;

import com.destinyoracle.domain.notification.entity.ActivityLog;
import com.destinyoracle.domain.notification.entity.NotificationRule;
import com.destinyoracle.domain.notification.repository.ActivityLogRepository;
import com.destinyoracle.domain.notification.repository.NotificationRuleRepository;
import com.destinyoracle.dto.request.CreateNotificationRuleRequest;
import com.destinyoracle.dto.response.NotificationRuleResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

@RestController
@RequestMapping("/api/v1/notification-rules")
@Tag(name = "Notification Rules", description = "User-configurable smart notification rules")
public class NotificationRuleController {

    private final NotificationRuleRepository ruleRepo;
    private final ActivityLogRepository activityRepo;

    public NotificationRuleController(NotificationRuleRepository ruleRepo,
                                      ActivityLogRepository activityRepo) {
        this.ruleRepo = ruleRepo;
        this.activityRepo = activityRepo;
    }

    // ── CRUD ──────────────────────────────────────────────────────────────

    @GetMapping
    @Operation(summary = "List all notification rules for user")
    public ResponseEntity<List<NotificationRuleResponse>> list(
        @RequestHeader("X-User-Id") UUID userId
    ) {
        return ResponseEntity.ok(
            ruleRepo.findByUserIdOrderByPriorityAsc(userId).stream()
                .map(this::toResponse).toList());
    }

    @PostMapping
    @Operation(summary = "Create a notification rule")
    public ResponseEntity<NotificationRuleResponse> create(
        @RequestHeader("X-User-Id") UUID userId,
        @Valid @RequestBody CreateNotificationRuleRequest req
    ) {
        NotificationRule rule = NotificationRule.builder()
            .userId(userId)
            .name(req.name())
            .description(req.description())
            .category(req.category())
            .outputType(req.outputType())
            .schedule(req.schedule())
            .intervalMinutes(req.intervalMinutes())
            .timeOfDay(req.timeOfDay())
            .dayOfWeek(req.dayOfWeek())
            .bedtime(req.bedtime())
            .wakeTime(req.wakeTime())
            .quietStart(req.quietStart())
            .quietEnd(req.quietEnd())
            .dailyQuota(req.dailyQuota())
            .cooldownMinutes(req.cooldownMinutes())
            .priority(req.priority())
            .quickAction(req.quickAction() != null ? req.quickAction() : false)
            .suppressDuringWorkout(req.suppressDuringWorkout() != null ? req.suppressDuringWorkout() : false)
            .build();

        return ResponseEntity.status(201).body(toResponse(ruleRepo.save(rule)));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a notification rule")
    @Transactional
    public ResponseEntity<NotificationRuleResponse> update(
        @RequestHeader("X-User-Id") UUID userId,
        @PathVariable UUID id,
        @Valid @RequestBody CreateNotificationRuleRequest req
    ) {
        NotificationRule rule = ruleRepo.findById(id)
            .orElseThrow(() -> new RuntimeException("Rule not found"));
        if (!rule.getUserId().equals(userId)) throw new RuntimeException("Access denied");

        rule.setName(req.name());
        rule.setDescription(req.description());
        rule.setCategory(req.category());
        rule.setOutputType(req.outputType());
        rule.setSchedule(req.schedule());
        rule.setIntervalMinutes(req.intervalMinutes());
        rule.setTimeOfDay(req.timeOfDay());
        rule.setDayOfWeek(req.dayOfWeek());
        rule.setBedtime(req.bedtime());
        rule.setWakeTime(req.wakeTime());
        rule.setQuietStart(req.quietStart());
        rule.setQuietEnd(req.quietEnd());
        rule.setDailyQuota(req.dailyQuota());
        rule.setCooldownMinutes(req.cooldownMinutes());
        rule.setPriority(req.priority());
        rule.setQuickAction(req.quickAction() != null ? req.quickAction() : false);
        rule.setSuppressDuringWorkout(req.suppressDuringWorkout() != null ? req.suppressDuringWorkout() : false);

        return ResponseEntity.ok(toResponse(ruleRepo.save(rule)));
    }

    @PatchMapping("/{id}/toggle")
    @Operation(summary = "Enable or disable a rule")
    @Transactional
    public ResponseEntity<NotificationRuleResponse> toggle(
        @RequestHeader("X-User-Id") UUID userId,
        @PathVariable UUID id
    ) {
        NotificationRule rule = ruleRepo.findById(id)
            .orElseThrow(() -> new RuntimeException("Rule not found"));
        if (!rule.getUserId().equals(userId)) throw new RuntimeException("Access denied");
        rule.setActive(!rule.getActive());
        return ResponseEntity.ok(toResponse(ruleRepo.save(rule)));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a notification rule")
    public ResponseEntity<Void> delete(
        @RequestHeader("X-User-Id") UUID userId,
        @PathVariable UUID id
    ) {
        NotificationRule rule = ruleRepo.findById(id)
            .orElseThrow(() -> new RuntimeException("Rule not found"));
        if (!rule.getUserId().equals(userId)) throw new RuntimeException("Access denied");
        ruleRepo.delete(rule);
        return ResponseEntity.noContent().build();
    }

    // ── Activity Log ─────────────────────────────────────────────────────

    @PostMapping("/activity-log")
    @Operation(summary = "Log a quick action (water intake, stand, etc.)")
    public ResponseEntity<Map<String, Object>> logActivity(
        @RequestHeader("X-User-Id") UUID userId,
        @RequestBody Map<String, String> body
    ) {
        String type = body.getOrDefault("activityType", "WATER").toUpperCase();
        String ruleId = body.get("ruleId");
        String remId = body.get("reminderId");

        ActivityLog log = ActivityLog.builder()
            .userId(userId)
            .activityType(type)
            .notificationRuleId(ruleId != null ? UUID.fromString(ruleId) : null)
            .reminderId(remId != null ? UUID.fromString(remId) : null)
            .build();

        activityRepo.save(log);

        // Return today's count for this type
        LocalDateTime startOfDay = LocalDate.now().atStartOfDay();
        int count = activityRepo.countByUserIdAndTypeSince(userId, type, startOfDay);

        return ResponseEntity.status(201).body(Map.of(
            "activityType", type,
            "todayCount", count
        ));
    }

    @GetMapping("/activity-log/today")
    @Operation(summary = "Get today's activity counts by type")
    public ResponseEntity<Map<String, Integer>> todayActivity(
        @RequestHeader("X-User-Id") UUID userId
    ) {
        LocalDateTime startOfDay = LocalDate.now().atStartOfDay();
        List<Object[]> rows = activityRepo.countByTypeSince(userId, startOfDay);

        Map<String, Integer> result = new LinkedHashMap<>();
        for (Object[] row : rows) {
            result.put((String) row[0], ((Number) row[1]).intValue());
        }
        return ResponseEntity.ok(result);
    }

    // ── Mapper ───────────────────────────────────────────────────────────

    private NotificationRuleResponse toResponse(NotificationRule r) {
        return new NotificationRuleResponse(
            r.getId(), r.getName(), r.getDescription(),
            r.getCategory(), r.getOutputType(), r.getSchedule(),
            r.getIntervalMinutes(), r.getTimeOfDay(), r.getDayOfWeek(),
            r.getBedtime(), r.getWakeTime(),
            r.getQuietStart(), r.getQuietEnd(),
            r.getDailyQuota(), r.getCooldownMinutes(),
            r.getPriority(), r.getQuickAction(), r.getSuppressDuringWorkout(),
            r.getActive(), r.getFiredToday(),
            r.getLastFiredAt(), r.getCreatedAt()
        );
    }
}
