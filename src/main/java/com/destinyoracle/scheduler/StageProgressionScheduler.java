package com.destinyoracle.scheduler;

import com.destinyoracle.entity.DestinyCard;
import com.destinyoracle.repository.DestinyCardRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Daily job that increments daysAtCurrentStage for all cards.
 * Runs at midnight.
 */
@Component
public class StageProgressionScheduler {

    private static final Logger log = LoggerFactory.getLogger(StageProgressionScheduler.class);

    private final DestinyCardRepository cardRepo;

    public StageProgressionScheduler(DestinyCardRepository cardRepo) {
        this.cardRepo = cardRepo;
    }

    @Scheduled(cron = "0 0 0 * * *")  // Midnight
    @Transactional
    public void incrementDaysAtStage() {
        List<DestinyCard> cards = cardRepo.findAll();
        for (DestinyCard card : cards) {
            card.setDaysAtCurrentStage(card.getDaysAtCurrentStage() + 1);
        }
        cardRepo.saveAll(cards);
        log.info("Incremented daysAtCurrentStage for {} cards", cards.size());
    }
}
