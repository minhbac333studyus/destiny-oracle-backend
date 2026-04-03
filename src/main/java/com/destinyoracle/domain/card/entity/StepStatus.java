package com.destinyoracle.domain.card.entity;

/**
 * Lifecycle states for a single GenerationJobStep.
 *
 * WAITING  → step queued but not yet started
 * RUNNING  → step is actively executing (Claude call or Gemini call in flight)
 * DONE     → step completed successfully
 * FAILED   → step threw an exception; see step.message for the error
 * SKIPPED  → step bypassed (e.g. prompt already existed in DB from a previous run)
 */
public enum StepStatus {
    WAITING,
    RUNNING,
    DONE,
    FAILED,
    SKIPPED
}
