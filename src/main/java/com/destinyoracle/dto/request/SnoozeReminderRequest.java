package com.destinyoracle.dto.request;

import jakarta.validation.constraints.Min;

public record SnoozeReminderRequest(
    @Min(1) int minutes   // snooze for N minutes
) {}
