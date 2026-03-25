package com.destinyoracle.shared.event;

import java.util.UUID;

/**
 * Published when a card advances to the next stage.
 */
public record StageAdvancedEvent(
    UUID cardId, UUID userId, String oldStage, String newStage
) {}
