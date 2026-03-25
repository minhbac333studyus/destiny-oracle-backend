package com.destinyoracle.shared.xp;

import com.destinyoracle.entity.CardStage;
import com.destinyoracle.entity.DestinyCard;
import com.destinyoracle.repository.DestinyCardRepository;
import com.destinyoracle.shared.event.StageAdvancedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Handles XP awards and stage progression for Destiny Cards.
 *
 * XP thresholds per stage:
 *   Storm → Fog:      100 XP
 *   Fog → Clearing:   200 XP
 *   Clearing → Aura:  300 XP
 *   Aura → Radiance:  500 XP
 *   Radiance → Legend: 800 XP
 */
@Component
public class XpCalculator {

    private static final Logger log = LoggerFactory.getLogger(XpCalculator.class);

    private final DestinyCardRepository cardRepo;
    private final ApplicationEventPublisher eventPublisher;

    public XpCalculator(DestinyCardRepository cardRepo, ApplicationEventPublisher eventPublisher) {
        this.cardRepo = cardRepo;
        this.eventPublisher = eventPublisher;
    }

    private static final int STEP_XP = 15;
    private static final int TASK_COMPLETE_XP = 50;

    /**
     * Award XP for completing a single task step (15 XP).
     */
    public void awardTaskStepXp(UUID cardId) {
        if (cardId == null) return;
        awardXp(cardId, STEP_XP);
    }

    /**
     * Award bonus XP for completing all steps of a task (50 XP).
     */
    public void awardTaskCompleteXp(UUID cardId) {
        if (cardId == null) return;
        awardXp(cardId, TASK_COMPLETE_XP);
    }

    /**
     * Award XP to a card and check for stage advancement.
     */
    @Transactional
    public void awardXp(UUID cardId, int xp) {
        if (cardId == null) return;
        var cardOpt = cardRepo.findById(cardId);
        if (cardOpt.isEmpty()) {
            log.warn("Card {} not found for XP award", cardId);
            return;
        }

        DestinyCard card = cardOpt.get();
        card.setCurrentXp(card.getCurrentXp() + xp);

        // Check for stage advancement
        if (card.getCurrentXp() >= card.getXpToNextStage()) {
            advanceStage(card);
        }

        cardRepo.save(card);
    }

    private void advanceStage(DestinyCard card) {
        CardStage currentStage = card.getCurrentStage();
        CardStage nextStage = currentStage.next();

        if (nextStage == null || nextStage == currentStage) {
            // Already at Legend — cap XP
            card.setCurrentXp(card.getXpToNextStage());
            return;
        }

        String oldStageName = currentStage.name();
        card.setCurrentXp(card.getCurrentXp() - card.getXpToNextStage());
        card.setCurrentStage(nextStage);
        card.setXpToNextStage(getXpThreshold(nextStage));
        card.setStageAdvancedAt(LocalDateTime.now());
        card.setDaysAtCurrentStage(0);

        log.info("Card {} advanced: {} → {}", card.getId(), oldStageName, nextStage.name());

        UUID userId = card.getUser() != null ? card.getUser().getId() : null;
        eventPublisher.publishEvent(new StageAdvancedEvent(
            card.getId(), userId, oldStageName, nextStage.name()
        ));
    }

    private int getXpThreshold(CardStage stage) {
        return switch (stage) {
            case storm -> 100;
            case fog -> 200;
            case clearing -> 300;
            case aura -> 500;
            case radiance -> 800;
            case legend -> 999999;  // Can't advance past Legend
        };
    }
}
