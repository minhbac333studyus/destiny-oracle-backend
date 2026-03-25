package com.destinyoracle.dto.request;

import jakarta.validation.constraints.NotNull;

import java.time.DayOfWeek;
import java.time.LocalTime;

public record CreateScheduleRequest(
    @NotNull DayOfWeek dayOfWeek,
    LocalTime timeOfDay,
    String repeatType,              // WEEKLY, DAILY, ONE_TIME, CUSTOM
    String repeatCron,              // For CUSTOM
    Boolean notifyBefore,
    Integer notifyMinutesBefore
) {}
