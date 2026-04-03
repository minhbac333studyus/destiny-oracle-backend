package com.destinyoracle.seeder;

import com.destinyoracle.config.AppProperties;
import com.destinyoracle.domain.card.entity.*;
import com.destinyoracle.domain.user.entity.*;
import com.destinyoracle.domain.card.repository.*;
import com.destinyoracle.domain.user.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Seeds the demo user and their 10 destiny cards on first startup.
 * Safe to re-run — skips if data already exists.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DataInitializer implements CommandLineRunner {

    private final AppProperties              appProperties;
    private final UserRepository             userRepository;
    private final DestinyCardRepository      cardRepository;
    private final AspectDefinitionRepository aspectDefinitionRepository;

    private static final String PLACEHOLDER_IMAGE = "assets/health-user1.png";

    @Override
    @Transactional
    public void run(String... args) {
        // Always seed aspect definitions first (idempotent)
        if (aspectDefinitionRepository.count() == 0) {
            seedAspectDefinitions();
        }

        UUID userId = appProperties.getDefaultUserId();
        if (userRepository.existsById(userId)) {
            log.info("Seed data already present — skipping user/card seed.");
            return;
        }

        log.info("Seeding demo user and 5 starter destiny cards...");
        AppUser user = seedUser(userId);
        seedCards(user);

        log.info("Seed complete.");
    }

    // ── Aspect Definitions ────────────────────────────────────────────────────

    private void seedAspectDefinitions() {
        log.info("Seeding aspect_definitions table...");
        List<AspectDefinition> aspects = List.of(
            AspectDefinition.builder().aspectKey("health")        .label("Health & Body")          .icon("⚔️")  .sortOrder(1).build(),
            AspectDefinition.builder().aspectKey("career")        .label("Career & Purpose")       .icon("🎖️") .sortOrder(2).build(),
            AspectDefinition.builder().aspectKey("finances")      .label("Finances")               .icon("💴")  .sortOrder(3).build(),
            AspectDefinition.builder().aspectKey("relationships") .label("Relationships")          .icon("🌸")  .sortOrder(4).build(),
            AspectDefinition.builder().aspectKey("family")        .label("Family")                 .icon("🏯")  .sortOrder(5).build(),
            AspectDefinition.builder().aspectKey("learning")      .label("Mind & Learning")        .icon("📜")  .sortOrder(6).build(),
            AspectDefinition.builder().aspectKey("creativity")    .label("Creativity")             .icon("🎴")  .sortOrder(7).build(),
            AspectDefinition.builder().aspectKey("spirituality")  .label("Spirituality / Meaning") .icon("⛩️")  .sortOrder(8).build(),
            AspectDefinition.builder().aspectKey("lifestyle")     .label("Lifestyle & Freedom")    .icon("🍃")  .sortOrder(9).build(),
            AspectDefinition.builder().aspectKey("legacy")        .label("Legacy & Impact")        .icon("⚜️")  .sortOrder(10).build()
        );
        aspectDefinitionRepository.saveAll(aspects);
        log.info("Seeded {} aspect definitions.", aspects.size());
    }

    // ── User ─────────────────────────────────────────────────────────────────

    private AppUser seedUser(UUID userId) {
        AppUser user = AppUser.builder()
                .id(userId)
                .email("demo@destinyoracle.app")
                .displayName("Oracle Seeker")
                .onboardingComplete(true)
                .joinedAt(Instant.now())
                .build();
        return userRepository.save(user);
    }

    // ── Cards ─────────────────────────────────────────────────────────────────

    private void seedCards(AppUser user) {
        // ── Starter set: 5 aspects ──────────────────────────────────────────
        // Users can add the remaining 5 built-in aspects (or custom ones)
        // at any time via POST /api/v1/cards
        seedCard(user, "health",        "Health & Body",          "⚔️",  1,
                "In 10 years I'll be overweight, exhausted, and dependent on medication.",
                CardStage.storm, 85, 24, 12, 8, 26,
                List.of(stage(CardStage.storm)));

        seedCard(user, "career",        "Career & Purpose",       "🎖️", 2,
                "Still doing the same job, same title, watching people I once mentored pass me by.",
                CardStage.fog, 58, 34, 14, 8, 42,
                List.of(stage(CardStage.storm), stage(CardStage.fog)));

        seedCard(user, "finances",      "Finances",               "💴",  3,
                "No savings, maxed credit cards, unable to help my family when they need it.",
                CardStage.clearing, 42, 78, 30, 18, 45,
                List.of(stage(CardStage.storm), stage(CardStage.fog), stage(CardStage.clearing)));

        seedCard(user, "relationships", "Relationships",          "🌸",  4,
                "Growing old with no one who truly knows me. Surface-level friendships and a hollow phone.",
                CardStage.clearing, 70, 62, 21, 14, 91,
                List.of(stage(CardStage.storm), stage(CardStage.fog), stage(CardStage.clearing)));

        seedCard(user, "family",        "Family",                 "🏯",  5,
                "My kids will remember me as the parent who was always on the phone, never really there.",
                CardStage.aura, 60, 156, 60, 45, 88,
                List.of(stage(CardStage.storm), stage(CardStage.fog), stage(CardStage.clearing), stage(CardStage.aura)));
        // ── Remaining 5 aspects (learning, creativity, spirituality, lifestyle, legacy)
        // are NOT seeded — users unlock them via POST /api/v1/cards
    }

    private void seedCard(AppUser user,
                          String aspectKey, String aspectLabel, String icon, int sortOrder,
                          String fear,
                          CardStage currentStage, int progress,
                          int totalCheckIns, int longestStreak, int currentStreak, int daysAtStage,
                          List<StageContentDef> stageContents) {

        DestinyCard card = DestinyCard.builder()
                .user(user)
                .aspectKey(aspectKey)
                .aspectLabel(aspectLabel)
                .aspectIcon(icon)
                .sortOrder(sortOrder)
                .fearOriginal(fear)
                .currentStage(currentStage)
                .stageProgressPercent(progress)
                .totalCheckIns(totalCheckIns)
                .longestStreak(longestStreak)
                .currentStreak(currentStreak)
                .daysAtCurrentStage(daysAtStage)
                .imageUrl(PLACEHOLDER_IMAGE)
                .lastUpdated(Instant.now())
                .build();

        card = cardRepository.save(card);

        // Stage content (action scenes are generated by AI — seed only creates empty placeholders)
        for (StageContentDef def : stageContents) {
            CardStageContent content = CardStageContent.builder()
                    .card(card)
                    .stage(def.stage())
                    .build();
            card.getStageContents().add(content);
        }

        cardRepository.save(card);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private StageContentDef stage(CardStage stage) {
        return new StageContentDef(stage);
    }

    private record StageContentDef(CardStage stage) {}
}
