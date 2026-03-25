package com.destinyoracle.dto.request;

import com.destinyoracle.domain.notification.entity.Reminder;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.UUID;

public record CreateReminderRequest(
    @NotBlank @Size(max = 255) String title,
    String body,
    @NotNull LocalDateTime scheduledAt,
    Reminder.RepeatType repeatType,
    String repeatCron,
    UUID taskId,
    UUID taskStepId
) {}
