package com.destinyoracle.shared.ai;

import com.destinyoracle.domain.chat.entity.AiMessage;
import com.destinyoracle.domain.chat.entity.ConversationMemory;
import com.destinyoracle.domain.chat.repository.AiMessageRepository;
import com.destinyoracle.domain.chat.repository.ConversationMemoryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Compresses older conversation messages into bullet-point summaries.
 * Triggered every 20 uncompressed messages — keeps recent 10, compresses the rest.
 * Saves ~70% tokens per compression round.
 */
@Component
public class ConversationCompressor {

    private static final Logger log = LoggerFactory.getLogger(ConversationCompressor.class);

    private static final int COMPRESSION_THRESHOLD = 20;
    private static final int KEEP_RECENT = 10;

    private final AiMessageRepository messageRepo;
    private final ConversationMemoryRepository memoryRepo;
    private final ChatClient chatClient;

    public ConversationCompressor(
        AiMessageRepository messageRepo,
        ConversationMemoryRepository memoryRepo,
        ChatClient.Builder chatClientBuilder
    ) {
        this.messageRepo = messageRepo;
        this.memoryRepo = memoryRepo;
        this.chatClient = chatClientBuilder.build();
    }

    /**
     * Check if compression is needed and run it async.
     */
    @Async
    @Transactional
    public void compressIfNeeded(UUID conversationId) {
        try {
            long uncompressedCount = messageRepo.countUncompressed(conversationId);
            if (uncompressedCount < COMPRESSION_THRESHOLD) return;

            compress(conversationId);
        } catch (Exception e) {
            log.error("Compression failed for conversation {}: {}", conversationId, e.getMessage());
        }
    }

    @Transactional
    public void compress(UUID conversationId) {
        List<AiMessage> candidates = messageRepo.findCompressCandidates(conversationId, KEEP_RECENT);
        if (candidates.isEmpty()) return;

        // Build text for compression (pre-truncate long messages to save tokens)
        String conversationText = candidates.stream()
            .map(m -> m.getRole() + ": " + (m.getContent().length() > 300
                ? m.getContent().substring(0, 300) + "…"
                : m.getContent()))
            .collect(Collectors.joining("\n"));

        // Call Claude to summarize
        String summary = chatClient.prompt()
            .system("""
                Summarize this conversation excerpt into concise bullet points.
                Focus on: user preferences, decisions made, tasks created, plans discussed,
                important facts mentioned. Keep it under 200 words.
                Format: bullet points starting with "- "
                """)
            .user(conversationText)
            .call()
            .content();

        // Get next compression round number
        int round = memoryRepo.findMaxRound(conversationId).orElse(0) + 1;

        // Save the summary
        ConversationMemory memory = ConversationMemory.builder()
            .conversationId(conversationId)
            .summary(summary)
            .messagesCompressed(candidates.size())
            .tokenEstimate(summary.length() / 4)
            .compressionRound(round)
            .build();
        memoryRepo.save(memory);

        // Mark messages as compressed
        List<UUID> messageIds = candidates.stream().map(AiMessage::getId).toList();
        messageRepo.markAsCompressed(messageIds, memory.getId());

        log.info("Compressed {} messages in conversation {} (round {})",
            candidates.size(), conversationId, round);
    }
}
