package com.destinyoracle.dto.request;

import com.destinyoracle.domain.dailyplan.entity.PlanItem;
import jakarta.validation.constraints.NotBlank;

import java.time.LocalTime;
import java.util.UUID;

public record AddPlanItemRequest(
    @NotBlank String title,
    String description,
    PlanItem.ItemCategory category,
    LocalTime scheduledTime,
    Integer estimatedDurationMinutes,
    UUID parentItemId              // null = top-level item
) {}
