package com.destinyoracle.entity;

/**
 * Lifecycle states for a GenerationJob.
 *
 * QUEUED     → job created, waiting to start
 * PROMPTING  → Claude is generating the 6 image prompts
 * IMAGING    → all 6 prompts ready; Gemini Imagen is generating card art (parallel)
 * COMPLETED  → all steps finished successfully
 * FAILED     → one or more steps failed; see step.message for details
 */
public enum JobStatus {
    QUEUED,
    PROMPTING,
    IMAGING,
    COMPLETED,
    FAILED
}
