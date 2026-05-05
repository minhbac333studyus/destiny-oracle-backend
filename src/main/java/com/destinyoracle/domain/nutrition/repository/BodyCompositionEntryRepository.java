package com.destinyoracle.domain.nutrition.repository;

import com.destinyoracle.domain.nutrition.entity.BodyCompositionEntry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BodyCompositionEntryRepository extends JpaRepository<BodyCompositionEntry, UUID> {
    List<BodyCompositionEntry> findByUserIdOrderByLogDateDesc(UUID userId);
    Optional<BodyCompositionEntry> findFirstByUserIdOrderByLogDateDesc(UUID userId);
}
