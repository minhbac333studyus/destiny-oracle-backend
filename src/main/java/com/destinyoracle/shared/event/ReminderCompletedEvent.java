package com.destinyoracle.shared.event;

import java.util.UUID;

/**
 * Published when a reminder is marked done.
 */
public record ReminderCompletedEvent(
    UUID reminderId, UUID cardId, UUID userId
) {}
