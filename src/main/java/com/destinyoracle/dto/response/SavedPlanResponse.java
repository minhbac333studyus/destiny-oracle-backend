package com.destinyoracle.dto.response;

import com.destinyoracle.domain.plan.entity.SavedPlan;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record SavedPlanResponse(
    UUID id,
    String name,
    String slug,
    SavedPlan.PlanType type,
    String description,
    String content,
    Integer version,
    Boolean active,
    UUID parentPlanId,
    List<ScheduleResponse> schedules,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {
    public record ScheduleResponse(
        UUID id,
        String dayOfWeek,
        String timeOfDay,
        String repeatType,
        Boolean notifyBefore,
        Integer notifyMinutesBefore,
        Boolean active
    ) {}
}
