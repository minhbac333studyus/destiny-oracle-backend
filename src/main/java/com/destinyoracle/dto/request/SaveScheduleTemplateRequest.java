package com.destinyoracle.dto.request;

import com.destinyoracle.domain.dailyplan.entity.ScheduleTemplate;
import jakarta.validation.constraints.NotNull;

import java.time.LocalTime;

public record SaveScheduleTemplateRequest(
    @NotNull ScheduleTemplate.DayType dayType,
    String terminalGoal,
    LocalTime terminalGoalTime,
    String fixedBlocks,          // JSON string
    String mealTimes,            // JSON string
    String recurringReminders    // JSON string
) {}
