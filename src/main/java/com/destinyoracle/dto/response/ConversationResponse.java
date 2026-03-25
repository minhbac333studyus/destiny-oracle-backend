package com.destinyoracle.dto.response;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record ConversationResponse(
    UUID id,
    String title,
    LocalDateTime createdAt,
    LocalDateTime updatedAt,
    List<MessageResponse> messages
) {
    public record MessageResponse(
        UUID id,
        String role,
        String content,
        String actionType,
        String actionPayload,
        LocalDateTime createdAt
    ) {}
}
