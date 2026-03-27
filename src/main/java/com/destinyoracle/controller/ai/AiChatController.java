package com.destinyoracle.controller.ai;

import com.destinyoracle.dto.request.ChatMessageRequest;
import com.destinyoracle.dto.response.ConversationResponse;
import com.destinyoracle.service.AiChatService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/chat")
@Tag(name = "AI Chat", description = "AI chat with SSE streaming, conversation management")
public class AiChatController {

    private final AiChatService chatService;

    public AiChatController(AiChatService chatService) {
        this.chatService = chatService;
    }

    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "Send message and get streaming AI response")
    public Flux<String> chatStream(
        @RequestHeader("X-User-Id") UUID userId,
        @Valid @RequestBody ChatMessageRequest request
    ) {
        return chatService.chat(userId, request.conversationId(), request.message());
    }

    @GetMapping("/conversations")
    @Operation(summary = "List all conversations for a user")
    public ResponseEntity<List<ConversationResponse>> listConversations(
        @RequestHeader("X-User-Id") UUID userId
    ) {
        return ResponseEntity.ok(chatService.listConversations(userId));
    }

    @GetMapping("/conversations/{conversationId}")
    @Operation(summary = "Get a single conversation with messages")
    public ResponseEntity<ConversationResponse> getConversation(
        @RequestHeader("X-User-Id") UUID userId,
        @PathVariable UUID conversationId
    ) {
        return ResponseEntity.ok(chatService.getConversation(userId, conversationId));
    }

    @DeleteMapping("/conversations/{conversationId}")
    @Operation(summary = "Delete a conversation")
    public ResponseEntity<Void> deleteConversation(
        @RequestHeader("X-User-Id") UUID userId,
        @PathVariable UUID conversationId
    ) {
        chatService.deleteConversation(userId, conversationId);
        return ResponseEntity.noContent().build();
    }
}
