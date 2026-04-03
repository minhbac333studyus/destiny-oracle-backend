package com.destinyoracle.domain.card.service.impl;

import com.destinyoracle.dto.response.ImagePromptResponse;
import com.destinyoracle.domain.card.entity.*;
import com.destinyoracle.domain.user.entity.*;
import com.destinyoracle.exception.ResourceNotFoundException;
import com.destinyoracle.domain.card.repository.CardStageContentRepository;
import com.destinyoracle.domain.card.repository.DestinyCardRepository;
import com.destinyoracle.domain.card.repository.GenerationJobRepository;
import com.destinyoracle.domain.card.repository.GenerationJobStepRepository;
import com.destinyoracle.domain.card.service.ImagePromptService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
public class ImagePromptServiceImpl implements ImagePromptService {

    private final DestinyCardRepository       cardRepository;
    private final CardStageContentRepository  stageContentRepository;
    private final GenerationJobRepository     jobRepository;
    private final GenerationJobStepRepository stepRepository;
    private final JobStepUpdater              stepUpdater;
    private final ChatClient.Builder          chatClientBuilder;

    public ImagePromptServiceImpl(
        DestinyCardRepository cardRepository,
        CardStageContentRepository stageContentRepository,
        GenerationJobRepository jobRepository,
        GenerationJobStepRepository stepRepository,
        JobStepUpdater stepUpdater,
        @Qualifier("anthropicChatClient") ChatClient.Builder chatClientBuilder
    ) {
        this.cardRepository = cardRepository;
        this.stageContentRepository = stageContentRepository;
        this.jobRepository = jobRepository;
        this.stepRepository = stepRepository;
        this.stepUpdater = stepUpdater;
        this.chatClientBuilder = chatClientBuilder;
    }

    // ── Stage visual themes — baked into every prompt ────────────────────────
    private static final Map<CardStage, String> STAGE_MOODS = new LinkedHashMap<>();
    static {
        STAGE_MOODS.put(CardStage.storm,
            "dark stormy atmosphere, heavy rain, dramatic lightning, dark blues and greys, " +
            "oppressive shadows, emotional weight, cinematic gloom");
        STAGE_MOODS.put(CardStage.fog,
            "thick pale mist, soft diffused light barely breaking through, muted lavender and silver tones, " +
            "uncertainty, quiet reflection, dreamlike ambiguity");
        STAGE_MOODS.put(CardStage.clearing,
            "golden sunlight breaking through clouds, warm amber rays, dust motes floating in light, " +
            "hopeful atmosphere, first signs of clarity, soft glow on the horizon");
        STAGE_MOODS.put(CardStage.aura,
            "ethereal energy field surrounding the character, soft purple and blue aura glow, " +
            "magical atmosphere, inner power visible, serene confidence, mystical light");
        STAGE_MOODS.put(CardStage.radiance,
            "brilliant golden radiance, warm sparks and embers floating upward, blazing confidence, " +
            "vibrant warm tones, glowing aura, the character is luminous and magnetic");
        STAGE_MOODS.put(CardStage.legend,
            "sakura petals falling, transcendent golden-pink light, legendary aura, " +
            "celestial atmosphere, the character stands as a timeless icon, epic and serene");
    }


