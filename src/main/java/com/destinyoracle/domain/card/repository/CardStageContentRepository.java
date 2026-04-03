package com.destinyoracle.domain.card.repository;

import com.destinyoracle.domain.card.entity.CardStage;
import com.destinyoracle.domain.card.entity.CardStageContent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CardStageContentRepository extends JpaRepository<CardStageContent, UUID> {
    List<CardStageContent> findAllByCardIdOrderByStageAsc(UUID cardId);
    Optional<CardStageContent> findByCardIdAndStage(UUID cardId, CardStage stage);
}
