package com.destinyoracle.event;

import java.util.UUID;

/**
 * Published after a new card is created and stage content generation succeeds.
 * Triggers the async image generation pipeline in the background.
 */
public record CardCreatedEvent(UUID userId, UUID cardId) {
}
