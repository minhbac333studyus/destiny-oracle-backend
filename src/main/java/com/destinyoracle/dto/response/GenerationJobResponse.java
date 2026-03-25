package com.destinyoracle.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data
@Builder
public class GenerationJobResponse {
    private UUID   id;
    private UUID   cardId;
    private UUID   userId;
    private String jobLabel;
    private String status;          // JobStatus enum name
    private int    totalSteps;
    private int    completedSteps;
    private int    progressPercent; // 0–100, computed server-side
    private Instant createdAt;
    private Instant startedAt;
    private Instant completedAt;
    private String  errorMessage;
    private List<GenerationJobStepResponse> steps;
}
