package com.destinyoracle.domain.chat.repository;

import com.destinyoracle.domain.chat.entity.AiMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface AiMessageRepository extends JpaRepository<AiMessage, UUID> {

    /**
     * Get last N uncompressed messages for the recent window (Layer 4).
     * Returns in DESC order — caller reverses if needed.
     */
    @Query(value = """
        SELECT * FROM ai_messages
        WHERE conversation_id = :convId AND compressed = false
        ORDER BY created_at DESC
        LIMIT :limit
        """, nativeQuery = true)
    List<AiMessage> findRecentUncompressed(
        @Param("convId") UUID conversationId,
        @Param("limit") int limit);

    /**
     * Count uncompressed messages — used to decide if compression triggers.
     */
    @Query("SELECT COUNT(m) FROM AiMessage m WHERE m.conversation.id = :convId AND m.compressed = false")
    long countUncompressed(@Param("convId") UUID conversationId);

    /**
     * Get messages eligible for compression.
     * Uncompressed messages EXCEPT the most recent N.
     */
    @Query(value = """
        SELECT * FROM ai_messages
        WHERE conversation_id = :convId AND compressed = false
        ORDER BY created_at ASC
        LIMIT GREATEST(0,
            (SELECT COUNT(*) FROM ai_messages
             WHERE conversation_id = :convId AND compressed = false)
            - :keepRecent)
        """, nativeQuery = true)
    List<AiMessage> findCompressCandidates(
        @Param("convId") UUID conversationId,
        @Param("keepRecent") int keepRecent);

    /**
     * Mark messages as compressed after summarization.
     */
    @Modifying
    @Query("""
        UPDATE AiMessage m SET m.compressed = true, m.compressionMemoryId = :memoryId
        WHERE m.id IN :messageIds
        """)
    void markAsCompressed(
        @Param("messageIds") List<UUID> messageIds,
        @Param("memoryId") UUID memoryId);
}
