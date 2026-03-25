package com.destinyoracle.service;

import com.destinyoracle.dto.request.CreateScheduleRequest;
import com.destinyoracle.dto.request.SavePlanRequest;
import com.destinyoracle.dto.request.UpdatePlanRequest;
import com.destinyoracle.dto.response.SavedPlanResponse;

import java.util.List;
import java.util.UUID;

public interface SavedPlanService {

    SavedPlanResponse savePlan(UUID userId, SavePlanRequest request);

    SavedPlanResponse updatePlan(UUID userId, UUID planId, UpdatePlanRequest request);

    List<SavedPlanResponse> listPlans(UUID userId);

    SavedPlanResponse getPlan(UUID userId, UUID planId);

    SavedPlanResponse getPlanBySlug(UUID userId, String slug);

    List<SavedPlanResponse> getVersionHistory(UUID userId, String slug);

    void deletePlan(UUID userId, UUID planId);

    SavedPlanResponse.ScheduleResponse addSchedule(UUID userId, UUID planId, CreateScheduleRequest request);

    void removeSchedule(UUID userId, UUID scheduleId);

    List<SavedPlanResponse.ScheduleResponse> getSchedules(UUID userId, UUID planId);
}
