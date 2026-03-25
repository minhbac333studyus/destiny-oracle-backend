package com.destinyoracle.service;

import com.destinyoracle.dto.response.StageContentGenerationResponse;

import java.util.UUID;

public interface StageContentGenerationService {

    /**
     * Core pipeline step 0 — the very first AI call when a user adds a new card.
     *
     * Takes the user's raw fear + dream text and asks Claude to write a full
     * narrative arc across all 6 stages (Storm → Fog → Clearing → Aura → Radiance → Legend).
     *
     * Each stage gets:
     *   - title    : short evocative card name  (e.g. "The Invisible Worker")
     *   - tagline  : one-line emotional hook     (e.g. "Talent buried under obligation")
     *   - lore     : 2–3 sentences of narrative  (e.g. "Years pass. The desk stays the same…")
     *
     * Results are persisted to card_stage_content immediately so they are available
     * for image prompt generation (ImagePromptService uses them as emotional context).
     *
     * @param userId  user who owns the card
     * @param cardId  the newly created card
     * @return        map of stage → {title, tagline, lore}
     */
    StageContentGenerationResponse generateStageContent(UUID userId, UUID cardId);

    /**
     * Re-generate stage content after the user edits their fear/dream text.
     * Clears existing content for all 6 stages and regenerates fresh from the new input.
     * Also resets promptStatus to NONE so image prompts are re-generated on next image call.
     */
    StageContentGenerationResponse regenerateStageContent(UUID userId, UUID cardId);
}
