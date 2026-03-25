package com.destinyoracle.service.impl;

import com.destinyoracle.dto.request.CreateGoalRequest;
import com.destinyoracle.dto.request.MilestoneStatusRequest;
import com.destinyoracle.dto.response.GoalResponse;
import com.destinyoracle.dto.response.MilestoneResponse;
import com.destinyoracle.entity.Goal;
import com.destinyoracle.entity.Milestone;
import com.destinyoracle.exception.ResourceNotFoundException;
import com.destinyoracle.repository.GoalRepository;
import com.destinyoracle.repository.MilestoneRepository;
import com.destinyoracle.service.GoalService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class GoalServiceImpl implements GoalService {

    private final GoalRepository goalRepository;
    private final MilestoneRepository milestoneRepository;

    @Override
    @Transactional(readOnly = true)
    public List<GoalResponse> getGoals(UUID userId) {
        List<Goal> goals = goalRepository.findAllByUserIdWithMilestones(userId);
        return goals.stream()
                .map(this::toGoalResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public GoalResponse createGoal(UUID userId, CreateGoalRequest request) {
        Goal goal = Goal.builder()
                .userId(userId)
                .aspectKey(request.getAspectKey())
                .aspectLabel(request.getAspectLabel())
                .title(request.getTitle())
                .status("active")
                .milestones(new ArrayList<>())
                .build();

        if (request.getMilestones() != null) {
            for (String milestoneText : request.getMilestones()) {
                if (milestoneText != null && !milestoneText.isBlank()) {
                    Milestone milestone = Milestone.builder()
                            .goal(goal)
                            .text(milestoneText.trim())
                            .status("pending")
                            .build();
                    goal.getMilestones().add(milestone);
                }
            }
        }

        Goal saved = goalRepository.save(goal);
        log.info("Created goal {} for user {}", saved.getId(), userId);
        return toGoalResponse(saved);
    }

    @Override
    @Transactional
    public MilestoneResponse updateMilestone(UUID userId, UUID goalId, UUID milestoneId,
                                              MilestoneStatusRequest request) {
        Goal goal = goalRepository.findByIdAndUserIdWithMilestones(goalId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Goal", "id", goalId));

        Milestone milestone = milestoneRepository.findByIdAndGoalId(milestoneId, goalId)
                .orElseThrow(() -> new ResourceNotFoundException("Milestone", "id", milestoneId));

        milestone.setStatus(request.getStatus());
        if ("achieved".equalsIgnoreCase(request.getStatus())) {
            milestone.setAchievedAt(Instant.now());
        } else {
            milestone.setAchievedAt(null);
        }

        Milestone saved = milestoneRepository.save(milestone);
        log.info("Updated milestone {} to status {} for goal {}", milestoneId, request.getStatus(), goalId);
        return toMilestoneResponse(saved);
    }

    // ── Mappers ───────────────────────────────────────────────────────────────

    private GoalResponse toGoalResponse(Goal goal) {
        List<MilestoneResponse> milestones = goal.getMilestones().stream()
                .map(this::toMilestoneResponse)
                .collect(Collectors.toList());

        return GoalResponse.builder()
                .id(goal.getId())
                .aspectKey(goal.getAspectKey())
                .aspectLabel(goal.getAspectLabel())
                .title(goal.getTitle())
                .status(goal.getStatus())
                .milestones(milestones)
                .createdAt(goal.getCreatedAt())
                .build();
    }

    private MilestoneResponse toMilestoneResponse(Milestone milestone) {
        return MilestoneResponse.builder()
                .id(milestone.getId())
                .text(milestone.getText())
                .status(milestone.getStatus())
                .achievedAt(milestone.getAchievedAt())
                .build();
    }
}
