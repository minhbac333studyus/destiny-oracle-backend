package com.destinyoracle.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

/**
 * One atomic step inside a GenerationJob.
 *
 * Steps are pre-created in WAITING state so the UI shows the full
 * pipeline before anything runs. Each step transitions through:
 *   WAITING → RUNNING → DONE | FAILED | SKIPPED
 *
 * For PROMPT phase: resultUrl is null (prompts are text, stored in card_stage_content)
 * For IMAGE  phase: resultUrl is the GCS URL of the generated image
 */
@Entity
@Table(name = "generation_job_steps",
       indexes = {
           @Index(name = "idx_step_job",   columnList = "job_id"),
           @Index(name = "idx_step_order", columnList = "job_id, step_order")
       })
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class GenerationJobStep {

    @Id
    @Builder.Default
    private UUID id = UUID.randomUUID();

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "job_id", nullable = false)
    private GenerationJob job;

    /** 0-based ordering: 0–5 = PROMPT steps, 6–11 = IMAGE steps. */
    @Column(name = "step_order", nullable = false)
    private int stepOrder;

    /** Human-readable name shown in the UI — e.g. "Generate storm prompt" */
    @Column(name = "step_name", nullable = false, length = 100)
    @Builder.Default
    private String stepName = "";

    @Enumerated(EnumType.STRING)
    @Column(name = "phase", nullable = false, length = 10)
    private JobPhase phase;

    /** Which CardStage this step targets — e.g. "storm", "fog" */
    @Column(name = "stage", nullable = false, length = 20)
    private String stage;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 10)
    @Builder.Default
    private StepStatus status = StepStatus.WAITING;

    /**
     * Human-readable log message visible in the UI.
     * Updated at each state transition so users see real-time progress.
     * Examples:
     *   WAITING  → "Waiting for previous steps to complete…"
     *   RUNNING  → "Calling Claude to write the storm stage prompt…"
     *   DONE     → "Storm prompt ready — 142 characters"
     *   SKIPPED  → "Storm prompt already exists in DB — skipping Claude"
     *   FAILED   → "Claude timed out after 30s: Connection refused"
     */
    @Column(name = "message", columnDefinition = "TEXT")
    @Builder.Default
    private String message = "Waiting for previous steps to complete\u2026";

    /** GCS URL populated after a successful IMAGE step. Null for PROMPT steps. */
    @Column(name = "result_url", columnDefinition = "TEXT")
    private String resultUrl;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;
}
