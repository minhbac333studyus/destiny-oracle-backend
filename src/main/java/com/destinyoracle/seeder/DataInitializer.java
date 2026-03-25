package com.destinyoracle.seeder;

import com.destinyoracle.config.AppProperties;
import com.destinyoracle.entity.*;
import com.destinyoracle.repository.*;
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
    private final HabitRepository            habitRepository;
    private final GoalRepository             goalRepository;
    private final MilestoneRepository        milestoneRepository;
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
        seedGoals(userId);
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
                List.of(
                    stage(CardStage.storm, "The Frail Vessel", "A body ignored becomes a cage",
                          "The storm rages inside — joints ache, energy fades. But within the silence of neglect, a spark waits.")
                ),
                List.of("Moved my body today|daily|3", "Ate nourishing food|daily|1"));

        seedCard(user, "career",        "Career & Purpose",       "🎖️", 2,
                "Still doing the same job, same title, watching people I once mentored pass me by.",
                CardStage.fog, 58, 34, 14, 8, 42,
                List.of(
                    stage(CardStage.storm, "The Invisible Worker", "Talent buried under obligation",
                          "Years pass. The desk stays the same. The name goes unspoken. The dream collects dust on the shelf."),
                    stage(CardStage.fog,   "The Uncertain Path",   "Direction blurs, but the feet still move",
                          "The old certainties dissolve. A quiet ambition stirs beneath the surface — unfamiliar, but unmistakably yours.")
                ),
                List.of("Worked on my craft for 30 min|daily|8", "One meaningful connection made|weekly|2"));

        seedCard(user, "finances",      "Finances",               "💴",  3,
                "No savings, maxed credit cards, unable to help my family when they need it.",
                CardStage.clearing, 42, 78, 30, 18, 45,
                List.of(
                    stage(CardStage.storm,    "The Empty Ledger",    "Every number is a wound",
                          "Numbers spiral into red. The weight of each statement disrupts sleep. The hole feels bottomless."),
                    stage(CardStage.fog,      "The Clouded Count",   "The damage becomes visible — and so does the way",
                          "The chaos slows enough to see patterns. The scale is frightening. But knowing is better than not knowing."),
                    stage(CardStage.clearing, "The Rising Fortune",  "Every coin saved is a step toward freedom",
                          "The numbers start to make sense. Clarity replaces chaos. A future takes shape in the ledger.")
                ),
                List.of("Reviewed my spending today|daily|0"));

        seedCard(user, "relationships", "Relationships",          "🌸",  4,
                "Growing old with no one who truly knows me. Surface-level friendships and a hollow phone.",
                CardStage.clearing, 70, 62, 21, 14, 91,
                List.of(
                    stage(CardStage.storm,    "The Hollow Circle",  "Surrounded yet utterly alone",
                          "The phone fills with names. None of them know you. Presence without intimacy is its own kind of grief."),
                    stage(CardStage.fog,      "The Fading Thread",  "The desire for connection stirs in the silence",
                          "Old faces surface in memory. The impulse to reach out grows louder. First steps feel clumsy — but they are steps."),
                    stage(CardStage.clearing, "The Lone Figure",    "Connection fades when not tended",
                          "Missed calls. Unopened messages. A life full of acquaintances and empty of depth.")
                ),
                List.of("Reached out to someone I care about|daily|14", "Was fully present in a conversation|weekly|6"));

        seedCard(user, "family",        "Family",                 "🏯",  5,
                "My kids will remember me as the parent who was always on the phone, never really there.",
                CardStage.aura, 60, 156, 60, 45, 88,
                List.of(
                    stage(CardStage.storm,    "The Absent Anchor",  "A home is just walls without presence",
                          "The family table is full but the heart is empty. Screens replace eyes. Time is always later."),
                    stage(CardStage.fog,      "The Trying Hand",    "Love shows up imperfectly but it shows up",
                          "Old habits loosen their grip. Small efforts accumulate. The family notices — even if nobody says it yet."),
                    stage(CardStage.clearing, "The Present One",    "Being here is the whole gift",
                          "Screens down. Eyes that actually meet. Conversations that run past bedtime. The house starts becoming a home."),
                    stage(CardStage.aura,     "The Guardian Spirit","Your presence is their greatest treasure",
                          "The table is full. The laughter is real. Every moment savored becomes a memory that lasts generations.")
                ),
                List.of("Quality time with family today|daily|2"));
        // ── Remaining 5 aspects (learning, creativity, spirituality, lifestyle, legacy)
        // are NOT seeded — users unlock them via POST /api/v1/cards
    }

    private void seedCard(AppUser user,
                          String aspectKey, String aspectLabel, String icon, int sortOrder,
                          String fear,
                          CardStage currentStage, int progress,
                          int totalCheckIns, int longestStreak, int currentStreak, int daysAtStage,
                          List<StageContentDef> stageContents,
                          List<String> habitDefs) {

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

        // Stage content
        for (StageContentDef def : stageContents) {
            CardStageContent content = CardStageContent.builder()
                    .card(card)
                    .stage(def.stage())
                    .title(def.title())
                    .tagline(def.tagline())
                    .lore(def.lore())
                    .build();
            card.getStageContents().add(content);
        }

        // Habits
        for (String habitDef : habitDefs) {
            String[] parts = habitDef.split("\\|");
            Habit habit = Habit.builder()
                    .card(card)
                    .text(parts[0])
                    .frequency(parts[1])
                    .streakDays(Integer.parseInt(parts[2]))
                    .build();
            card.getHabits().add(habit);
        }

        cardRepository.save(card);
    }

    // ── Goals ─────────────────────────────────────────────────────────────────

    private void seedGoals(UUID userId) {
        Goal healthGoal = Goal.builder()
                .userId(userId)
                .aspectKey("health")
                .aspectLabel("Health & Body")
                .title("Run a 5K")
                .status("active")
                .build();
        healthGoal = goalRepository.save(healthGoal);

        Goal finalHealthGoal = healthGoal;
        List<Milestone> healthMilestones = List.of(
                milestone(finalHealthGoal, "Run 1km without stopping", "achieved"),
                milestone(finalHealthGoal, "Run 3km in one session",   "pending"),
                milestone(finalHealthGoal, "Complete a 5K race",       "pending")
        );
        milestoneRepository.saveAll(healthMilestones);

        Goal creativeGoal = Goal.builder()
                .userId(userId)
                .aspectKey("creativity")
                .aspectLabel("Creativity")
                .title("Finish my first album")
                .status("active")
                .build();
        creativeGoal = goalRepository.save(creativeGoal);

        Goal finalCreativeGoal = creativeGoal;
        List<Milestone> creativeMilestones = List.of(
                milestone(finalCreativeGoal, "Write 5 song drafts",         "achieved"),
                milestone(finalCreativeGoal, "Record first demo track",     "pending"),
                milestone(finalCreativeGoal, "Mix and master 3 songs",      "pending")
        );
        milestoneRepository.saveAll(creativeMilestones);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private StageContentDef stage(CardStage stage, String title, String tagline, String lore) {
        return new StageContentDef(stage, title, tagline, lore);
    }

    private Milestone milestone(Goal goal, String text, String status) {
        Milestone m = Milestone.builder()
                .goal(goal)
                .text(text)
                .status(status)
                .achievedAt("achieved".equals(status) ? Instant.now() : null)
                .build();
        return m;
    }

    private record StageContentDef(CardStage stage, String title, String tagline, String lore) {}
}
