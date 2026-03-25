package com.destinyoracle.domain.chat.repository;

import com.destinyoracle.domain.chat.entity.ConversationMemory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ConversationMemoryRepository extends JpaRepository<ConversationMemory, UUID> {

    List<ConversationMemory> findByConversationIdOrderByCompressionRound(UUID conversationId);

    @Query("SELECT MAX(m.compressionRound) FROM ConversationMemory m WHERE m.conversationId = :convId")
    Optional<Integer> findMaxRound(@Param("convId") UUID conversationId);

    void deleteByConversationId(UUID conversationId);
}
