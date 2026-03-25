package com.destinyoracle.controller;

import com.destinyoracle.config.AppProperties;
import com.destinyoracle.dto.request.CreateGoalRequest;
import com.destinyoracle.dto.request.MilestoneStatusRequest;
import com.destinyoracle.dto.response.ApiResponse;
import com.destinyoracle.dto.response.GoalResponse;
import com.destinyoracle.dto.response.MilestoneResponse;
import com.destinyoracle.service.GoalService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/goals")
@RequiredArgsConstructor
public class GoalController {

    private final GoalService goalService;
    private final AppProperties appProperties;

    @GetMapping
    public ResponseEntity<ApiResponse<List<GoalResponse>>> getGoals(
            @RequestHeader(value = "X-User-Id", required = false) UUID userIdHeader) {
        UUID userId = resolveUserId(userIdHeader);
        List<GoalResponse> goals = goalService.getGoals(userId);
        return ResponseEntity.ok(ApiResponse.success(goals));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<GoalResponse>> createGoal(
            @RequestHeader(value = "X-User-Id", required = false) UUID userIdHeader,
            @Valid @RequestBody CreateGoalRequest request) {
        UUID userId = resolveUserId(userIdHeader);
        GoalResponse goal = goalService.createGoal(userId, request);
        return ResponseEntity.ok(ApiResponse.success("Goal created", goal));
    }

    @PutMapping("/{goalId}/milestones/{milestoneId}")
    public ResponseEntity<ApiResponse<MilestoneResponse>> updateMilestone(
            @RequestHeader(value = "X-User-Id", required = false) UUID userIdHeader,
            @PathVariable UUID goalId,
            @PathVariable UUID milestoneId,
            @Valid @RequestBody MilestoneStatusRequest request) {
        UUID userId = resolveUserId(userIdHeader);
        MilestoneResponse milestone = goalService.updateMilestone(userId, goalId, milestoneId, request);
        return ResponseEntity.ok(ApiResponse.success("Milestone updated", milestone));
    }

    private UUID resolveUserId(UUID header) {
        return header != null ? header : appProperties.getDefaultUserId();
    }
}
