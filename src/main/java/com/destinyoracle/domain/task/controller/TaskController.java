package com.destinyoracle.domain.task.controller;

import com.destinyoracle.domain.task.entity.Task;
import com.destinyoracle.dto.response.TaskResponse;
import com.destinyoracle.domain.task.service.TaskService;
import com.destinyoracle.domain.task.service.TaskService.StepInput;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/tasks")
@Tag(name = "Tasks", description = "AI-generated tasks with step toggles and XP")
public class TaskController {

    private final TaskService taskService;

    public TaskController(TaskService taskService) {
        this.taskService = taskService;
    }

    @PostMapping
    @Operation(summary = "Create a new task with steps")
    @SuppressWarnings("unchecked")
    public ResponseEntity<TaskResponse> createTask(
        @RequestHeader("X-User-Id") UUID userId,
        @RequestBody Map<String, Object> body
    ) {
        String name = (String) body.get("name");
        Task.TaskCategory category = Task.TaskCategory.valueOf(
            ((String) body.getOrDefault("category", "CUSTOM")).toUpperCase());

        List<Map<String, String>> rawSteps = (List<Map<String, String>>) body.getOrDefault("steps", List.of());
        List<StepInput> steps = rawSteps.stream()
            .map(s -> new StepInput(
                s.getOrDefault("title", "Step"),
                s.get("description"),
                s.get("payload")))
            .toList();

        return ResponseEntity.status(201).body(taskService.createTask(userId, name, category, steps));
    }

    @GetMapping("/active")
    @Operation(summary = "Get all active tasks")
    public ResponseEntity<List<TaskResponse>> getActiveTasks(
        @RequestHeader("X-User-Id") UUID userId
    ) {
        return ResponseEntity.ok(taskService.getActiveTasks(userId));
    }

    @GetMapping("/completed")
    @Operation(summary = "Get completed tasks")
    public ResponseEntity<List<TaskResponse>> getCompletedTasks(
        @RequestHeader("X-User-Id") UUID userId
    ) {
        return ResponseEntity.ok(taskService.getCompletedTasks(userId));
    }

    @GetMapping("/{taskId}")
    @Operation(summary = "Get a specific task with steps")
    public ResponseEntity<TaskResponse> getTask(
        @RequestHeader("X-User-Id") UUID userId,
        @PathVariable UUID taskId
    ) {
        return ResponseEntity.ok(taskService.getTask(userId, taskId));
    }

    @PatchMapping("/steps/{stepId}/toggle")
    @Operation(summary = "Toggle a step complete/incomplete")
    public ResponseEntity<TaskResponse> toggleStep(
        @RequestHeader("X-User-Id") UUID userId,
        @PathVariable UUID stepId
    ) {
        return ResponseEntity.ok(taskService.toggleStep(userId, stepId));
    }

    @PatchMapping("/{taskId}/abandon")
    @Operation(summary = "Abandon a task")
    public ResponseEntity<Void> abandonTask(
        @RequestHeader("X-User-Id") UUID userId,
        @PathVariable UUID taskId
    ) {
        taskService.abandonTask(userId, taskId);
        return ResponseEntity.noContent().build();
    }
}
