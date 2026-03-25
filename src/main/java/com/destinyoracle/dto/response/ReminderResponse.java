package com.destinyoracle.dto.response;

import com.destinyoracle.domain.notification.entity.Reminder;

import java.time.LocalDateTime;
import java.util.UUID;

public record ReminderResponse(
    UUID id,
    String title,
    String body,
    LocalDateTime scheduledAt,
    Reminder.RepeatType repeatType,
    Boolean notificationSent,
    Boolean completed,
    LocalDateTime snoozedUntil,
    UUID taskId,
    UUID taskStepId,
    LocalDateTime createdAt
) {}
