package com.destinyoracle.domain.card.entity;

/**
 * Which phase of the AI pipeline a step belongs to.
 *
 * PROMPT → Claude generates a text prompt for one stage
 * IMAGE  → Gemini Imagen converts that prompt into a card image
 */
public enum JobPhase {
    PROMPT,
    IMAGE
}
