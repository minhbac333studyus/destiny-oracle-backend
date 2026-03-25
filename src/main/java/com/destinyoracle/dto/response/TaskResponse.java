package com.destinyoracle.dto.response;

import com.destinyoracle.domain.task.entity.Task;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record TaskResponse(
    UUID id,
    String name,
    Task.TaskCategory category,
    Task.TaskStatus status,
    Integer totalSteps,
    Integer completedSteps,
    Integer xpPerStep,
    UUID cardId,
    UUID savedPlanId,
    List<StepResponse> steps,
    LocalDateTime createdAt
) {
    public record StepResponse(
        UUID id,
        Integer stepNumber,
        String title,
        String description,
        String payload,
        Boolean completed,
        LocalDateTime completedAt,
        String scheduledDate
    ) {}
}
