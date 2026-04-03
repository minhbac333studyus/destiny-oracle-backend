package com.destinyoracle.domain.card.event;

import com.destinyoracle.domain.card.service.CardImageGenerationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * Listens for card lifecycle events and triggers background processing.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CardEventListener {

    private final CardImageGenerationService cardImageGenerationService;

    @Async
    @EventListener
    public void onCardCreated(CardCreatedEvent event) {
        log.info("Received CardCreatedEvent — starting image pipeline in background for card={}", event.cardId());
        try {
            cardImageGenerationService.generateAllStageImages(event.userId(), event.cardId());
            log.info("Image pipeline completed successfully for card={}", event.cardId());
        } catch (Exception e) {
            log.error("Image pipeline FAILED for card={}: {}", event.cardId(), e.getMessage(), e);
        }
    }
}
