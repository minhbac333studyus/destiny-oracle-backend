package com.destinyoracle.service.impl;

import com.destinyoracle.domain.chat.entity.AiConversation;
import com.destinyoracle.domain.chat.entity.AiMessage;
import com.destinyoracle.domain.chat.repository.AiConversationRepository;
import com.destinyoracle.domain.chat.repository.AiMessageRepository;
import com.destinyoracle.dto.response.ConversationResponse;
import com.destinyoracle.integration.Mem0Client;
import com.destinyoracle.service.AiChatService;
import com.destinyoracle.shared.ai.ConversationCompressor;
import com.destinyoracle.shared.ai.IntentClassifier;
import com.destinyoracle.shared.context.AssembledContext;
import com.destinyoracle.shared.context.ContextAssembler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class AiChatServiceImpl implements AiChatService {

    private static final Logger log = LoggerFactory.getLogger(AiChatServiceImpl.class);

    private final ChatClient chatClient;
    private final AiConversationRepository conversationRepo;
    private final AiMessageRepository messageRepo;
    private final ContextAssembler contextAssembler;
    private final IntentClassifier intentClassifier;
    private final ConversationCompressor compressor;
    private final Mem0Client mem0Client;

    public AiChatServiceImpl(
        ChatClient.Builder chatClientBuilder,
        AiConversationRepository conversationRepo,
        AiMessageRepository messageRepo,
        ContextAssembler contextAssembler,
        IntentClassifier intentClassifier,
        ConversationCompressor compressor,
        Mem0Client mem0Client
    ) {
        this.chatClient = chatClientBuilder.build();
        this.conversationRepo = conversationRepo;
        this.messageRepo = messageRepo;
        this.contextAssembler = contextAssembler;
        this.intentClassifier = intentClassifier;
        this.compressor = compressor;
        this.mem0Client = mem0Client;
    }

    @Override
    public Flux<String> chat(UUID userId, UUID conversationId, String message) {
        // 1. Get or create conversation
        AiConversation conversation;
        if (conversationId != null) {
            conversation = conversationRepo.findById(conversationId)
                .orElseThrow(() -> new RuntimeException("Conversation not found: " + conversationId));
        } else {
            conversation = AiConversation.builder()
                .userId(userId)
                .title(message.length() > 50 ? message.substring(0, 50) + "..." : message)
                .build();
            conversation = conversationRepo.save(conversation);
        }

        final UUID convId = conversation.getId();

        // 2. Save user message
        AiMessage userMsg = AiMessage.builder()
            .conversation(conversation)
            .role("USER")
            .content(message)
            .build();
        messageRepo.save(userMsg);

        // 3. Classify intent
        IntentClassifier.Intent intent = intentClassifier.classify(message);
        log.debug("Intent classified as: {} for message: '{}'", intent, message.substring(0, Math.min(50, message.length())));

        // 4. Assemble context
        AssembledContext ctx = contextAssembler.assemble(userId, convId, message);

        // 5. Build messages for Claude
        List<Message> messages = new ArrayList<>();
        messages.add(new SystemMessage(ctx.systemPrompt()));

        // Add session summary context
        if (!ctx.sessionSummary().isEmpty()) {
            messages.add(new SystemMessage("Previous conversation summary:\n" + ctx.sessionSummary()));
        }

        // Add Mem0 memories
        if (!ctx.mem0Memories().isEmpty()) {
            messages.add(new SystemMessage(ctx.mem0Memories()));
        }

        // Add saved plan context
        if (!ctx.savedPlanContext().isEmpty()) {
            messages.add(new SystemMessage("Relevant saved plan:\n" + ctx.savedPlanContext()));
        }

        // Add recent messages for continuity
        for (AssembledContext.MessagePair pair : ctx.recentMessages()) {
            if ("USER".equals(pair.role())) {
                messages.add(new UserMessage(pair.content()));
            } else {
                messages.add(new AssistantMessage(pair.content()));
            }
        }

        // Add the new user message
        messages.add(new UserMessage(ctx.userMessage()));

        // 6. Stream response from Claude
        Sinks.Many<String> sink = Sinks.many().unicast().onBackpressureBuffer();

        StringBuilder fullResponse = new StringBuilder();

        Flux<String> stream = chatClient.prompt()
            .messages(messages)
            .stream()
            .content();

        stream.subscribe(
            chunk -> {
                fullResponse.append(chunk);
                sink.tryEmitNext(chunk);
            },
            error -> {
                log.error("Chat stream error for conv {}: {}", convId, error.getMessage());
                sink.tryEmitError(error);
            },
            () -> {
                // Save assistant response
                String response = fullResponse.toString();
                AiMessage assistantMsg = AiMessage.builder()
                    .conversation(conversationRepo.getReferenceById(convId))
                    .role("ASSISTANT")
                    .content(response)
                    .actionType(intent != IntentClassifier.Intent.GENERAL ? intent.name() : null)
                    .build();
                messageRepo.save(assistantMsg);

                // Async: add to Mem0 long-term memory
                addToMem0Async(userId, message, response);

                // Async: check if compression is needed
                compressor.compressIfNeeded(convId);

                sink.tryEmitComplete();
            }
        );

        return sink.asFlux();
    }

    @Async
    protected void addToMem0Async(UUID userId, String userMessage, String response) {
        try {
            mem0Client.addMemory(userId, userMessage, response);
        } catch (Exception e) {
            log.debug("Mem0 async memory add failed: {}", e.getMessage());
        }
    }

    @Override
    public List<ConversationResponse> listConversations(UUID userId) {
        return conversationRepo.findByUserIdOrderByUpdatedAtDesc(userId).stream()
            .map(conv -> new ConversationResponse(
                conv.getId(),
                conv.getTitle(),
                conv.getCreatedAt(),
                conv.getUpdatedAt(),
                List.of()  // Don't load messages for list view
            ))
            .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public ConversationResponse getConversation(UUID userId, UUID conversationId) {
        AiConversation conv = conversationRepo.findById(conversationId)
            .orElseThrow(() -> new RuntimeException("Conversation not found"));

        if (!conv.getUserId().equals(userId)) {
            throw new RuntimeException("Access denied");
        }

        List<ConversationResponse.MessageResponse> messages = conv.getMessages().stream()
            .filter(m -> !m.getCompressed())
            .map(m -> new ConversationResponse.MessageResponse(
                m.getId(), m.getRole(), m.getContent(),
                m.getActionType(), m.getActionPayload(), m.getCreatedAt()
            ))
            .toList();

        return new ConversationResponse(
            conv.getId(), conv.getTitle(),
            conv.getCreatedAt(), conv.getUpdatedAt(), messages
        );
    }

    @Override
    @Transactional
    public void deleteConversation(UUID userId, UUID conversationId) {
        AiConversation conv = conversationRepo.findById(conversationId)
            .orElseThrow(() -> new RuntimeException("Conversation not found"));

        if (!conv.getUserId().equals(userId)) {
            throw new RuntimeException("Access denied");
        }

        conversationRepo.delete(conv);
    }
}
