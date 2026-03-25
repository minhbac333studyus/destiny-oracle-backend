package com.destinyoracle.repository;

import com.destinyoracle.entity.DestinyCard;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DestinyCardRepository extends JpaRepository<DestinyCard, UUID> {

    @Query("""
        SELECT c FROM DestinyCard c
        WHERE c.user.id = :userId
        ORDER BY c.sortOrder ASC
        """)
    List<DestinyCard> findAllByUserId(@Param("userId") UUID userId);

    // NOTE: Only stageContents is JOIN FETCHed here — Hibernate forbids multiple bag fetches
    // in one query. imageHistory and habits are FetchType.LAZY and load on first access
    // inside the same @Transactional boundary, which is fine for all service operations.
    @Query("""
        SELECT c FROM DestinyCard c
        LEFT JOIN FETCH c.stageContents
        LEFT JOIN FETCH c.user
        WHERE c.id = :cardId AND c.user.id = :userId
        """)
    Optional<DestinyCard> findByIdAndUserIdWithDetails(
            @Param("cardId") UUID cardId,
            @Param("userId") UUID userId);

    Optional<DestinyCard> findByAspectKeyAndUserId(String aspectKey, UUID userId);

    boolean existsByAspectKeyAndUserId(String aspectKey, UUID userId);

    List<DestinyCard> findAllByUserIdOrderByUpdatedAtDesc(UUID userId);

    long countByUserId(UUID userId);
}
