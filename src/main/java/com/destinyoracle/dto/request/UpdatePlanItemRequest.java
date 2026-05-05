package com.destinyoracle.dto.request;

import com.destinyoracle.domain.dailyplan.entity.PlanItem;
import jakarta.validation.constraints.NotNull;

import java.time.LocalTime;

public record UpdatePlanItemRequest(
    @NotNull PlanItem.ItemStatus status,
    LocalTime newScheduledTime    // for RESCHEDULED status
) {}
