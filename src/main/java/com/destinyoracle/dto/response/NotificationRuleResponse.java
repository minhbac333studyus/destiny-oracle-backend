package com.destinyoracle.dto.response;

import com.destinyoracle.domain.notification.entity.NotificationRule;
import com.destinyoracle.domain.task.entity.Task;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.UUID;

public record NotificationRuleResponse(
    UUID id,
    String name,
    String description,
    Task.TaskCategory category,
    NotificationRule.OutputType outputType,
    NotificationRule.ScheduleType schedule,
    Integer intervalMinutes,
    LocalTime timeOfDay,
    DayOfWeek dayOfWeek,
    LocalTime bedtime,
    LocalTime wakeTime,
    LocalTime quietStart,
    LocalTime quietEnd,
    Integer dailyQuota,
    Integer cooldownMinutes,
    Integer priority,
    Boolean quickAction,
    Boolean suppressDuringWorkout,
    Boolean active,
    Integer firedToday,
    LocalDateTime lastFiredAt,
    LocalDateTime createdAt
) {}
