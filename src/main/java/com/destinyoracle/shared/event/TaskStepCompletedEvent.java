package com.destinyoracle.shared.event;

import java.util.UUID;

/**
 * Published when a task step is toggled complete.
 */
public record TaskStepCompletedEvent(
    UUID taskId, UUID stepId, UUID cardId, UUID userId
) {}
