package com.destinyoracle.dto.request;

import com.destinyoracle.domain.notification.entity.NotificationRule;
import com.destinyoracle.domain.task.entity.Task;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.DayOfWeek;
import java.time.LocalTime;

public record CreateNotificationRuleRequest(
    @NotBlank @Size(max = 255) String name,
    String description,
    @NotNull Task.TaskCategory category,
    @NotNull NotificationRule.OutputType outputType,
    @NotNull NotificationRule.ScheduleType schedule,
    Integer intervalMinutes,
    LocalTime timeOfDay,
    DayOfWeek dayOfWeek,
    LocalTime bedtime,
    LocalTime wakeTime,
    LocalTime quietStart,
    LocalTime quietEnd,
    Integer dailyQuota,
    Integer cooldownMinutes,
    @NotNull @Min(1) @Max(5) Integer priority,
    Boolean quickAction,
    Boolean suppressDuringWorkout
) {}
