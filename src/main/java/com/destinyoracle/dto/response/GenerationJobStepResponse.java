package com.destinyoracle.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
public class GenerationJobStepResponse {
    private UUID   id;
    private int    stepOrder;
    private String stepName;
    private String phase;       // "PROMPT" | "IMAGE"
    private String stage;       // "storm" | "fog" | ...
    private String status;      // "WAITING" | "RUNNING" | "DONE" | "FAILED" | "SKIPPED"
    private String message;     // human-readable log line for the UI
    private String resultUrl;   // GCS URL (IMAGE steps only)
    private Instant startedAt;
    private Instant completedAt;
}
