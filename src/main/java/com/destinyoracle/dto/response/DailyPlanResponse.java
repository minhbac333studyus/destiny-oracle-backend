package com.destinyoracle.dto.response;

import com.destinyoracle.domain.dailyplan.entity.DailyPlan;
import com.destinyoracle.domain.dailyplan.entity.PlanItem;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

public record DailyPlanResponse(
    UUID id,
    LocalDate planDate,
    String terminalGoal,
    LocalTime terminalGoalTime,
    DailyPlan.PlanStatus status,
    Integer version,
    List<PlanItemResponse> items,
    LocalDateTime createdAt
) {

    public record PlanItemResponse(
        UUID id,
        String title,
        String description,
        PlanItem.ItemCategory category,
        LocalTime scheduledTime,
        Integer estimatedDurationMinutes,
        PlanItem.ItemStatus status,
        Boolean isPrep,
        LocalTime reminderTime,
        Boolean reminderDismissed,
        Integer sortOrder,
        Boolean aiGenerated,
        Boolean userModified,
        List<PlanItemResponse> children    // recursive tree
    ) {}
}
