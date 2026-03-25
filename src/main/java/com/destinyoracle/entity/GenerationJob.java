package com.destinyoracle.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Tracks one full AI generation pipeline run for a card.
 *
 * A job has exactly 12 steps: 6 PROMPT steps + 6 IMAGE steps.
 * All 12 are created upfront as WAITING so the UI can render the full
 * pipeline immediately. Steps transition WAITING → RUNNING → DONE|FAILED
 * in order. IMAGE steps cannot start until ALL PROMPT steps are DONE.
 */
@Entity
@Table(name = "generation_jobs",
       indexes = {
           @Index(name = "idx_gen_job_card",    columnList = "card_id"),
           @Index(name = "idx_gen_job_user",    columnList = "user_id"),
           @Index(name = "idx_gen_job_created", columnList = "created_at")
       })
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class GenerationJob {

    @Id
    @Builder.Default
    private UUID id = UUID.randomUUID();

    @Column(name = "card_id", nullable = false)
    private UUID cardId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    /** Human-readable label — e.g. "health · Full Pipeline" */
    @Column(name = "job_label", length = 200)
    @Builder.Default
    private String jobLabel = "";

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private JobStatus status = JobStatus.QUEUED;

    /** Total number of steps (normally 12 = 6 prompt + 6 image). */
    @Column(name = "total_steps", nullable = false)
    @Builder.Default
    private int totalSteps = 12;

    /** How many steps have reached DONE or SKIPPED. */
    @Column(name = "completed_steps", nullable = false)
    @Builder.Default
    private int completedSteps = 0;

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @OneToMany(mappedBy = "job",
               cascade = CascadeType.ALL,
               orphanRemoval = true,
               fetch = FetchType.EAGER)
    @OrderBy("stepOrder ASC")
    @Builder.Default
    private List<GenerationJobStep> steps = new ArrayList<>();
}
