package com.destinyoracle.service.impl;

import com.destinyoracle.dto.response.StageContentEntry;
import com.destinyoracle.dto.response.StageContentGenerationResponse;
import com.destinyoracle.entity.*;
import com.destinyoracle.exception.ResourceNotFoundException;
import com.destinyoracle.repository.CardStageContentRepository;
import com.destinyoracle.repository.DestinyCardRepository;
import com.destinyoracle.service.StageContentGenerationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * ══════════════════════════════════════════════════════════════════
 *  CORE LOGIC — Stage Content Generation (2-Phase)
 * ══════════════════════════════════════════════════════════════════
 *
 * Phase 1: ACTION PLAN — Claude generates 6 concrete escalating actions
 * Phase 2: NARRATIVE   — Claude writes poetic lore AROUND those actions
 *
 * Both phases are persisted to card_stage_content.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StageContentGenerationServiceImpl implements StageContentGenerationService {

    private final DestinyCardRepository      cardRepository;
    private final CardStageContentRepository stageContentRepository;
    private final ChatClient.Builder         chatClientBuilder;

    // ── Public API ────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public StageContentGenerationResponse generateStageContent(UUID userId, UUID cardId) {
        return doGenerate(userId, cardId, false);
    }

    @Override
    @Transactional
    public StageContentGenerationResponse regenerateStageContent(UUID userId, UUID cardId) {
        return doGenerate(userId, cardId, true);
    }

    // ── Core logic ────────────────────────────────────────────────────────────

    private StageContentGenerationResponse doGenerate(UUID userId, UUID cardId, boolean isRegen) {
        DestinyCard card = cardRepository.findByIdAndUserIdWithDetails(cardId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Card", "id", cardId));

        String aspectLabel = card.getAspectLabel();
        String fearText    = card.getFearOriginal();
        String dreamText   = card.getDreamOriginal();

        log.info("━━━ [STAGE CONTENT] {} for card={} aspect='{}'",
                isRegen ? "REGENERATE" : "GENERATE", cardId, aspectLabel);
        log.info("  Fear : {}", fearText);
        log.info("  Dream: {}", dreamText);

        if (isRegen) {
            card.setPromptStatus(PromptStatus.NONE);
            cardRepository.save(card);
            log.info("  PromptStatus reset to NONE — image prompts will be regenerated on next image call");
        }

        // ══════════════════════════════════════════════════════════════════
        // PHASE 1: ACTION PLAN — concrete physical actions per stage
        // ══════════════════════════════════════════════════════════════════
        log.info("─── Phase 1: ACTION PLAN ───");
        Map<String, String> actionPlan = new LinkedHashMap<>();
        com.destinyoracle.dto.response.TokenUsageResponse tokenUsage1 = null;

        try {
            String actionPrompt = buildActionPlanPrompt(aspectLabel, fearText, dreamText);
            log.info("  Calling Claude for action plan ({} chars prompt)…", actionPrompt.length());

            org.springframework.ai.chat.model.ChatResponse chatResponse1 = chatClientBuilder.build()
                    .prompt().user(actionPrompt).call().chatResponse();

            String aiResponse1 = chatResponse1 == null ? null : chatResponse1.getResult().getOutput().getText();
            tokenUsage1 = buildTokenUsage(chatResponse1, "Phase 1: Action Plan");
            log.info("  Claude responded ({} chars) — parsing action plan…",
                    aiResponse1 == null ? 0 : aiResponse1.length());

            actionPlan = parseActionPlanResponse(aiResponse1);
            log.info("  Parsed {} action scenes", actionPlan.size());

        } catch (Exception e) {
            log.warn("  Phase 1 Claude unavailable ({}). Using fallback actions.", e.getMessage());
        }

        // Fill missing actions with fallbacks
        for (CardStage stage : CardStage.values()) {
            actionPlan.putIfAbsent(stage.name(), buildFallbackAction(stage));
        }

        // Save action_scene to DB
        for (CardStage stage : CardStage.values()) {
            String action = actionPlan.get(stage.name());
            Optional<CardStageContent> existing = stageContentRepository.findByCardIdAndStage(cardId, stage);
            if (existing.isPresent()) {
                existing.get().setActionScene(action);
                stageContentRepository.save(existing.get());
            } else {
                stageContentRepository.save(CardStageContent.builder()
                        .card(card).stage(stage)
                        .title("").tagline("").lore("")
                        .actionScene(action)
                        .build());
            }
            log.debug("  Saved action_scene for stage={}: {}", stage.name(), action);
        }

        // ══════════════════════════════════════════════════════════════════
        // PHASE 2: NARRATIVE — poetic lore built around the actions
        // ══════════════════════════════════════════════════════════════════
        log.info("─── Phase 2: NARRATIVE ───");
        Map<String, StageContentEntry> parsed = new LinkedHashMap<>();
        com.destinyoracle.dto.response.TokenUsageResponse tokenUsage2 = null;

        try {
            String narrativePrompt = buildNarrativePrompt(aspectLabel, fearText, dreamText, actionPlan);
            log.info("  Calling Claude for narrative ({} chars prompt)…", narrativePrompt.length());

            org.springframework.ai.chat.model.ChatResponse chatResponse2 = chatClientBuilder.build()
                    .prompt().user(narrativePrompt).call().chatResponse();

            String aiResponse2 = chatResponse2 == null ? null : chatResponse2.getResult().getOutput().getText();
            tokenUsage2 = buildTokenUsage(chatResponse2, "Phase 2: Narrative");
            log.info("  Claude responded ({} chars) — parsing narrative…",
                    aiResponse2 == null ? 0 : aiResponse2.length());

            parsed = parseNarrativeResponse(aiResponse2);
            log.info("  Parsed {} narrative entries", parsed.size());

        } catch (Exception e) {
            log.warn("  Phase 2 Claude unavailable ({}). Using fallback content.", e.getMessage());
        }

        // Fill missing stages with fallback
        for (CardStage stage : CardStage.values()) {
            if (!parsed.containsKey(stage.name())) {
                log.warn("  Missing narrative for stage={} — using fallback", stage.name());
                parsed.put(stage.name(), buildFallback(aspectLabel, fearText, dreamText, stage));
            }
        }

        // Persist title/tagline/lore
        log.info("  Persisting 6 stage content entries to DB…");
        for (CardStage stage : CardStage.values()) {
            StageContentEntry entry = parsed.get(stage.name());
            if (entry == null) continue;

            Optional<CardStageContent> existing = stageContentRepository.findByCardIdAndStage(cardId, stage);
            if (existing.isPresent()) {
                CardStageContent sc = existing.get();
                sc.setTitle(entry.getTitle());
                sc.setTagline(entry.getTagline());
                sc.setLore(entry.getLore());
                stageContentRepository.save(sc);
                log.debug("    Updated stage={} title='{}'", stage.name(), entry.getTitle());
            } else {
                stageContentRepository.save(CardStageContent.builder()
                        .card(card).stage(stage)
                        .title(entry.getTitle())
                        .tagline(entry.getTagline())
                        .lore(entry.getLore())
                        .build());
                log.debug("    Created stage={} title='{}'", stage.name(), entry.getTitle());
            }
        }

        log.info("━━━ [STAGE CONTENT] Done — all 6 stages saved for card={}", cardId);

        // Combine token usage
        com.destinyoracle.dto.response.TokenUsageResponse combinedUsage = combineTokenUsage(tokenUsage1, tokenUsage2);

        return StageContentGenerationResponse.builder()
                .cardId(cardId)
                .aspectKey(card.getAspectKey())
                .aspectLabel(aspectLabel)
                .stageContent(parsed)
                .tokenUsage(combinedUsage)
                .build();
    }

    // ══════════════════════════════════════════════════════════════════════════
    // PHASE 1: ACTION PLAN PROMPT
    // ══════════════════════════════════════════════════════════════════════════

    private String buildActionPlanPrompt(String aspectLabel, String fearText, String dreamText) {
        StringBuilder sb = new StringBuilder();
        sb.append("""
                You design action plans for people overcoming fears.

                FEAR: """).append(fearText).append("\n");

        if (dreamText != null && !dreamText.isBlank()) {
            sb.append("DREAM: ").append(dreamText).append("\n");
        }

        sb.append("ASPECT: ").append(aspectLabel).append("\n\n");

        sb.append("""
                Design 6 escalating actions this person takes to go from their worst-case fear to their ultimate dream.
                Each action must be a concrete physical activity a camera could photograph.

                STORM: The worst case is real. What is the FIRST tiny brave action they take despite the nightmare?
                FOG: What low-stakes private practice or preparation do they do next?
                CLEARING: What is their first real-world attempt with real people and real stakes?
                AURA: How do they build on success at intermediate scale? (leading, teaching, managing)
                RADIANCE: What high-level achievement shows the dream becoming real?
                LEGEND: What is the ULTIMATE version of the dream at maximum scale? They must still be actively DOING something, not just posing.

                RULES:
                - One sentence each. Describe only what a camera sees.
                - Each action must be bigger than the previous one.
                - No emotions or feelings — only physical actions, objects, people.

                FORMAT (exactly):
                STORM: <one sentence>
                FOG: <one sentence>
                CLEARING: <one sentence>
                AURA: <one sentence>
                RADIANCE: <one sentence>
                LEGEND: <one sentence>
                """);

        return sb.toString();
    }

    private Map<String, String> parseActionPlanResponse(String response) {
        Map<String, String> result = new LinkedHashMap<>();
        if (response == null || response.isBlank()) return result;

        Pattern pattern = Pattern.compile(
                "(STORM|FOG|CLEARING|AURA|RADIANCE|LEGEND):\\s*(.+?)(?=(?:STORM|FOG|CLEARING|AURA|RADIANCE|LEGEND):|$)",
                Pattern.DOTALL | Pattern.CASE_INSENSITIVE);

        Matcher matcher = pattern.matcher(response);
        while (matcher.find()) {
            String stage  = matcher.group(1).toLowerCase();
            String action = matcher.group(2).trim().replaceAll("\\s+", " ");
            result.put(stage, action);
            log.debug("  Action for stage={}: {}", stage, action);
        }
        return result;
    }

    private String buildFallbackAction(CardStage stage) {
        return switch (stage) {
            case storm    -> "Character sits alone in a dark room, staring at their hands.";
            case fog      -> "Character opens a notebook and writes one line by dim lamplight.";
            case clearing -> "Character steps outside into morning light, taking a deep breath.";
            case aura     -> "Character stands before a small group, speaking with quiet confidence.";
            case radiance -> "Character leads a room full of people, gesturing toward a bright screen.";
            case legend   -> "Character stands at a podium addressing a vast crowd, arms open wide.";
        };
    }

    // ══════════════════════════════════════════════════════════════════════════
    // PHASE 2: NARRATIVE PROMPT (uses action plan)
    // ══════════════════════════════════════════════════════════════════════════

    private String buildNarrativePrompt(String aspectLabel, String fearText, String dreamText,
                                        Map<String, String> actionPlan) {
        StringBuilder sb = new StringBuilder();
        sb.append("""
                You write narrative arcs for a destiny card app.

                FEAR: """).append(fearText).append("\n");

        if (dreamText != null && !dreamText.isBlank()) {
            sb.append("DREAM: ").append(dreamText).append("\n");
        }

        sb.append("ASPECT: ").append(aspectLabel).append("\n\n");

        sb.append("The character takes these 6 actions to overcome their fear:\n");
        for (CardStage stage : CardStage.values()) {
            String action = actionPlan.getOrDefault(stage.name(), "");
            sb.append(stage.name().toUpperCase()).append(": ").append(action).append("\n");
        }

        sb.append("""

                For each stage, write narrative content grounded in the action above.

                RULES:
                - Lore must describe the action FIRST, then the emotional consequence.
                - Tone: poetic but grounded, like a journal entry mixed with a tarot reading.
                - Use "you" to speak directly to the user.
                - No joy/smile language until RADIANCE. Storm through Aura = grit, effort, determination.
                - 2-3 sentences per lore. First sentence = what you DO. Second = what happens.

                FORMAT (exactly):
                STORM:
                TITLE: <3-5 word card name>
                TAGLINE: <one-line hook>
                LORE: <2-3 sentences, action-first>

                FOG:
                TITLE: <title>
                TAGLINE: <tagline>
                LORE: <lore>

                CLEARING:
                TITLE: <title>
                TAGLINE: <tagline>
                LORE: <lore>

                AURA:
                TITLE: <title>
                TAGLINE: <tagline>
                LORE: <lore>

                RADIANCE:
                TITLE: <title>
                TAGLINE: <tagline>
                LORE: <lore>

                LEGEND:
                TITLE: <title>
                TAGLINE: <tagline>
                LORE: <lore>
                """);

        return sb.toString();
    }

    // ── Response parser (Phase 2 — same format as before) ───────────────────

    private Map<String, StageContentEntry> parseNarrativeResponse(String response) {
        Map<String, StageContentEntry> result = new LinkedHashMap<>();
        if (response == null || response.isBlank()) return result;

        Pattern stagePattern = Pattern.compile(
            "(STORM|FOG|CLEARING|AURA|RADIANCE|LEGEND):\\s*\\n" +
            "TITLE:\\s*(.+?)\\n" +
            "TAGLINE:\\s*(.+?)\\n" +
            "LORE:\\s*(.+?)(?=\\n(?:STORM|FOG|CLEARING|AURA|RADIANCE|LEGEND):|$)",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

        Matcher matcher = stagePattern.matcher(response);
        while (matcher.find()) {
            String stage   = matcher.group(1).toLowerCase();
            String title   = matcher.group(2).trim();
            String tagline = matcher.group(3).trim();
            String lore    = matcher.group(4).trim().replaceAll("\\s+", " ");

            result.put(stage, StageContentEntry.builder()
                    .title(title).tagline(tagline).lore(lore).build());
            log.debug("  Parsed stage={} title='{}'", stage, title);
        }
        return result;
    }

    // ── Fallback ──────────────────────────────────────────────────────────────

    private StageContentEntry buildFallback(String aspectLabel, String fearText,
                                             String dreamText, CardStage stage) {
        return switch (stage) {
            case storm    -> StageContentEntry.builder()
                    .title("The " + aspectLabel + " Storm")
                    .tagline("Facing what you feared most")
                    .lore(fearText != null && !fearText.isBlank() ? fearText
                            : "The weight of this fear is real. It has shaped your choices without you knowing.")
                    .build();
            case fog      -> StageContentEntry.builder()
                    .title("The Uncertain Path")
                    .tagline("Moving forward without a map")
                    .lore("You've taken the first step even though you can't see the destination. That takes courage.")
                    .build();
            case clearing -> StageContentEntry.builder()
                    .title("First Light")
                    .tagline("The path ahead becomes clear")
                    .lore("Something shifted. The fog thinned and you could see further than before. Keep going.")
                    .build();
            case aura     -> StageContentEntry.builder()
                    .title("The Inner Glow")
                    .tagline("Strength that others can feel")
                    .lore("You carry yourself differently now. The work you've done is showing up in ways you didn't expect.")
                    .build();
            case radiance -> StageContentEntry.builder()
                    .title("Living It")
                    .tagline("The dream is no longer a dream")
                    .lore(dreamText != null && !dreamText.isBlank()
                            ? "You are becoming the person who " + dreamText.toLowerCase() + "."
                            : "You are living what you once only imagined was possible.")
                    .build();
            case legend   -> StageContentEntry.builder()
                    .title("The " + aspectLabel + " Legend")
                    .tagline("Your story inspires others")
                    .lore("You have walked the full arc — from fear to freedom. What you've built cannot be taken away.")
                    .build();
        };
    }

    // ── Token usage helpers ──────────────────────────────────────────────────

    private com.destinyoracle.dto.response.TokenUsageResponse buildTokenUsage(
            org.springframework.ai.chat.model.ChatResponse response, String label) {
        if (response == null || response.getMetadata() == null) return null;
        var usage = response.getMetadata().getUsage();
        if (usage == null) return null;

        long inputTokens  = usage.getPromptTokens();
        long outputTokens = usage.getGenerationTokens();
        long totalTokens  = usage.getTotalTokens();

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

    private com.destinyoracle.dto.response.TokenUsageResponse combineTokenUsage(
            com.destinyoracle.dto.response.TokenUsageResponse a,
            com.destinyoracle.dto.response.TokenUsageResponse b) {
        if (a == null) return b;
        if (b == null) return a;
        return com.destinyoracle.dto.response.TokenUsageResponse.builder()
                .inputTokens(a.getInputTokens() + b.getInputTokens())
                .outputTokens(a.getOutputTokens() + b.getOutputTokens())
                .totalTokens(a.getTotalTokens() + b.getTotalTokens())
                .inputCostUsd(a.getInputCostUsd() + b.getInputCostUsd())
                .outputCostUsd(a.getOutputCostUsd() + b.getOutputCostUsd())
                .totalCostUsd(a.getTotalCostUsd() + b.getTotalCostUsd())
                .model("claude-haiku-4-5")
                .build();
    }
}
