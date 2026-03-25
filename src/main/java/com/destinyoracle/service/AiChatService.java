package com.destinyoracle.service;

import com.destinyoracle.dto.response.ConversationResponse;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.UUID;

public interface AiChatService {

    /**
     * Send a message and get streaming SSE response.
     */
    Flux<String> chat(UUID userId, UUID conversationId, String message);

    /**
     * List all conversations for a user.
     */
    List<ConversationResponse> listConversations(UUID userId);

    /**
     * Get a single conversation with messages.
     */
    ConversationResponse getConversation(UUID userId, UUID conversationId);

    /**
     * Delete a conversation.
     */
    void deleteConversation(UUID userId, UUID conversationId);
}
