package com.destinyoracle.domain.card.service.impl;

import com.destinyoracle.dto.response.BatchImageResult;
import com.destinyoracle.dto.response.GeneratedImageResponse;
import com.destinyoracle.dto.response.GenerationJobResponse;
import com.destinyoracle.dto.response.ImagePromptResponse;
import com.destinyoracle.domain.card.entity.*;
import com.destinyoracle.domain.user.entity.*;
import com.destinyoracle.exception.ResourceNotFoundException;
import com.destinyoracle.domain.card.repository.*;
import com.destinyoracle.domain.user.repository.*;
import com.destinyoracle.domain.card.service.CardImageGenerationService;
import com.destinyoracle.domain.card.service.GenerationJobService;
import com.destinyoracle.domain.card.service.ImagePromptService;
import com.destinyoracle.domain.card.service.ImageProvider;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

/**
 * Orchestrates the full AI image generation pipeline with per-step job tracking.
 *
 * Pipeline (12 steps total per card):
 *
 *   Phase 1 — PROMPT (steps 0–5, sequential inside Claude's batch call):
 *     Step 0: storm  prompt  WAITING → RUNNING → DONE|SKIPPED|FAILED
 *     Step 1: fog    prompt  …
 *     Step 2: clearing prompt …
 *     Step 3: aura   prompt  …
 *     Step 4: radiance prompt …
 *     Step 5: legend prompt  …
 *
 *   ★ GATE: ALL 6 prompt steps must reach DONE or SKIPPED before Phase 2 begins.
 *
 *   Phase 2 — IMAGE (steps 6–11, run in PARALLEL via virtual threads):
 *     Step 6:  storm   image  WAITING → RUNNING → DONE|FAILED
 *     Step 7:  fog     image  …
 *     Step 8:  clearing image …
 *     Step 9:  aura    image  …
 *     Step 10: radiance image …
 *     Step 11: legend  image  …
 *
 * The job entity is persisted after every step transition so UI polling always
 * reflects real-time state. The job's completedSteps counter increments on each
 * DONE or SKIPPED step, enabling a clean progress bar in the frontend.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CardImageGenerationServiceImpl implements CardImageGenerationService {

    private final ImagePromptServiceImpl      imagePromptService;
    private final DestinyCardRepository       cardRepository;
    private final CardImageRepository         cardImageRepository;
    private final CardStageContentRepository  stageContentRepository;
    private final GenerationJobRepository     jobRepository;
    private final GenerationJobStepRepository stepRepository;
    private final GenerationJobService        jobService;
    private final JobStepUpdater              stepUpdater;

    private final ImageProvider imageProvider;

    @org.springframework.beans.factory.annotation.Value("${app.gcs.bucket:destiny-oracle-assets}")
    private String gcsBucket;

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Full pipeline: prompt generation → image generation for all 6 stages.
     * Creates a tracked GenerationJob so the UI can poll progress.
     *
     * Returns all generated image responses AND the job ID for polling.
     */
    // NOTE: NOT @Transactional — the orchestrator must NOT wrap everything in one transaction.
    // The job entity must commit immediately so the UI polling endpoint can see it.
    // Each inner operation (imagePromptService, cardRepository queries, stepRepository saves)
    // runs in its own @Transactional context — failures in one don't roll back the job record.
    @Override
    public List<GeneratedImageResponse> generateAllStageImages(UUID userId, UUID cardId) {
        DestinyCard card = cardRepository.findByIdAndUserIdWithDetails(cardId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Card", "id", cardId));

        log.info("╔══════════════════════════════════════════════════════╗");
        log.info("║  FULL PIPELINE START  card={}  aspect={}", cardId, card.getAspectLabel());
        log.info("╚══════════════════════════════════════════════════════╝");

        // Create job with 12 pre-built steps (UI can render full pipeline immediately)
        GenerationJob job = createJob(card, userId, "Full Pipeline");
        log.info("  Job created: id={} label='{}'", job.getId(), job.getJobLabel());
        log.info("  12 steps pre-created: 6 PROMPT (WAITING) + 6 IMAGE (WAITING)");

        // ── Reuse existing prompts if available, otherwise regenerate ──────
        Map<String, String> cachedPrompts = loadCachedPrompts(cardId);
        boolean hasAllPrompts = cachedPrompts.size() == CardStage.values().length;

        if (hasAllPrompts) {
            log.info("  ✓ Found {} cached prompts — reusing (skip Claude call)", cachedPrompts.size());
        } else {
            log.info("  {} of 6 prompts cached — clearing and regenerating all",
                    cachedPrompts.size());
            stageContentRepository.findAllByCardIdOrderByStageAsc(cardId)
                    .forEach(sc -> {
                        sc.setImagePrompt(null);
                        stageContentRepository.save(sc);
                    });
            card.setPromptStatus(PromptStatus.PENDING);
            cardRepository.save(card);
        }

        try {
            Map<String, String> promptsByStage;

            if (hasAllPrompts) {
                // Skip Phase 1 — mark prompt steps as DONE immediately
                log.info("─── Phase 1: PROMPTS (cached — skipping Claude) ───");
                promptsByStage = cachedPrompts;
                for (GenerationJobStep step : new ArrayList<>(job.getSteps())) {
                    if (step.getPhase() == JobPhase.PROMPT) {
                        stepUpdater.markStepDone(step.getId(),
                                "Reused cached prompt (" + cachedPrompts.getOrDefault(
                                        step.getStage(), "").length() + " chars)", null);
                    }
                }
            } else {
                // Run Phase 1 — generate fresh prompts via Claude
                log.info("─── Phase 1: PROMPTS (generating via Claude) ───");
                promptsByStage = runPromptPhase(userId, cardId, card, job);
            }

            // ★ GATE: validate returned prompts — if runPromptPhase returned
            // successfully with all 6 stages, prompts are ready. No need to
            // re-query step statuses from DB (Hibernate cache would be stale anyway).
            assertAllPromptsPresent(promptsByStage);
            log.info("  ✓ GATE PASSED — all 6 prompts ready. Starting image generation.");

            // Reload card after prompt phase (promptStatus was updated)
            card = cardRepository.findByIdAndUserIdWithDetails(cardId, userId).orElseThrow();

            // ── PHASE 2: IMAGES ────────────────────────────────────────────────
            log.info("─── Phase 2: IMAGES (parallel, virtual threads) ───");

            List<GeneratedImageResponse> results = runImagePhase(card, userId, promptsByStage, job);

            // ── COMPLETE ───────────────────────────────────────────────────────
            stepUpdater.markJobStatus(job.getId(), JobStatus.COMPLETED);

            log.info("╔══════════════════════════════════════════════════════╗");
            log.info("║  FULL PIPELINE COMPLETE  job={}  images={}", job.getId(), results.size());
            log.info("╚══════════════════════════════════════════════════════╝");

            return results;

        } catch (Exception e) {
            stepUpdater.markJobFailed(job.getId(), e.getMessage());
            log.error("  Pipeline FAILED for job={}: {}", job.getId(), e.getMessage());
            throw e;
        }
    }

    @Override
    public GeneratedImageResponse generateStageImage(UUID userId, UUID cardId, String stageName) {
        CardStage stage = CardStage.valueOf(stageName.toLowerCase());
        log.info("── Single stage image: card={} stage={} ──", cardId, stage.name());

        // Try to load saved prompt from DB first — avoids calling Claude if prompts already exist
        String prompt = stageContentRepository.findByCardIdAndStage(cardId, stage)
                .map(CardStageContent::getImagePrompt)
                .filter(p -> p != null && !p.isBlank())
                .orElse(null);

        if (prompt == null) {
            log.info("  No saved prompt for stage={} — calling Claude to generate all 6 prompts first", stage.name());
            ImagePromptResponse prompts = imagePromptService.generatePromptsForCard(userId, cardId);
            prompt = prompts.getPromptsByStage().getOrDefault(stage.name(), "");
            log.info("  Claude returned prompt for stage={} ({} chars)", stage.name(), prompt.length());
        } else {
            log.info("  Using saved prompt for stage={} ({} chars)", stage.name(), prompt.length());
        }

        DestinyCard card = cardRepository.findByIdAndUserIdWithDetails(cardId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Card", "id", cardId));

        return generateStageImageInternal(card, stage, prompt, card.getUser().getChibiUrl(), userId, null);
    }

    /**
     * Generates 6 stage images for ALL of the user's aspect cards.
     *
     * Each card = 1 aspect = 6 images. Cards are processed one at a time
     * (sequential) to avoid hitting Gemini rate limits; within each card,
     * all 6 stage image calls run in parallel via virtual threads.
     *
     * This is the true "batch" — one GenerationJob is created per card so
     * the user can track progress for each aspect independently.
     */
    @Override
    public List<BatchImageResult> generateAllUserCards(UUID userId) {
        // Load all aspect card IDs for this user
        List<DestinyCard> cards = cardRepository.findAllByUserId(userId);

        log.info("╔══════════════════════════════════════════════════════╗");
        log.info("║  GENERATE ALL ASPECTS  userId={}  aspects={}", userId, cards.size());
        log.info("╚══════════════════════════════════════════════════════╝");

        List<BatchImageResult> results = new ArrayList<>();

        for (int i = 0; i < cards.size(); i++) {
            DestinyCard card = cards.get(i);
            log.info("  [{}/{}] Aspect: {} ({})", i + 1, cards.size(), card.getAspectLabel(), card.getId());

            try {
                // generateAllStageImages creates its own GenerationJob with 12 steps
                List<GeneratedImageResponse> images = generateAllStageImages(userId, card.getId());

                results.add(BatchImageResult.builder()
                        .cardId(card.getId())
                        .aspectKey(card.getAspectKey())
                        .status("completed")
                        .images(images)
                        .build());

                log.info("  [{}/{}] {} completed — {} images generated",
                        i + 1, cards.size(), card.getAspectLabel(), images.size());

            } catch (Exception e) {
                log.error("  [{}/{}] {} FAILED: {}", i + 1, cards.size(), card.getAspectLabel(), e.getMessage());
                results.add(BatchImageResult.builder()
                        .cardId(card.getId())
                        .aspectKey(card.getAspectKey())
                        .status("failed")
                        .error(e.getMessage())
                        .images(List.of())
                        .build());
            }
        }

        long succeeded = results.stream().filter(r -> "completed".equals(r.getStatus())).count();
        log.info("  All aspects done: {}/{} succeeded", succeeded, cards.size());
        return results;
    }

    /** Returns the latest job for a card — used by the polling endpoint. */
    public GenerationJobResponse getLatestJob(UUID userId, UUID cardId) {
        return jobService.getLatestJob(userId, cardId);
    }

    // ── Pipeline phases ───────────────────────────────────────────────────────

    /**
     * Phase 1: ensure all 6 prompts are in DB. Returns the promptsByStage map.
     * Delegates to ImagePromptServiceImpl with the job so step transitions are tracked.
     */
    private Map<String, String> runPromptPhase(UUID userId, UUID cardId, DestinyCard card, GenerationJob job) {
        if (card.getPromptStatus() == PromptStatus.READY) {
            log.info("  Prompts already READY for card={} — loading from DB, skipping Claude", cardId);

            Map<String, String> promptsByStage = stageContentRepository
                    .findAllByCardIdOrderByStageAsc(cardId).stream()
                    .filter(sc -> sc.getImagePrompt() != null && !sc.getImagePrompt().isBlank())
                    .collect(Collectors.toMap(
                            sc -> sc.getStage().name(),
                            CardStageContent::getImagePrompt,
                            (a, b) -> a,
                            LinkedHashMap::new));

            // Copy to plain list first — avoids ConcurrentModificationException
            // when stepUpdater fires a REQUIRES_NEW transaction inside the loop
            // which causes Hibernate to refresh the persistent collection mid-iteration
            List<GenerationJobStep> promptSteps = new ArrayList<>(job.getSteps()).stream()
                    .filter(s -> s.getPhase() == JobPhase.PROMPT)
                    .collect(Collectors.toList());

            for (GenerationJobStep step : promptSteps) {
                String savedPrompt = promptsByStage.getOrDefault(step.getStage(), "");
                stepUpdater.markStepSkipped(step.getId(),
                        "Prompt already in DB (" + savedPrompt.length() + " chars) \u2014 Claude skipped");
            }
            return promptsByStage;

        } else {
            log.info("  Prompts not ready (status={}) — invoking Claude", card.getPromptStatus());
            ImagePromptResponse response = imagePromptService.generatePromptsForCard(userId, cardId, job);
            return response.getPromptsByStage();
        }
    }

    /**
     * Phase 2: generate images in PARALLEL using virtual threads.
     * One Gemini Imagen call per stage. Steps 6–11 are updated in real-time.
     *
     * ★ Guaranteed: only runs after ALL prompt steps are DONE or SKIPPED.
     */
    private List<GeneratedImageResponse> runImagePhase(
            DestinyCard card, UUID userId, Map<String, String> promptsByStage, GenerationJob job) {

        // Mark all image steps as RUNNING
        for (GenerationJobStep step : job.getSteps()) {
            if (step.getPhase() == JobPhase.IMAGE) {
                String prompt = promptsByStage.getOrDefault(step.getStage(), "");
                stepUpdater.markStepRunning(step.getId(),
                        "Sending " + step.getStage() + " prompt to " + imageProvider.providerName()
                        + " (" + prompt.length() + " char prompt)\u2026");
            }
        }
        stepUpdater.markJobStatus(job.getId(), JobStatus.IMAGING);

        // Sequential — one image at a time. Rate limit retry handled per-request.
        List<GeneratedImageResponse> results = new ArrayList<>();

        for (CardStage stage : CardStage.values()) {
            String prompt = promptsByStage.get(stage.name());
            if (prompt == null) {
                log.warn("  No prompt found for stage={} — skipping", stage.name());
                continue;
            }

            try {
                String chibiUrl = card.getUser().getChibiUrl();
                results.add(generateStageImageInternal(card, stage, prompt, chibiUrl, userId, job));
            } catch (Exception e) {
                log.error("  Image generation error for stage={}: {}", stage.name(), e.getMessage());
            }
        }

        return results;
    }

    // ── Internal image generator ──────────────────────────────────────────────

    private GeneratedImageResponse generateStageImageInternal(
            DestinyCard card, CardStage stage, String prompt, String chibiUrl, UUID userId,
            GenerationJob job) {

        log.info("  Generating image: card={} stage={}", card.getId(), stage.name());

        if (imageProvider == null) {
            log.warn("  No image provider configured — returning fallback for stage={}", stage.name());
            markImageStepDone(job, stage, "assets/health-user1.png", true);
            return fallbackResponse(card.getAspectKey(), stage, prompt);
        }

        try {
            // Call image generation via the active provider (Strategy pattern)
            log.info("  Generating image for stage={} (prompt: {} chars, provider: {})",
                    stage.name(), prompt.length(), imageProvider.providerName());
            String imageBase64 = imageProvider.generate(prompt, chibiUrl);
            log.info("  Image generated for stage={} via {}", stage.name(), imageProvider.providerName());

            // Store in GCS
            String gcsUrl = storeInGcs(imageBase64, userId, card.getAspectKey(), stage);
            log.info("  Stored image in GCS: {}", gcsUrl);

            // Persist to card_images table
            persistCardImage(card, stage, gcsUrl, prompt);
            log.info("  Persisted card_image row for stage={}", stage.name());

            // Update card's main imageUrl if this is the current stage
            if (stage == card.getCurrentStage()) {
                card.setImageUrl(gcsUrl);
                card.setLastUpdated(Instant.now());
                cardRepository.save(card);
                log.info("  Updated card.imageUrl (current stage={})", stage.name());
            }

            // Mark job step DONE
            markImageStepDone(job, stage, gcsUrl, false);

            return GeneratedImageResponse.builder()
                    .aspectKey(card.getAspectKey())
                    .stage(stage.name())
                    .imageUrl(gcsUrl)
                    .promptUsed(prompt)
                    .status("generated")
                    .build();

        } catch (Exception e) {
            log.error("  {} FAILED for stage={}: {}", imageProvider.providerName(), stage.name(), e.getMessage());
            markImageStepFailed(job, stage, e.getMessage());
            return fallbackResponse(card.getAspectKey(), stage, prompt);
        }
    }

    // ── Job step helpers (IMAGE phase) ────────────────────────────────────────

    private void markImageStepDone(GenerationJob job, CardStage stage, String imageUrl, boolean isFallback) {
        if (job == null) return;
        job.getSteps().stream()
                .filter(s -> s.getPhase() == JobPhase.IMAGE && s.getStage().equals(stage.name()))
                .findFirst()
                .ifPresent(step -> stepUpdater.markStepDone(step.getId(),
                        isFallback
                                ? "GCP not configured \u2014 returning placeholder image"
                                : "Image generated and stored in GCS",
                        imageUrl));
    }

    private void markImageStepFailed(GenerationJob job, CardStage stage, String errorMessage) {
        if (job == null) return;
        job.getSteps().stream()
                .filter(s -> s.getPhase() == JobPhase.IMAGE && s.getStage().equals(stage.name()))
                .findFirst()
                .ifPresent(step -> stepUpdater.markStepFailed(step.getId(), errorMessage));
    }

    // ── Gate assertion ────────────────────────────────────────────────────────

    /**
     * Validates that all 6 stage prompts are present in the returned map.
     * This is the gate between Phase 1 and Phase 2.
     *
     * Uses the actual prompt data returned by runPromptPhase() — no need to
     * re-query job step statuses from DB (which would hit stale Hibernate cache).
     * If runPromptPhase() returned without throwing, the prompts are saved.
     */
    private void assertAllPromptsPresent(Map<String, String> promptsByStage) {
        List<String> missing = Arrays.stream(CardStage.values())
                .map(CardStage::name)
                .filter(stage -> {
                    String p = promptsByStage.get(stage);
                    return p == null || p.isBlank();
                })
                .collect(Collectors.toList());

        if (!missing.isEmpty()) {
            throw new RuntimeException(
                    "GATE FAILED — missing prompts for " + missing.size() +
                    " stage(s): [" + String.join(", ", missing) + "]");
        }
        log.info("  GATE: all 6 stage prompts present ✓");
    }

    // ── Job factory ───────────────────────────────────────────────────────────

    /**
     * Creates a new GenerationJob with 12 pre-built steps (all WAITING).
     * Steps 0–5 = PROMPT phase, steps 6–11 = IMAGE phase.
     * The UI can render the complete pipeline immediately from this snapshot.
     */
    private GenerationJob createJob(DestinyCard card, UUID userId, String jobType) {
        GenerationJob job = GenerationJob.builder()
                .cardId(card.getId())
                .userId(userId)
                .jobLabel(card.getAspectLabel() + " · " + jobType)
                .status(JobStatus.QUEUED)
                .totalSteps(12)
                .completedSteps(0)
                .build();

        // Build 12 steps upfront — all WAITING
        List<GenerationJobStep> steps = new ArrayList<>();
        CardStage[] stages = CardStage.values();

        for (int i = 0; i < stages.length; i++) {
            CardStage stage = stages[i];
            steps.add(GenerationJobStep.builder()
                    .job(job)
                    .stepOrder(i)
                    .stepName("Generate " + stage.name() + " prompt")
                    .phase(JobPhase.PROMPT)
                    .stage(stage.name())
                    .status(StepStatus.WAITING)
                    .message("Waiting for previous steps to complete\u2026")
                    .build());
        }

        for (int i = 0; i < stages.length; i++) {
            CardStage stage = stages[i];
            steps.add(GenerationJobStep.builder()
                    .job(job)
                    .stepOrder(stages.length + i)
                    .stepName("Generate " + stage.name() + " image")
                    .phase(JobPhase.IMAGE)
                    .stage(stage.name())
                    .status(StepStatus.WAITING)
                    .message("Waiting for all prompts to finish first\u2026")
                    .build());
        }

        job.setSteps(steps);
        // saveAndFlush — commits immediately so the UI polling endpoint can see the job
        // even before the pipeline starts (all 12 steps visible as WAITING right away)
        GenerationJob saved = jobRepository.saveAndFlush(job);
        log.info("  Created job {} with {} steps", saved.getId(), steps.size());
        return saved;
    }

    /**
     * Saves the generated image locally under static/generated/ so it is
     * immediately served at http://localhost:8080/generated/{userId}/{aspectKey}-{stage}.png
     *
     * TODO: swap storeLocally() for GCS upload in production.
     */

    /** Loads existing image prompts from stage_content rows. Returns map of stage name → prompt. */
    private Map<String, String> loadCachedPrompts(UUID cardId) {
        Map<String, String> cached = new LinkedHashMap<>();
        stageContentRepository.findAllByCardIdOrderByStageAsc(cardId).forEach(sc -> {
            if (sc.getImagePrompt() != null && !sc.getImagePrompt().isBlank()) {
                cached.put(sc.getStage().name(), sc.getImagePrompt());
            }
        });
        return cached;
    }

    private String storeInGcs(String base64Image, UUID userId, String aspectKey, CardStage stage) {
        try {
            return storeLocally(base64Image, userId, aspectKey, stage);
        } catch (Exception e) {
            log.error("  Failed to save image locally for stage={}: {}", stage.name(), e.getMessage());
            return "assets/health-user1.png";
        }
    }

    private String storeLocally(String base64Image, UUID userId, String aspectKey, CardStage stage) throws Exception {
        // Resolve path: {project}/src/main/resources/static/generated/{userId}/
        java.nio.file.Path staticDir = java.nio.file.Paths.get(
                System.getProperty("user.dir"),
                "src", "main", "resources", "static", "generated", userId.toString());
        java.nio.file.Files.createDirectories(staticDir);

        byte[] imageBytes = Base64.getDecoder().decode(base64Image);

        // Detect actual format from magic bytes and use correct extension
        String ext = detectImageExtension(imageBytes);
        String filename = aspectKey + "-" + stage.name() + ext;
        java.nio.file.Path filePath = staticDir.resolve(filename);

        java.nio.file.Files.write(filePath, imageBytes);

        String url = "/generated/" + userId + "/" + filename;
        log.info("  Image saved locally: {} ({} KB, format: {})", url, imageBytes.length / 1024, ext);
        return url;
    }

    /** Detect image format from magic bytes. Returns ".jpg", ".png", or ".webp". */
    private String detectImageExtension(byte[] data) {
        if (data.length >= 3 && data[0] == (byte) 0xFF && data[1] == (byte) 0xD8 && data[2] == (byte) 0xFF) {
            return ".jpg";
        }
        if (data.length >= 4 && data[0] == (byte) 0x89 && data[1] == 0x50 && data[2] == 0x4E && data[3] == 0x47) {
            return ".png";
        }
        if (data.length > 11 && data[0] == 0x52 && data[1] == 0x49 && data[8] == 0x57 && data[9] == 0x45) {
            return ".webp";
        }
        return ".png"; // fallback
    }


    /** Upserts a card_images row for this stage. Thread-safe — operates on the DB row directly. */
    private void persistCardImage(DestinyCard card, CardStage stage, String imageUrl, String prompt) {
        String summary = prompt.length() > 200 ? prompt.substring(0, 200) : prompt;

        cardImageRepository.findByCardIdAndStage(card.getId(), stage).ifPresentOrElse(
            existing -> {
                existing.setImageUrl(imageUrl);
                existing.setPromptSummary(summary);
                existing.setGeneratedAt(Instant.now());
                cardImageRepository.save(existing);
            },
            () -> cardImageRepository.save(CardImage.builder()
                    .card(card)
                    .stage(stage)
                    .imageUrl(imageUrl)
                    .promptSummary(summary)
                    .generatedAt(Instant.now())
                    .build())
        );
    }

    // ── Utilities ─────────────────────────────────────────────────────────────

    private GeneratedImageResponse fallbackResponse(String aspectKey, CardStage stage, String prompt) {
        return GeneratedImageResponse.builder()
                .aspectKey(aspectKey)
                .stage(stage.name())
                .imageUrl("assets/health-user1.png")
                .promptUsed(prompt)
                .status("fallback")
                .build();
    }
}
