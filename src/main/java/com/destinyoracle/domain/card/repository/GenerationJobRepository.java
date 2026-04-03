package com.destinyoracle.domain.card.repository;

import com.destinyoracle.domain.card.entity.GenerationJob;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GenerationJobRepository extends JpaRepository<GenerationJob, UUID> {

    /** Most recent job for a card — used by the UI polling endpoint. */
    @Query("SELECT j FROM GenerationJob j WHERE j.cardId = :cardId ORDER BY j.createdAt DESC LIMIT 1")
    Optional<GenerationJob> findLatestByCardId(@Param("cardId") UUID cardId);

    /** All jobs for a card, newest first — for job history display. */
    List<GenerationJob> findAllByCardIdOrderByCreatedAtDesc(UUID cardId);

    /** Jobs for a specific user — for admin dashboard. */
    List<GenerationJob> findAllByUserIdOrderByCreatedAtDesc(UUID userId);
}
