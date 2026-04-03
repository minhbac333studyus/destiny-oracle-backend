package com.destinyoracle.domain.card.repository;

import com.destinyoracle.domain.card.entity.CardImage;
import com.destinyoracle.domain.card.entity.CardStage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CardImageRepository extends JpaRepository<CardImage, UUID> {

    Optional<CardImage> findByCardIdAndStage(UUID cardId, CardStage stage);

    List<CardImage> findAllByCardIdOrderByStageAsc(UUID cardId);
}
