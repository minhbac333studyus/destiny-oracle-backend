package com.destinyoracle.dto.response;

import com.destinyoracle.domain.dailyplan.entity.ScheduleTemplate;

import java.time.LocalTime;
import java.util.UUID;

public record ScheduleTemplateResponse(
    UUID id,
    ScheduleTemplate.DayType dayType,
    String terminalGoal,
    LocalTime terminalGoalTime,
    String fixedBlocks,
    String mealTimes,
    String recurringReminders
) {}
