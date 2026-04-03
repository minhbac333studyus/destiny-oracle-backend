package com.destinyoracle.domain.task.service;

import com.destinyoracle.domain.task.entity.Task;
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

    /**
     * Create a task with steps — used by SmartNotificationScheduler and API.
     */
    TaskResponse createTask(UUID userId, String name, Task.TaskCategory category,
                            java.util.List<StepInput> steps);

    /** Input for creating a task step */
    record StepInput(String title, String description, String payload) {}
}
