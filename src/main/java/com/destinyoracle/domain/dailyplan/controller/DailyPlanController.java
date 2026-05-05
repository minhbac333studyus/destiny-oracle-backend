package com.destinyoracle.domain.dailyplan.controller;

import com.destinyoracle.domain.dailyplan.entity.ScheduleTemplate;
import com.destinyoracle.domain.dailyplan.service.DailyPlanAiService;
import com.destinyoracle.domain.dailyplan.service.DailyPlanService;
import com.destinyoracle.dto.request.AddPlanItemRequest;
import com.destinyoracle.dto.request.GenerateDailyPlanRequest;
import com.destinyoracle.dto.request.SaveScheduleTemplateRequest;
import com.destinyoracle.dto.request.UpdatePlanItemRequest;
import com.destinyoracle.dto.response.DailyPlanResponse;
import com.destinyoracle.dto.response.DailyPlanResponse.PlanItemResponse;
import com.destinyoracle.dto.response.ScheduleTemplateResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/daily-plans")
@RequiredArgsConstructor
@Tag(name = "Daily Plans", description = "AI-powered backward daily planning with tree structure")
public class DailyPlanController {

    private final DailyPlanService planService;
    private final DailyPlanAiService aiService;

    // ========== Schedule Templates ==========

    @PostMapping("/templates")
    @Operation(summary = "Save or update a schedule template")
    public ResponseEntity<ScheduleTemplateResponse> saveTemplate(
            @RequestHeader("X-User-Id") UUID userId,
            @Valid @RequestBody SaveScheduleTemplateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(planService.saveTemplate(userId, request));
    }

    @GetMapping("/templates")
    @Operation(summary = "Get all schedule templates for user")
    public List<ScheduleTemplateResponse> getTemplates(@RequestHeader("X-User-Id") UUID userId) {
        return planService.getTemplates(userId);
    }

    @GetMapping("/templates/{dayType}")
    @Operation(summary = "Get schedule template by day type")
    public ResponseEntity<ScheduleTemplateResponse> getTemplate(
            @RequestHeader("X-User-Id") UUID userId,
            @PathVariable ScheduleTemplate.DayType dayType) {
        var result = planService.getTemplate(userId, dayType);
        return result != null ? ResponseEntity.ok(result) : ResponseEntity.notFound().build();
    }

    // ========== Daily Plans ==========

    @PostMapping("/generate")
    @Operation(summary = "Generate AI daily plan (backward planning)")
    public ResponseEntity<DailyPlanResponse> generatePlan(
            @RequestHeader("X-User-Id") UUID userId,
            @RequestBody(required = false) GenerateDailyPlanRequest request) {
        LocalDate date = (request != null && request.date() != null) ? request.date() : LocalDate.now();
        var plan = aiService.generatePlan(userId, date);
        return ResponseEntity.status(HttpStatus.CREATED).body(plan);
    }

    @PostMapping("/{planId}/replan")
    @Operation(summary = "Re-plan remaining items after skip/reschedule")
    public ResponseEntity<DailyPlanResponse> replan(
            @RequestHeader("X-User-Id") UUID userId,
            @PathVariable UUID planId,
            @RequestBody(required = false) Map<String, String> body) {
        String reason = body != null ? body.getOrDefault("reason", "") : "";
        var plan = aiService.replan(userId, planId, reason);
        return ResponseEntity.ok(plan);
    }

    @GetMapping("/today")
    @Operation(summary = "Get today's active plan with tree items")
    public ResponseEntity<DailyPlanResponse> getTodayPlan(@RequestHeader("X-User-Id") UUID userId) {
        var plan = planService.getTodayPlan(userId);
        return plan != null ? ResponseEntity.ok(plan) : ResponseEntity.noContent().build();
    }

    @GetMapping("/{date}")
    @Operation(summary = "Get plan by date")
    public ResponseEntity<DailyPlanResponse> getPlanByDate(
            @RequestHeader("X-User-Id") UUID userId,
            @PathVariable LocalDate date) {
        var plan = planService.getPlanByDate(userId, date);
        return plan != null ? ResponseEntity.ok(plan) : ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{planId}")
    @Operation(summary = "Delete a daily plan")
    public ResponseEntity<Void> deletePlan(
            @RequestHeader("X-User-Id") UUID userId,
            @PathVariable UUID planId) {
        planService.deletePlan(userId, planId);
        return ResponseEntity.noContent().build();
    }

    // ========== Plan Items ==========

    @PatchMapping("/items/{itemId}")
    @Operation(summary = "Update plan item status (DONE, SKIPPED, RESCHEDULED)")
    public PlanItemResponse updateItemStatus(
            @RequestHeader("X-User-Id") UUID userId,
            @PathVariable UUID itemId,
            @Valid @RequestBody UpdatePlanItemRequest request) {
        return planService.updateItemStatus(userId, itemId, request);
    }

    @PostMapping("/{planId}/items")
    @Operation(summary = "Manually add an item to a daily plan")
    public ResponseEntity<PlanItemResponse> addItem(
            @RequestHeader("X-User-Id") UUID userId,
            @PathVariable UUID planId,
            @Valid @RequestBody AddPlanItemRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(planService.addItem(userId, planId, request));
    }
}
