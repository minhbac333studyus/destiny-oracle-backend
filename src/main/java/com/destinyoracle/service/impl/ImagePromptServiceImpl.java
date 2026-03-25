package com.destinyoracle.service.impl;

import com.destinyoracle.dto.response.ImagePromptResponse;
import com.destinyoracle.entity.*;
import com.destinyoracle.exception.ResourceNotFoundException;
import com.destinyoracle.repository.CardStageContentRepository;
import com.destinyoracle.repository.DestinyCardRepository;
import com.destinyoracle.repository.GenerationJobRepository;
import com.destinyoracle.repository.GenerationJobStepRepository;
import com.destinyoracle.service.ImagePromptService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class ImagePromptServiceImpl implements ImagePromptService {

    private final DestinyCardRepository       cardRepository;
    private final CardStageContentRepository  stageContentRepository;
    private final GenerationJobRepository     jobRepository;
    private final GenerationJobStepRepository stepRepository;
    private final JobStepUpdater              stepUpdater;
    private final ChatClient.Builder          chatClientBuilder;

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
                            .title("").tagline("").lore("")
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
        You are a master visual storyteller who writes prompts for AI image generators.
        You create 6 image prompts for a tarot-style destiny card showing one person's
        transformation journey. ALL prompts share one consistent chibi anime character.

        ART STYLE (every prompt):
        - Chibi anime (large expressive eyes, 2.5-head proportion, cute but emotionally deep)
        - Tarot card vertical 9:16, ornate gold filigree border
        - Cinematic lighting, rich atmospheric background
        - Color palette shifts: cold/desaturated (Storm) → warm/vibrant (Legend)

        """);

        sb.append("LIFE ASPECT: ").append(aspectLabel).append("\n");
        sb.append("USER'S DEEPEST FEAR: ").append(fearText).append("\n\n");

        // ── Per-stage direction (lore + visual spec together) ──
        sb.append("STAGE DIRECTION — use the lore as emotional context, then follow the visual spec:\n\n");

        Map<CardStage, String[]> stageSpecs = buildStageSpecs();

        for (CardStage stage : CardStage.values()) {
            CardStageContent content = contentByStage.get(stage);
            String[] spec = stageSpecs.get(stage);

            sb.append("── ").append(stage.name().toUpperCase()).append(" ──\n");

            if (content != null) {
                sb.append("Lore: ").append(content.getTitle())
                        .append(" — ").append(content.getLore()).append("\n");

                // Action scene is the MOST IMPORTANT input — tells the image what the character is DOING
                if (content.getActionScene() != null && !content.getActionScene().isBlank()) {
                    sb.append("Action: ").append(content.getActionScene()).append("\n");
                }
            } else {
                sb.append("Mood: ").append(STAGE_MOODS.get(stage)).append("\n");
            }

            sb.append("Pose: ").append(spec[0]).append("\n");
            sb.append("Outfit: ").append(spec[1]).append("\n");
            sb.append("Crowd: ").append(spec[2]).append("\n");
            sb.append("Scene: ").append(spec[3]).append("\n\n");
        }

        sb.append("""
        PROMPT RULES:
        1. Start every prompt with "Chibi anime character"
        2. The "Action" line for each stage is the MOST IMPORTANT input.
           Build the entire image around that specific physical activity.
        3. Write ONLY things an image AI can render — specific colors, objects, poses,
           light sources, spatial positions. NO abstract emotions ("cautious optimism",
           "emerging hope"). Instead: "eyes wide, slight smile, chin raised."
        3. Describe the exact COLOR of every element — hair, outfit, skin lighting, sky,
           ground, particles. Be specific: "dusty rose blouse" not "lighter clothing."
        4. Background figures: describe their EXACT pose and position relative to the
           main character. "turned 45 degrees away, arms crossed, 3 feet to the left"
           — not "emotionally distant."
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
        Map<CardStage, String[]> specs = new LinkedHashMap<>();

        specs.put(CardStage.storm, new String[]{
                "Curled up or collapsed, knees to chest, head bowed into arms, fists clenched around "
                        + "a symbolic object that represents the user's specific fear. "
                        + "Eyes squeezed shut or staring down. Body small, tight, shoulders hunched inward.",

                "Worn dark oversized hoodie or coat in dull charcoal and faded black, frayed edges, "
                        + "fabric swallowing the small body. Scuffed shoes, no accessories. Everything looks borrowed or outgrown.",

                "2-3 figures that EMBODY the user's specific fear through their actions toward the character. "
                        + "Read the fear text and derive what these people would be doing if the fear were true: "
                        + "financial fear → figures walking past with shopping bags while character has empty hands; "
                        + "loneliness fear → couples and friend groups laughing with backs turned, character invisible; "
                        + "abuse/control fear → one large figure looming over the character, others looking away pretending not to see; "
                        + "career failure → figures in polished suits climbing stairs while character sits at the bottom; "
                        + "health fear → figures running and active while character is too exhausted to stand. "
                        + "These are EXAMPLES — always derive from the ACTUAL fear text, not these templates.",

                "Dark stormy sky, rain streaks, cold blue-purple lighting, cracked ground, no shelter. "
                        + "Include ONE symbolic object on the ground that represents the user's fear made visible — "
                        + "derive from fear text (scattered bills, an empty phone, a locked door, a broken mirror, "
                        + "a wilted plant, chains, a rejection letter). The object should make the viewer instantly "
                        + "understand WHAT this person is afraid of without any text."
        });

        specs.put(CardStage.fog, new String[]{
                "Standing still, half-turned, one arm reaching tentatively into empty space, "
                        + "other arm clutching something related to the fear — a phone, a document, a key, "
                        + "a hand reaching for someone who isn't there. "
                        + "Wide uncertain eyes, mouth slightly open, head tilted — neither retreating nor advancing.",

                "Muted taupe and soft grey layers, protective wrap or scarf, slightly oversized but neater than Storm. "
                        + "First hint of trying — a tucked shirt, a buttoned coat — but still hiding behind the fabric.",

                "3 blurred silhouettes at different distances in the mist, each derived from the fear: "
                        + "one appears to offer what the character needs but is unreachable through the fog; "
                        + "one walks the opposite direction, oblivious to the character's existence; "
                        + "one holds or represents the thing the character desperately wants but can't access yet "
                        + "(a hand to hold, an opportunity, freedom, health, acceptance). "
                        + "The character is TRYING to reach them but the fog makes connection impossible.",

                "Thick silver-grey mist, diffused light, ground dissolving, visibility limited. "
                        + "Include a FAINT outline of something hopeful barely visible through the fog — "
                        + "a doorway, a path, a warm light, a silhouette reaching back — "
                        + "the solution EXISTS but the character can't see it clearly yet."
        });

        specs.put(CardStage.clearing, new String[]{
                "Head lifting, making eye contact with the viewer for the first time. "
                        + "One hand on heart, other hand reaching forward palm-up. Small tentative smile forming. "
                        + "One small step forward onto solid ground. Body still cautious but no longer hiding.",

                "Soft pastel lavender and cream, lighter flowing fabric. First intentional accessory — "
                        + "a thin gold necklace or bracelet. Clothing fits better, chosen not defaulted to.",

                "TWO figures showing the first break in the pattern of the fear: "
                        + "ONE pauses, looks back, extends a genuine hand — this is the first person who truly SEES the character. "
                        + "Another is doing something WITH the character that relates to the fear being addressed — "
                        + "sharing a meal (loneliness), reviewing a plan together (financial), walking side by side as equals "
                        + "(control/abuse), training together (health), collaborating (career). "
                        + "The connection is small but REAL — the first proof that the fear doesn't have to be permanent.",

                "Golden hour light breaking through grey clouds, warm amber rays on character's face. "
                        + "Ground transitions from cracked earth to green shoots and small flowers. "
                        + "ONE small symbolic object near the character showing the first victory over the fear — "
                        + "a sprouting plant through concrete, a coin in sunlight, an unlocked door, "
                        + "an outstretched hand being held, a first step on a new path."
        });

        specs.put(CardStage.aura, new String[]{
                "Standing tall, feet shoulder-width apart, arms opening outward palms up. "
                        + "Chin raised, calm steady gaze, slight knowing smile. "
                        + "One hand may casually hold or interact with something that shows growing mastery over the fear — "
                        + "held with ease, not anxiety.",

                "Elegant fitted jewel-tone purple and teal with flowing sleeves catching ethereal light. "
                        + "Silver and crystal jewelry — intentional, refined, self-chosen. Hair styled with care.",

                "2-3 figures in a loose semicircle showing the REVERSAL of the fear playing out: "
                        + "derive from fear text — the thing the character was most afraid of is now being actively disproven. "
                        + "If lonely → figures genuinely leaning in, making space for the character at center; "
                        + "if controlled → character is the one making choices, others following HER lead; "
                        + "if financial → character freely choosing without calculating, saying yes to experiences; "
                        + "if health → character active and strong, others keeping up with HER pace; "
                        + "if career → others seeking the character's input, valuing her voice. "
                        + "Include one SMALLER faded version of the Storm-stage character at the far edge, "
                        + "watching the transformation — the past self as witness.",

                "Twilight purple sky, constellation patterns, ethereal energy particles floating. "
                        + "Ground glowing deep blue-violet. Include ONE symbolic object showing the fear is now MANAGED — "
                        + "not magically gone, but handled with confidence. The object should feel earned, not gifted."
        });

        specs.put(CardStage.radiance, new String[]{
                "Dynamic wide pose, arms spread with genuine joy, head tilted back, bright real smile. "
                        + "Whole body radiating warmth. Feet planted firmly — GROUNDED confidence, not floating. "
                        + "Posture says 'I built this myself.'",

                "Luxurious cream and gold outfit with warm metallic accents, fitted and confident. "
                        + "Statement gold jewelry, subtle shimmer in fabric. Beautiful but practical — still active, still building.",

                "3-4 figures CLOSE around the character as equals — not admirers, but genuine partners and friends: "
                        + "the SPECIFIC scene should be the OPPOSITE of what the fear described — "
                        + "if fear was about dinners they couldn't afford → character at a full table picking up the check with ease; "
                        + "if fear was about being alone → intimate group with arms around each other, real laughter; "
                        + "if fear was about being controlled → character leading, others happily following; "
                        + "if fear was about health → character finishing a race, friends cheering at the finish line; "
                        + "if fear was about career → character mentoring someone younger, passing the torch. "
                        + "Include someone the character is HELPING — giving what they once lacked. "
                        + "IMPORTANT: derive the exact scene from the fear text, these are only examples.",

                "Full golden sunlight, warm sparks, lens flares, everything amber-gold. "
                        + "Solid rich ground, blue sky with golden clouds. "
                        + "Environmental details showing ABUNDANCE specific to the conquered fear — "
                        + "a full table, an open road, a thriving workspace, a home with warm lights, "
                        + "a healthy body in motion, a circle of real friends. Derive from fear text."
        });

        specs.put(CardStage.legend, new String[]{
                "Sovereign still pose — one hand over heart, other extended in invitation or blessing. "
                        + "Serene expression, eyes holding both the memory of the Storm and the wisdom of having overcome it. "
                        + "Weightless grace, feet barely touching the ground — transcended but still human.",

                "Mythic ornate gown in gold, sapphire, and pearl with celestial self-illuminating fabric. "
                        + "Crown or luminous halo element. Visual history woven into the outfit — "
                        + "a patch of Storm grey at the hem, a thread of Clearing gold, Aura crystals at the collar — "
                        + "the journey is part of the garment.",

                "3-4 figures at DIFFERENT points of their OWN journeys inspired by the character: "
                        + "one small scared figure resembling Storm (just beginning), one mid-journey (looking up with hope), "
                        + "one nearly there (standing tall and smiling). They are NOT looking to the character for rescue — "
                        + "they are finding their own courage because the character proved it's possible. "
                        + "One figure is telling the character's story to a small group of listeners. "
                        + "The fear that once defined the character has become the STORY that helps others face theirs.",

                "Cherry blossoms falling, celestial twilight, both stars and dawn visible simultaneously. "
                        + "Behind the character: translucent layered scenes showing the FULL 6-stage journey — "
                        + "Storm darkness, Fog mist, Clearing sunrise, Aura twilight, Radiance gold — "
                        + "visible like stained glass or memory panels. "
                        + "Golden path stretching both behind and ahead. Timeless mythic atmosphere — "
                        + "this moment exists outside time, the character has become the story."
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
        long outputTokens = usage.getGenerationTokens();
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
