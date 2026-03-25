package com.destinyoracle.service.impl;

import com.destinyoracle.domain.task.entity.Task;
import com.destinyoracle.domain.task.entity.TaskStep;
import com.destinyoracle.domain.task.repository.TaskRepository;
import com.destinyoracle.domain.task.repository.TaskStepRepository;
import com.destinyoracle.dto.response.TaskResponse;
import com.destinyoracle.service.TaskService;
import com.destinyoracle.shared.event.TaskCompletedEvent;
import com.destinyoracle.shared.event.TaskStepCompletedEvent;
import com.destinyoracle.shared.xp.XpCalculator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class TaskServiceImpl implements TaskService {

    private static final Logger log = LoggerFactory.getLogger(TaskServiceImpl.class);

    private final TaskRepository taskRepo;
    private final TaskStepRepository stepRepo;
    private final XpCalculator xpCalculator;
    private final ApplicationEventPublisher eventPublisher;

    public TaskServiceImpl(
        TaskRepository taskRepo,
        TaskStepRepository stepRepo,
        XpCalculator xpCalculator,
        ApplicationEventPublisher eventPublisher
    ) {
        this.taskRepo = taskRepo;
        this.stepRepo = stepRepo;
        this.xpCalculator = xpCalculator;
        this.eventPublisher = eventPublisher;
    }

    @Override
    public List<TaskResponse> getActiveTasks(UUID userId) {
        return taskRepo.findByUserIdAndStatusOrderByCreatedAtDesc(userId, Task.TaskStatus.ACTIVE)
            .stream().map(this::toResponse).toList();
    }

    @Override
    public List<TaskResponse> getCompletedTasks(UUID userId) {
        return taskRepo.findByUserIdAndStatusOrderByCreatedAtDesc(userId, Task.TaskStatus.COMPLETED)
            .stream().map(this::toResponse).toList();
    }

    @Override
    public TaskResponse getTask(UUID userId, UUID taskId) {
        Task task = taskRepo.findById(taskId)
            .orElseThrow(() -> new RuntimeException("Task not found"));
        if (!task.getUserId().equals(userId)) throw new RuntimeException("Access denied");
        return toResponse(task);
    }

    @Override
    @Transactional
    public TaskResponse toggleStep(UUID userId, UUID stepId) {
        TaskStep step = stepRepo.findById(stepId)
            .orElseThrow(() -> new RuntimeException("Step not found"));

        Task task = step.getTask();
        if (!task.getUserId().equals(userId)) throw new RuntimeException("Access denied");

        boolean wasCompleted = step.getCompleted();
        step.toggle();
        stepRepo.save(step);

        if (!wasCompleted && step.getCompleted()) {
            // Step completed → increment + award XP
            task.incrementCompleted();

            if (task.getCardId() != null) {
                xpCalculator.awardXp(task.getCardId(), task.getXpPerStep());
            }

            eventPublisher.publishEvent(new TaskStepCompletedEvent(
                task.getId(), step.getId(), task.getCardId(), userId
            ));

            if (task.getStatus() == Task.TaskStatus.COMPLETED) {
                eventPublisher.publishEvent(new TaskCompletedEvent(
                    task.getId(), task.getCardId(), userId
                ));
                log.info("Task {} completed! All {} steps done.", task.getId(), task.getTotalSteps());
            }
        } else if (wasCompleted && !step.getCompleted()) {
            // Step uncompleted → decrement
            task.decrementCompleted();

            if (task.getCardId() != null) {
                // Optionally deduct XP — for now we don't deduct
            }
        }

        taskRepo.save(task);
        return toResponse(task);
    }

    @Override
    @Transactional
    public void abandonTask(UUID userId, UUID taskId) {
        Task task = taskRepo.findById(taskId)
            .orElseThrow(() -> new RuntimeException("Task not found"));
        if (!task.getUserId().equals(userId)) throw new RuntimeException("Access denied");
        task.setStatus(Task.TaskStatus.ABANDONED);
        taskRepo.save(task);
    }

    // ── Mapper ───────────────────────────────────────────

    private TaskResponse toResponse(Task task) {
        List<TaskResponse.StepResponse> steps = task.getSteps().stream()
            .map(s -> new TaskResponse.StepResponse(
                s.getId(), s.getStepNumber(), s.getTitle(),
                s.getDescription(), s.getPayload(), s.getCompleted(),
                s.getCompletedAt(),
                s.getScheduledDate() != null ? s.getScheduledDate().toString() : null
            ))
            .toList();

        return new TaskResponse(
            task.getId(), task.getName(), task.getCategory(),
            task.getStatus(), task.getTotalSteps(), task.getCompletedSteps(),
            task.getXpPerStep(), task.getCardId(), task.getSavedPlanId(),
            steps, task.getCreatedAt()
        );
    }
}
