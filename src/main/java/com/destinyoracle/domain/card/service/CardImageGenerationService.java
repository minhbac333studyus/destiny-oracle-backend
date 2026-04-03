package com.destinyoracle.domain.card.service;

import com.destinyoracle.dto.response.BatchImageResult;
import com.destinyoracle.dto.response.GeneratedImageResponse;
import com.destinyoracle.dto.response.GenerationJobResponse;

import java.util.List;
import java.util.UUID;

public interface CardImageGenerationService {

    /**
     * Full pipeline for one card with job tracking (12 steps):
     *   Phase 1 — Claude generates 6 prompts (steps 0–5, PROMPT phase)
     *   Phase 2 — Gemini Imagen generates 6 images in parallel (steps 6–11, IMAGE phase)
     *
     * A GenerationJob is created upfront so the UI can poll GET /{cardId}/jobs/latest
     * to see real-time step transitions (WAITING → RUNNING → DONE|FAILED|SKIPPED).
     *
     * ★ Phase 2 only starts after ALL 6 prompt steps are DONE or SKIPPED.
     */
    List<GeneratedImageResponse> generateAllStageImages(UUID userId, UUID cardId);

    /**
     * Generate (or re-generate) the image for a single specific stage.
     * Uses the saved prompt from DB; only calls Claude if no prompt exists yet.
     * No job tracking (lightweight, fire-and-forget).
     */
    GeneratedImageResponse generateStageImage(UUID userId, UUID cardId, String stage);

    /**
     * Run the full pipeline (prompts → images) for ALL of a user's aspect cards.
     *
     * This is the true "batch" — one GenerationJob per aspect card, each running
     * its own 6-stage image pipeline. Cards are processed one at a time to avoid
     * overwhelming the Gemini API; within each card all 6 stages run in parallel.
     *
     * Use case: first-time setup — generate all starter card art in one call.
     *
     * @param userId  the user whose cards to generate
     * @return one BatchImageResult per card, with status "completed" or "failed"
     */
    List<BatchImageResult> generateAllUserCards(UUID userId);
}
