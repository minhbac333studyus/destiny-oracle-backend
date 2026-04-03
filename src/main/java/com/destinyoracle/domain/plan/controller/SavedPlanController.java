package com.destinyoracle.domain.plan.controller;

import com.destinyoracle.dto.request.CreateScheduleRequest;
import com.destinyoracle.dto.request.SavePlanRequest;
import com.destinyoracle.dto.request.UpdatePlanRequest;
import com.destinyoracle.dto.response.SavedPlanResponse;
import com.destinyoracle.domain.plan.service.SavedPlanService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/plans")
@Tag(name = "Saved Plans", description = "Persistent plans with versioning and scheduling")
public class SavedPlanController {

    private final SavedPlanService planService;

    public SavedPlanController(SavedPlanService planService) {
        this.planService = planService;
    }

    @PostMapping
    @Operation(summary = "Save a new plan")
    public ResponseEntity<SavedPlanResponse> savePlan(
        @RequestHeader("X-User-Id") UUID userId,
        @Valid @RequestBody SavePlanRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(planService.savePlan(userId, request));
    }

    @PutMapping("/{planId}")
    @Operation(summary = "Update a plan (overwrite or new version)")
    public ResponseEntity<SavedPlanResponse> updatePlan(
        @RequestHeader("X-User-Id") UUID userId,
        @PathVariable UUID planId,
        @Valid @RequestBody UpdatePlanRequest request
    ) {
        return ResponseEntity.ok(planService.updatePlan(userId, planId, request));
    }

    @GetMapping
    @Operation(summary = "List all active plans")
    public ResponseEntity<List<SavedPlanResponse>> listPlans(
        @RequestHeader("X-User-Id") UUID userId
    ) {
        return ResponseEntity.ok(planService.listPlans(userId));
    }

    @GetMapping("/{planId}")
    @Operation(summary = "Get a plan by ID")
    public ResponseEntity<SavedPlanResponse> getPlan(
        @RequestHeader("X-User-Id") UUID userId,
        @PathVariable UUID planId
    ) {
        return ResponseEntity.ok(planService.getPlan(userId, planId));
    }

    @GetMapping("/slug/{slug}")
    @Operation(summary = "Get a plan by slug (e.g., 'leg-day')")
    public ResponseEntity<SavedPlanResponse> getPlanBySlug(
        @RequestHeader("X-User-Id") UUID userId,
        @PathVariable String slug
    ) {
        return ResponseEntity.ok(planService.getPlanBySlug(userId, slug));
    }

    @GetMapping("/slug/{slug}/versions")
    @Operation(summary = "Get version history for a plan")
    public ResponseEntity<List<SavedPlanResponse>> getVersionHistory(
        @RequestHeader("X-User-Id") UUID userId,
        @PathVariable String slug
    ) {
        return ResponseEntity.ok(planService.getVersionHistory(userId, slug));
    }

    @DeleteMapping("/{planId}")
    @Operation(summary = "Soft-delete a plan")
    public ResponseEntity<Void> deletePlan(
        @RequestHeader("X-User-Id") UUID userId,
        @PathVariable UUID planId
    ) {
        planService.deletePlan(userId, planId);
        return ResponseEntity.noContent().build();
    }

    // ── Schedule endpoints ────────────────────────────

    @PostMapping("/{planId}/schedules")
    @Operation(summary = "Add a schedule to a plan")
    public ResponseEntity<SavedPlanResponse.ScheduleResponse> addSchedule(
        @RequestHeader("X-User-Id") UUID userId,
        @PathVariable UUID planId,
        @Valid @RequestBody CreateScheduleRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(planService.addSchedule(userId, planId, request));
    }

    @GetMapping("/{planId}/schedules")
    @Operation(summary = "List schedules for a plan")
    public ResponseEntity<List<SavedPlanResponse.ScheduleResponse>> getSchedules(
        @RequestHeader("X-User-Id") UUID userId,
        @PathVariable UUID planId
    ) {
        return ResponseEntity.ok(planService.getSchedules(userId, planId));
    }

    @DeleteMapping("/schedules/{scheduleId}")
    @Operation(summary = "Remove a schedule")
    public ResponseEntity<Void> removeSchedule(
        @RequestHeader("X-User-Id") UUID userId,
        @PathVariable UUID scheduleId
    ) {
        planService.removeSchedule(userId, scheduleId);
        return ResponseEntity.noContent().build();
    }
}
