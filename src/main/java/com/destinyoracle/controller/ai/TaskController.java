package com.destinyoracle.controller.ai;

import com.destinyoracle.dto.response.TaskResponse;
import com.destinyoracle.service.TaskService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/tasks")
@Tag(name = "Tasks", description = "AI-generated tasks with step toggles and XP")
public class TaskController {

    private final TaskService taskService;

    public TaskController(TaskService taskService) {
        this.taskService = taskService;
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
