package com.destinyoracle.service;

import com.destinyoracle.dto.request.CreateGoalRequest;
import com.destinyoracle.dto.request.MilestoneStatusRequest;
import com.destinyoracle.dto.response.GoalResponse;
import com.destinyoracle.dto.response.MilestoneResponse;

import java.util.List;
import java.util.UUID;

public interface GoalService {

    List<GoalResponse> getGoals(UUID userId);

    GoalResponse createGoal(UUID userId, CreateGoalRequest request);

    MilestoneResponse updateMilestone(UUID userId, UUID goalId, UUID milestoneId,
                                      MilestoneStatusRequest request);
}
