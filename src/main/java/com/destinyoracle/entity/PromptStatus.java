package com.destinyoracle.entity;

/**
 * Tracks whether Claude has generated and saved image prompts for a destiny card.
 *
 * NONE       → prompts never generated; calling generate-images will trigger Claude first
 * GENERATING → Claude is currently running (guards against double-calls in concurrent requests)
 * READY      → all 6 stage prompts saved to card_stage_content.image_prompt; Claude can be skipped
 * FAILED     → last Claude call failed; prompts may be partial; retry is allowed
 */
public enum PromptStatus {
    NONE,
    PENDING,
    GENERATING,
    READY,
    FAILED
}
