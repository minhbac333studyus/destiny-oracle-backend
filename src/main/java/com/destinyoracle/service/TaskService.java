package com.destinyoracle.service;

import com.destinyoracle.dto.response.TaskResponse;

import java.util.List;
import java.util.UUID;

public interface TaskService {

    List<TaskResponse> getActiveTasks(UUID userId);

    List<TaskResponse> getCompletedTasks(UUID userId);

    TaskResponse getTask(UUID userId, UUID taskId);

    /**
     * Toggle a step — returns updated task with XP award info.
     */
    TaskResponse toggleStep(UUID userId, UUID stepId);

    void abandonTask(UUID userId, UUID taskId);
}
