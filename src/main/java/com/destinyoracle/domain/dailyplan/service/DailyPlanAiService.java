package com.destinyoracle.domain.dailyplan.service;

import com.destinyoracle.dto.response.DailyPlanResponse;

import java.time.LocalDate;
import java.util.UUID;

public interface DailyPlanAiService {

    /**
     * Generate a daily plan using backward planning from the terminal goal.
     * Uses the user's schedule template + yesterday's plan status + Mem0 memories.
     */
    DailyPlanResponse generatePlan(UUID userId, LocalDate date);

    /**
     * Re-plan remaining items after a skip/reschedule.
     * Keeps completed items, regenerates the rest.
     */
    DailyPlanResponse replan(UUID userId, UUID planId, String reason);
}
