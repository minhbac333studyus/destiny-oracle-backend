package com.destinyoracle;

import com.destinyoracle.entity.*;
import com.destinyoracle.repository.*;
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

        // Seed stage content for all 6 stages
        seedStageContent(card, CardStage.storm,
                "The Midnight Spiral",
                "When the weight of who you're not crushes who you could be",
                "In the darkest hour before transformation, fear wears the face of certainty — whispering that this is all there is. The cards are heavy with the weight of unlived dreams, but within that weight lies the seed of everything. You are not broken; you are breaking open.");

        seedStageContent(card, CardStage.fog,
                "Lost in the In-Between",
                "The old self is gone; the new self hasn't arrived yet",
                "Between the person you were and the person you are becoming, there is a sacred disorientation. Nothing is clear yet everything is shifting — old patterns dissolve while new ones form in the silence. Trust the fog; it is doing its holy work.");

        seedStageContent(card, CardStage.clearing,
                "First Light",
                "A single ray of truth breaks through the clouds",
                "The first honest moment arrives like dawn — quiet, unmistakable, revolutionary. You see yourself without the armor and realize the armor was never keeping you safe, it was keeping you small. This clarity is your compass from here forward.");

        seedStageContent(card, CardStage.aura,
                "The Awakening",
                "You begin to recognize your own power",
                "Something ancient and electric begins to move through you, recognizing itself in mirrors, in strangers, in the night sky. You are not just changing — you are remembering. The energy that moves mountains has always lived inside you.");

        seedStageContent(card, CardStage.radiance,
                "Golden Becoming",
                "Your presence lights up every room you enter",
                "You have stopped waiting for permission. Your gifts flow freely now, touching lives in ways you can no longer track or contain. The world feels your presence before you speak, and your joy has become a form of service.");

        seedStageContent(card, CardStage.legend,
                "The Living Myth",
                "You have become the story others tell to find courage",
                "There is a chapter of your life that future generations will speak of — the season you chose courage over comfort, truth over approval, becoming over belonging. You are no longer just living your story; you are the story that helps others find theirs.");

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

    private void seedStageContent(DestinyCard card, CardStage stage,
                                   String title, String tagline, String lore) {
        CardStageContent content = CardStageContent.builder()
                .card(card)
                .stage(stage)
                .title(title)
                .tagline(tagline)
                .lore(lore)
                .imagePrompt(null)
                .generatedAt(Instant.now())
                .build();
        cardStageContentRepository.save(content);
    }

    private static String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
