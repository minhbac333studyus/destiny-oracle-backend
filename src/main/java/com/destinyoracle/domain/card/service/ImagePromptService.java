package com.destinyoracle.domain.card.service;

import com.destinyoracle.dto.response.ImagePromptResponse;
import com.destinyoracle.domain.card.entity.GenerationJob;

import java.util.UUID;

public interface ImagePromptService {

    /**
     * Generates 6 image-generation prompts for all stages of a destiny card via Claude.
     * Prompts are persisted to card_stage_content.image_prompt so they survive
     * between API calls and don't need to be regenerated.
     *
     * @param userId  the user who owns the card
     * @param cardId  the destiny card to generate prompts for
     * @return        a map of stage → image prompt string
     */
    ImagePromptResponse generatePromptsForCard(UUID userId, UUID cardId);

    /**
     * Same as above but also updates step statuses inside the provided GenerationJob
     * so the UI polling endpoint reflects real-time progress.
     *
     * @param job  the active job (null-safe — if null, behaves identically to the 2-arg overload)
     */
    ImagePromptResponse generatePromptsForCard(UUID userId, UUID cardId, GenerationJob job);
}
