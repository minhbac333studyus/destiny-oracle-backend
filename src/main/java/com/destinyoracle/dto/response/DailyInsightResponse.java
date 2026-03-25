package com.destinyoracle.dto.response;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public record DailyInsightResponse(
    UUID id,
    LocalDate insightDate,
    String content,
    String suggestions,
    Integer tasksCompleted,
    LocalDateTime createdAt
) {}
