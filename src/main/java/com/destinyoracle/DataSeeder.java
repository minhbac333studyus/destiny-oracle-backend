package com.destinyoracle;

import com.destinyoracle.domain.card.entity.*;
import com.destinyoracle.domain.user.entity.*;
import com.destinyoracle.domain.card.repository.*;
import com.destinyoracle.domain.user.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class DataSeeder implements ApplicationRunner {

    private final UserRepository             userRepository;
    private final DestinyCardRepository      destinyCardRepository;
    private final CardStageContentRepository cardStageContentRepository;
    private final CardImageRepository        cardImageRepository;

    private static final UUID DEFAULT_USER_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final String DEMO_ASPECT_KEY = "destiny-oracle-demo";
    private static final String BASE_IMAGE_PATH =
            "/generated/00000000-0000-0000-0000-000000000001/financial-freedom-d872-";

    @Override
    @Transactional
    public void run(ApplicationArguments args) {

        // Idempotency check
        if (destinyCardRepository.existsByAspectKeyAndUserId(DEMO_ASPECT_KEY, DEFAULT_USER_ID)) {
            log.info("Demo card already exists — skipping DataSeeder.");
            return;
        }

        // Find default user
        AppUser user = userRepository.findById(DEFAULT_USER_ID).orElse(null);
        if (user == null) {
            log.warn("Default user {} not found — skipping DataSeeder.", DEFAULT_USER_ID);
            return;
        }

        // Create the demo DestinyCard (no .id() call — @Builder.Default generates UUID)
        DestinyCard card = DestinyCard.builder()
                .user(user)
                .aspectKey(DEMO_ASPECT_KEY)
                .aspectLabel("Destiny Oracle")
                .aspectIcon("🌟")
                .isCustomAspect(true)
                .sortOrder(0)
                .fearOriginal("I fear I will spend my entire life chasing approval, shrinking myself to fit others' expectations, and arriving at my deathbed wondering who I could have been if I had only dared to live fully.")
                .dreamOriginal("I dream of becoming a legend in my own story — someone who faced their deepest fears, transformed through every dark season, and emerged as a radiant force who inspires others simply by being fully, unapologetically alive.")
                .currentStage(CardStage.legend)
                .stageProgressPercent(100)
                .totalCheckIns(365)
                .longestStreak(365)
                .currentStreak(365)
                .daysAtCurrentStage(365)
                .imageUrl("/generated/00000000-0000-0000-0000-000000000001/financial-freedom-d872-legend.png")
                .promptStatus(PromptStatus.READY)
                .lastUpdated(Instant.now())
                .build();

        card = destinyCardRepository.save(card);

        // Seed stage content for all 6 stages (empty placeholders — AI generates action scenes)
        for (CardStage stage : CardStage.values()) {
            seedStageContent(card, stage);
        }

        // Seed card images for all 6 stages
        for (CardStage stage : CardStage.values()) {
            CardImage image = CardImage.builder()
                    .card(card)
                    .stage(stage)
                    .imageUrl(BASE_IMAGE_PATH + stage.name() + ".png")
                    .promptSummary("Demo image — " + capitalize(stage.name()) + " stage")
                    .generatedAt(Instant.now())
                    .build();
            cardImageRepository.save(image);
        }

        log.info("Demo card seeded successfully: {}", DEMO_ASPECT_KEY);
    }

    private void seedStageContent(DestinyCard card, CardStage stage) {
        CardStageContent content = CardStageContent.builder()
                .card(card)
                .stage(stage)
                .generatedAt(Instant.now())
                .build();
        cardStageContentRepository.save(content);
    }

    private static String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