    // ── Public API ────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public ImagePromptResponse generatePromptsForCard(UUID userId, UUID cardId) {
        return generatePromptsForCard(userId, cardId, null);
    }

    /**
     * Generates all 6 stage prompts via Claude and persists them to card_stage_content.
     *
     * If a job is provided (from the orchestrator), each step is updated in real-time
     * so the UI polling endpoint always reflects the current state.
     *
     * Sequence enforced by the job system:
     *   Step 0–5: PROMPT phase — one step per CardStage, run sequentially inside Claude's batch call.
     *             Each step transitions WAITING → RUNNING → DONE|SKIPPED|FAILED.
     *   Steps 6–11 (IMAGE phase) remain WAITING until this method returns.
     */
    @Transactional
    public ImagePromptResponse generatePromptsForCard(UUID userId, UUID cardId, GenerationJob job) {
        DestinyCard card = cardRepository.findByIdAndUserIdWithDetails(cardId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Card", "id", cardId));

        log.info("━━━ [PROMPT PHASE] Starting for card={} aspect={} ━━━", cardId, card.getAspectLabel());

        // Always clear existing prompts so Claude regenerates fresh every time
        stageContentRepository.findAllByCardIdOrderByStageAsc(cardId)
                .forEach(sc -> { sc.setImagePrompt(null); stageContentRepository.save(sc); });
        log.info("  Cleared cached image prompts — Claude will generate fresh");

        // Mark card as GENERATING — concurrent requests see this and won't double-fire
        card.setPromptStatus(PromptStatus.GENERATING);
        cardRepository.save(card);
        log.info("  PromptStatus → GENERATING for card={}", cardId);

        // Update job status if we're inside a tracked pipeline (REQUIRES_NEW — commits immediately)
        if (job != null) {
            stepUpdater.markJobStatus(job.getId(), JobStatus.PROMPTING);
        }

        try {
            Map<CardStage, CardStageContent> contentByStage = new LinkedHashMap<>();
            card.getStageContents().forEach(sc -> contentByStage.put(sc.getStage(), sc));

            String aspectLabel = card.getAspectLabel();
            String fearText    = card.getFearOriginal();

            // ── Check which stages already have prompts saved (allow skip) ──────────
            Map<CardStage, String> existingPrompts = new LinkedHashMap<>();
            for (CardStage stage : CardStage.values()) {
                stageContentRepository.findByCardIdAndStage(cardId, stage)
                        .map(CardStageContent::getImagePrompt)
                        .filter(p -> p != null && !p.isBlank())
                        .ifPresent(p -> existingPrompts.put(stage, p));
            }

            boolean allExist = existingPrompts.size() == CardStage.values().length;

            Map<String, String> promptsByStage;
            com.destinyoracle.dto.response.TokenUsageResponse tokenUsage = null;

            if (allExist) {
                // All 6 prompts already in DB — skip Claude entirely, mark steps SKIPPED
                log.info("  All 6 prompts already in DB — skipping Claude call (saved cost)");
                promptsByStage = new LinkedHashMap<>();
                for (CardStage stage : CardStage.values()) {
                    promptsByStage.put(stage.name(), existingPrompts.get(stage));
                }
                markPromptStepsSkipped(job, card.getAspectLabel());

            } else {
                // Need Claude — build meta-prompt and call AI
                log.info("  Calling Claude for {} prompts (aspect: {})", CardStage.values().length, aspectLabel);

                // Mark all PROMPT steps as RUNNING before the call
                markPromptStepsRunning(job, aspectLabel);

                String metaPrompt = buildMetaPrompt(aspectLabel, fearText, contentByStage);
                log.debug(" Meta-prompt built ({} chars", metaPrompt);
                log.info("  Meta-prompt built ({} chars) — sending to Claude…", metaPrompt.length());

                org.springframework.ai.chat.model.ChatResponse chatResponse = chatClientBuilder.build()
                        .prompt()
                        .user(metaPrompt)
                        .call()
                        .chatResponse();

                String aiResponse = chatResponse == null ? null : chatResponse.getResult().getOutput().getText();
                tokenUsage = buildTokenUsage(chatResponse, "Image Prompts");
                log.info("  Claude responded ({} chars) — parsing stage prompts…", aiResponse == null ? 0 : aiResponse.length());
                promptsByStage = parseAiResponse(aiResponse);
                log.info("  Parsed {} stage prompts from Claude response", promptsByStage.size());

                // Fill any missing stages with a sensible fallback
                for (CardStage stage : CardStage.values()) {
                    if (!promptsByStage.containsKey(stage.name())) {
                        log.warn("  Claude did not return prompt for stage={} — using fallback", stage.name());
                        promptsByStage.put(stage.name(), buildFallbackPrompt(aspectLabel, stage));
                    }
                }
            }

            // ── Persist each prompt to card_stage_content.image_prompt ──────────────
            log.info("  Persisting {} prompts to card_stage_content…", promptsByStage.size());
            for (CardStage stage : CardStage.values()) {
                String prompt = promptsByStage.get(stage.name());
                if (prompt == null) continue;

                Optional<CardStageContent> existing = stageContentRepository.findByCardIdAndStage(cardId, stage);
                if (existing.isPresent()) {
                    existing.get().setImagePrompt(prompt);
                    stageContentRepository.save(existing.get());
                    log.debug("Updated image_prompt for stage={} ({} chars)", stage.name(), prompt.length());
                } else {
                    stageContentRepository.save(CardStageContent.builder()
                            .card(card).stage(stage)
                            .imagePrompt(prompt)
                            .build());
                    log.debug("    Created new stage content row for stage={}", stage.name());
                }

                // Mark individual step DONE after saving each prompt
                markPromptStepDone(job, stage, prompt);
            }

            // ── Finalise card prompt status ──────────────────────────────────────────
            card.setPromptStatus(PromptStatus.READY);
            cardRepository.save(card);
            log.info("  PromptStatus → READY for card={}", cardId);
            log.info("━━━ [PROMPT PHASE] Complete — all 6 prompts saved ━━━");

            return ImagePromptResponse.builder()
                    .aspectKey(card.getAspectKey())
                    .aspectLabel(aspectLabel)
                    .promptsByStage(promptsByStage)
                    .tokenUsage(tokenUsage)
                    .build();

        } catch (Exception e) {
            card.setPromptStatus(PromptStatus.FAILED);
            cardRepository.save(card);
            log.error("━━━ [PROMPT PHASE] FAILED for card={} ━━━ error: {}", cardId, e.getMessage());

            // Mark all still-RUNNING or WAITING prompt steps as FAILED
            markPromptStepsFailed(job, e.getMessage());

            throw e;
        }
    }

    // ── Job step helpers ──────────────────────────────────────────────────────

    // ── Job step helpers — all delegate to JobStepUpdater (REQUIRES_NEW transactions) ──

    private void markPromptStepsRunning(GenerationJob job, String aspectLabel) {
        if (job == null) return;
        for (GenerationJobStep step : job.getSteps()) {
            if (step.getPhase() == JobPhase.PROMPT) {
                stepUpdater.markStepRunning(step.getId(),
                        "Calling Claude to write the " + step.getStage() + " stage prompt for " + aspectLabel + "\u2026");
            }
        }
    }

    private void markPromptStepsSkipped(GenerationJob job, String aspectLabel) {
        if (job == null) return;
        for (GenerationJobStep step : job.getSteps()) {
            if (step.getPhase() == JobPhase.PROMPT) {
                stepUpdater.markStepSkipped(step.getId(),
                        "Prompt already saved in DB from previous run \u2014 skipping Claude to save cost");
            }
        }
    }

    private void markPromptStepDone(GenerationJob job, CardStage stage, String prompt) {
        if (job == null) return;
        job.getSteps().stream()
                .filter(s -> s.getPhase() == JobPhase.PROMPT && s.getStage().equals(stage.name()))
                .findFirst()
                .ifPresent(step -> stepUpdater.markStepDone(step.getId(),
                        "Prompt ready \u2014 " + prompt.length() + " characters", null));
    }

    private void markPromptStepsFailed(GenerationJob job, String errorMessage) {
        if (job == null) return;
        for (GenerationJobStep step : job.getSteps()) {
            if (step.getPhase() == JobPhase.PROMPT
                    && (step.getStatus() == StepStatus.RUNNING || step.getStatus() == StepStatus.WAITING)) {
                stepUpdater.markStepFailed(step.getId(), errorMessage);
            }
        }
    }

    // ── Meta-prompt construction ───────────────────────────────────────────────
    private String buildMetaPrompt(String aspectLabel,
                                   String fearText,
                                   Map<CardStage, CardStageContent> contentByStage) {
        StringBuilder sb = new StringBuilder();

        sb.append("""
        You are a master visual storyteller.
        Create 6 image prompts   showing one person's transformation journey.
        ART STYLE (every prompt): 
        - Color palette shifts: cold/desaturated (Storm) → warm/vibrant (Legend) 
        """);

        sb.append("LIFE ASPECT: ").append(aspectLabel).append("\n");
        sb.append("USER'S DEEPEST FEAR: ").append(fearText).append("\n\n");

        sb.append("""
        CROWD RULE: For every stage, derive crowd/background figure behavior directly from the FEAR text above.
        Show what those people would be doing if the fear were true (early stages) or disproven (later stages).
        Never use generic examples — always specific to THIS user's fear. 
        """);

        // ── Per-stage direction (action scene + visual spec together) ──
        sb.append("STAGE DIRECTION — use the action as emotional context, then follow the visual spec:\n\n");

        Map<CardStage, String[]> stageSpecs = buildStageSpecs();

        for (CardStage stage : CardStage.values()) {
            CardStageContent content = contentByStage.get(stage);
            String[] spec = stageSpecs.get(stage);

            sb.append("── ").append(stage.name().toUpperCase()).append(" ──\n");

            sb.append("Mood: ").append(STAGE_MOODS.get(stage)).append("\n");
            sb.append("Pose: ").append(spec[0]).append("\n");
            sb.append("Outfit: ").append(spec[1]).append("\n");
            sb.append("Crowd: ").append(spec[2]).append("\n");
            sb.append("Scene: ").append(spec[3]).append("\n\n");
        }

        sb.append("""
        PROMPT RULES:
        1. Start every prompt with "anime character"
        2. The "Action" line for each stage is the MOST IMPORTANT input.
           Specify physical activity.
        3. Write specific colors, objects, poses,light sources, spatial positions. NO abstract emotions ("cautious optimism",
           "emerging hope"). Instead: "eyes wide, slight smile, chin raised."
        3. Describe the exact COLOR of every element — hair, outfit, skin lighting, sky,
           ground, particles.
        4. Background figures: describe their EXACT pose and position relative to the
           main character.  
        5. End every prompt with: "full bleed digital illustration, no borders, no frames,
           highly detailed, digital art, 1k, 9:16, gold and blue glitter around character
           and background to boost the contrast"
        6. NO text, words, letters, numbers, watermarks in the image.

        FORMAT — respond with EXACTLY (no other text):
        STORM: <prompt>
        FOG: <prompt>
        CLEARING: <prompt>
        AURA: <prompt>
        RADIANCE: <prompt>
        LEGEND: <prompt>
        """);

        return sb.toString();
    }
    private Map<CardStage, String[]> buildStageSpecs() {
        // [pose, outfit, crowd, scene]
        // Crowd directions kept concise — the shared CROWD RULE in buildMetaPrompt()
        // tells Claude to always derive crowd behavior from the fear text.
        Map<CardStage, String[]> specs = new LinkedHashMap<>();

        specs.put(CardStage.storm, new String[]{
                "Curled up, knees to chest, head bowed, fists clenched around a symbolic object of the fear. "
                        + "Eyes shut or staring down. Body small, tight, shoulders hunched.",

                "Worn dark oversized hoodie in dull charcoal and faded black, frayed edges. "
                        + "Scuffed shoes, no accessories. Everything borrowed or outgrown.",

                "2-3 figures embodying the fear through actions toward the character. "
                        + "Derive what they'd do if the fear were true — backs turned, looming, walking past, climbing away.",

                "Dark stormy sky, rain streaks, cold blue-purple lighting, cracked ground, no shelter. "
                        + "ONE symbolic ground object making the fear visible without text."
        });

        specs.put(CardStage.fog, new String[]{
                "Standing still, half-turned, one arm reaching into empty space, "
                        + "other clutching a fear-related object. Wide uncertain eyes, neither retreating nor advancing.",

                "Muted taupe and soft grey layers, protective wrap or scarf, slightly oversized but neater than Storm. "
                        + "First hint of trying — a tucked shirt, a buttoned coat.",

                "3 blurred silhouettes at different distances in mist: one offering what character needs but unreachable, "
                        + "one walking away oblivious, one holding what character wants but can't access yet.",

                "Thick silver-grey mist, diffused light, ground dissolving. "
                        + "FAINT outline of something hopeful barely visible — a doorway, path, or warm light."
        });

        specs.put(CardStage.clearing, new String[]{
                "Head lifting, making eye contact with viewer for the first time. "
                        + "One hand on heart, other reaching forward palm-up. Small tentative smile. One step onto solid ground.",

                "Soft pastel lavender and cream, lighter flowing fabric. First intentional accessory — "
                        + "thin gold necklace or bracelet. Clothing fits better, chosen not defaulted to.",

                "TWO figures showing first break in the fear pattern: one pauses to extend a genuine hand, "
                        + "another does something WITH the character that addresses the fear. Connection small but real.",

                "Golden hour light breaking through grey clouds, warm amber rays on face. "
                        + "Ground transitions from cracked earth to green shoots. ONE small symbolic victory object nearby."
        });

        specs.put(CardStage.aura, new String[]{
                "Standing tall, feet shoulder-width, arms opening outward palms up. "
                        + "Chin raised, calm steady gaze, knowing smile. One hand casually showing mastery over the fear.",

                "Elegant fitted jewel-tone purple and teal with flowing sleeves catching ethereal light. "
                        + "Silver and crystal jewelry — intentional, refined. Hair styled with care.",

                "2-3 figures in loose semicircle showing REVERSAL of the fear — derive from fear text. "
                        + "Include one SMALLER faded Storm-stage character at far edge watching the transformation.",

                "Twilight purple sky, constellation patterns, ethereal energy particles. "
                        + "Ground glowing blue-violet. ONE symbolic object showing fear is now MANAGED — earned, not gifted."
        });

        specs.put(CardStage.radiance, new String[]{
                "Dynamic wide pose, arms spread with genuine joy, head tilted back, bright smile. "
                        + "Whole body radiating warmth. Feet planted firmly — GROUNDED confidence. 'I built this myself.'",

                "Luxurious cream and gold with warm metallic accents, fitted and confident. "
                        + "Statement gold jewelry, subtle shimmer. Beautiful but practical — still active.",

                "3-4 figures CLOSE as equals — genuine partners and friends. "
                        + "Scene is the OPPOSITE of the fear. Include someone character is HELPING — giving what they once lacked.",

                "Full golden sunlight, warm sparks, lens flares, amber-gold palette. "
                        + "Solid rich ground, blue sky with golden clouds. Environmental ABUNDANCE specific to conquered fear."
        });

        specs.put(CardStage.legend, new String[]{
                "Sovereign still pose — one hand over heart, other extended in blessing. "
                        + "Serene expression holding both Storm memory and overcoming wisdom. Weightless grace, transcended but human.",

                "Mythic ornate gown in gold, sapphire, pearl with celestial self-illuminating fabric. "
                        + "Crown or halo. Visual history woven in — Storm grey hem, Clearing gold thread, Aura crystals at collar.",

                "3-4 figures at DIFFERENT points of their OWN journeys inspired by character: "
                        + "one scared (beginning), one mid-journey (hopeful), one nearly there (tall, smiling). "
                        + "One telling character's story to listeners. Fear became the STORY that helps others.",

                "Cherry blossoms, celestial twilight, stars and dawn visible simultaneously. "
                        + "Behind character: translucent 6-stage journey panels like stained glass. "
                        + "Golden path stretching both behind and ahead. Timeless mythic atmosphere."
        });

        return specs;
    }

    // ── Response parser ───────────────────────────────────────────────────────

    private Map<String, String> parseAiResponse(String response) {
        Map<String, String> result = new LinkedHashMap<>();
        if (response == null || response.isBlank()) return result;

        Pattern pattern = Pattern.compile(
                "(STORM|FOG|CLEARING|AURA|RADIANCE|LEGEND):\\s*(.+?)(?=(?:STORM|FOG|CLEARING|AURA|RADIANCE|LEGEND):|$)",
                Pattern.DOTALL | Pattern.CASE_INSENSITIVE);

        Matcher matcher = pattern.matcher(response);
        while (matcher.find()) {
            String stage  = matcher.group(1).toLowerCase();
            String prompt = matcher.group(2).trim().replaceAll("\\s+", " ");
            result.put(stage, prompt);
            log.debug("  Parsed prompt for stage={}: {} chars", stage, prompt.length());
        }

        return result;
    }

    // ── Token usage + cost ────────────────────────────────────────────────────

    private com.destinyoracle.dto.response.TokenUsageResponse buildTokenUsage(
            org.springframework.ai.chat.model.ChatResponse response, String label) {
        if (response == null || response.getMetadata() == null) return null;
        var usage = response.getMetadata().getUsage();
        if (usage == null) return null;

        long inputTokens  = usage.getPromptTokens();
        long outputTokens = usage.getCompletionTokens();
        long totalTokens  = usage.getTotalTokens();

        // claude-haiku-4-5: $0.80/M input, $4.00/M output
        double inputCost  = inputTokens  / 1_000_000.0 * 0.80;
        double outputCost = outputTokens / 1_000_000.0 * 4.00;
        double totalCost  = inputCost + outputCost;

        log.info("  ┌─ Claude Token Usage [{}]", label);
        log.info("  │  Input  tokens : {}", inputTokens);
        log.info("  │  Output tokens : {}", outputTokens);
        log.info("  │  Total  tokens : {}", totalTokens);
        log.info("  │  Est. cost     : ${} (in=${} out={})",
                String.format("%.6f", totalCost),
                String.format("%.6f", inputCost),
                String.format("%.6f", outputCost));
        log.info("  └─ Model: claude-haiku-4-5 @ $0.80/M in · $4.00/M out");

        return com.destinyoracle.dto.response.TokenUsageResponse.builder()
                .inputTokens(inputTokens).outputTokens(outputTokens).totalTokens(totalTokens)
                .inputCostUsd(inputCost).outputCostUsd(outputCost).totalCostUsd(totalCost)
                .model("claude-haiku-4-5")
                .build();
    }

    // ── Fallback (used when AI is disabled / key not set) ────────────────────

    private String buildFallbackPrompt(String aspectLabel, CardStage stage) {
        return String.format(
                "chibi anime character representing %s at the %s stage, %s, " +
                "full bleed digital illustration, no borders, no frames, highly detailed, digital art, 1k, 9:16",
                aspectLabel, stage.name(), STAGE_MOODS.get(stage));
    }
}
