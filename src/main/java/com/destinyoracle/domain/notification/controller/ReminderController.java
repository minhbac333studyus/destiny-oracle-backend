package com.destinyoracle.domain.notification.controller;

import com.destinyoracle.dto.request.CreateReminderRequest;
import com.destinyoracle.dto.request.SnoozeReminderRequest;
import com.destinyoracle.dto.response.ReminderResponse;
import com.destinyoracle.domain.notification.service.ReminderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/reminders")
@Tag(name = "Reminders", description = "Smart reminders with repeat and snooze")
public class ReminderController {

    private final ReminderService reminderService;

    public ReminderController(ReminderService reminderService) {
        this.reminderService = reminderService;
    }

    @PostMapping
    @Operation(summary = "Create a new reminder")
    public ResponseEntity<ReminderResponse> createReminder(
        @RequestHeader("X-User-Id") UUID userId,
        @Valid @RequestBody CreateReminderRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(reminderService.createReminder(userId, request));
    }

    @GetMapping
    @Operation(summary = "Get all active (not completed) reminders")
    public ResponseEntity<List<ReminderResponse>> getActiveReminders(
        @RequestHeader("X-User-Id") UUID userId
    ) {
        return ResponseEntity.ok(reminderService.getActiveReminders(userId));
    }

    @GetMapping("/upcoming")
    @Operation(summary = "Get reminders due in the next N hours")
    public ResponseEntity<List<ReminderResponse>> getUpcoming(
        @RequestHeader("X-User-Id") UUID userId,
        @RequestParam(defaultValue = "24") int hours
    ) {
        return ResponseEntity.ok(reminderService.getUpcomingReminders(userId, hours));
    }

    @PatchMapping("/{reminderId}/complete")
    @Operation(summary = "Mark a reminder as completed")
    public ResponseEntity<ReminderResponse> completeReminder(
        @RequestHeader("X-User-Id") UUID userId,
        @PathVariable UUID reminderId
    ) {
        return ResponseEntity.ok(reminderService.completeReminder(userId, reminderId));
    }

    @PatchMapping("/{reminderId}/snooze")
    @Operation(summary = "Snooze a reminder for N minutes")
    public ResponseEntity<ReminderResponse> snoozeReminder(
        @RequestHeader("X-User-Id") UUID userId,
        @PathVariable UUID reminderId,
        @Valid @RequestBody SnoozeReminderRequest request
    ) {
        return ResponseEntity.ok(reminderService.snoozeReminder(userId, reminderId, request.minutes()));
    }

    @DeleteMapping("/{reminderId}")
    @Operation(summary = "Delete a reminder")
    public ResponseEntity<Void> deleteReminder(
        @RequestHeader("X-User-Id") UUID userId,
        @PathVariable UUID reminderId
    ) {
        reminderService.deleteReminder(userId, reminderId);
        return ResponseEntity.noContent().build();
    }
}
