package com.destinyoracle.shared.event;

import java.util.UUID;

/**
 * Published when all steps of a task are complete.
 */
public record TaskCompletedEvent(
    UUID taskId, UUID cardId, UUID userId
) {}
