package com.destinyoracle.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record ChatMessageRequest(
    @NotBlank @Size(max = 5000) String message,
    UUID conversationId  // null = new conversation
) {}
