package com.destinyoracle.domain.dailyplan.service;

import com.destinyoracle.domain.dailyplan.entity.ScheduleTemplate;
import com.destinyoracle.dto.request.AddPlanItemRequest;
import com.destinyoracle.dto.request.SaveScheduleTemplateRequest;
import com.destinyoracle.dto.request.UpdatePlanItemRequest;
import com.destinyoracle.dto.response.DailyPlanResponse;
import com.destinyoracle.dto.response.DailyPlanResponse.PlanItemResponse;
import com.destinyoracle.dto.response.ScheduleTemplateResponse;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface DailyPlanService {

    // ---- Schedule Templates ----
    ScheduleTemplateResponse saveTemplate(UUID userId, SaveScheduleTemplateRequest request);
    List<ScheduleTemplateResponse> getTemplates(UUID userId);
    ScheduleTemplateResponse getTemplate(UUID userId, ScheduleTemplate.DayType dayType);

    // ---- Daily Plans ----
    DailyPlanResponse getTodayPlan(UUID userId);
    DailyPlanResponse getPlanByDate(UUID userId, LocalDate date);

    // ---- Plan Items ----
    PlanItemResponse updateItemStatus(UUID userId, UUID itemId, UpdatePlanItemRequest request);
    PlanItemResponse addItem(UUID userId, UUID planId, AddPlanItemRequest request);

    // ---- From chat ACTION block ----
    DailyPlanResponse savePlanFromActionBlock(UUID userId, LocalDate date, List<Map<String, Object>> items);

    // ---- Plan lifecycle ----
    void deletePlan(UUID userId, UUID planId);
}
