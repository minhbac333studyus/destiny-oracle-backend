package com.destinyoracle.controller.card;

import com.destinyoracle.config.AppProperties;
import com.destinyoracle.entity.*;
import com.destinyoracle.repository.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Admin/debug endpoints for inspecting card internals.
 * NOT for production use — exposes all raw data including prompts, fear text, etc.
 */
@Tag(name = "Admin Debug", description = "Developer-only endpoints for inspecting card data and prompt pipeline")
@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class AdminDebugController {

    private final DestinyCardRepository      cardRepository;
    private final CardStageContentRepository stageContentRepository;
    private final CardImageRepository        cardImageRepository;
    private final UserRepository             userRepository;
    private final AppProperties              appProperties;

    // ── Full card dump ──────────────────────────────────────────────────────

    @GetMapping("/cards/{cardId}")
    @Operation(summary = "Full card debug dump",
               description = "Returns ALL internal data for a card: fear, dream, stage content, " +
                             "action scenes, image prompts, image URLs. For developer debugging only.")
    public ResponseEntity<Map<String, Object>> getCardDebug(
            @RequestHeader(value = "X-User-Id", required = false) UUID userId,
            @PathVariable UUID cardId) {

        UUID uid = userId != null ? userId : appProperties.getDefaultUserId();
        DestinyCard card = cardRepository.findByIdAndUserIdWithDetails(cardId, uid)
                .orElseThrow(() -> new RuntimeException("Card not found: " + cardId));

        Map<String, Object> result = new LinkedHashMap<>();

        // ── Card metadata ──
        result.put("cardId", card.getId());
        result.put("aspectKey", card.getAspectKey());
        result.put("aspectLabel", card.getAspectLabel());
        result.put("currentStage", card.getCurrentStage().name());
        result.put("promptStatus", card.getPromptStatus().name());
        result.put("imageUrl", card.getImageUrl());
        result.put("createdAt", card.getUpdatedAt());

        // ── User input ──
        Map<String, Object> userInput = new LinkedHashMap<>();
        userInput.put("fearOriginal", card.getFearOriginal());
        userInput.put("dreamOriginal", card.getDreamOriginal());
        result.put("userInput", userInput);

        // ── User info ──
        AppUser user = card.getUser();
        if (user != null) {
            Map<String, Object> userInfo = new LinkedHashMap<>();
            userInfo.put("userId", user.getId());
            userInfo.put("email", user.getEmail());
            userInfo.put("displayName", user.getDisplayName());
            userInfo.put("onboardingComplete", user.isOnboardingComplete());
            result.put("user", userInfo);
        }

        // ── Stage content (all 6 stages) ──
        List<CardStageContent> stages = stageContentRepository.findAllByCardIdOrderByStageAsc(cardId);
        List<Map<String, Object>> stageList = new ArrayList<>();
        for (CardStageContent sc : stages) {
            Map<String, Object> stageData = new LinkedHashMap<>();
            stageData.put("stage", sc.getStage().name());
            stageData.put("title", sc.getTitle());
            stageData.put("tagline", sc.getTagline());
            stageData.put("lore", sc.getLore());
            stageData.put("actionScene", sc.getActionScene());
            stageData.put("imagePrompt", sc.getImagePrompt());
            stageData.put("generatedAt", sc.getGeneratedAt());
            stageList.add(stageData);
        }
        result.put("stageContent", stageList);

        // ── Images ──
        List<CardImage> images = cardImageRepository.findAllByCardIdOrderByStageAsc(cardId);
        List<Map<String, Object>> imageList = new ArrayList<>();
        for (CardImage img : images) {
            Map<String, Object> imgData = new LinkedHashMap<>();
            imgData.put("stage", img.getStage().name());
            imgData.put("imageUrl", img.getImageUrl());
            imgData.put("promptSummary", img.getPromptSummary());
            imgData.put("generatedAt", img.getGeneratedAt());
            imageList.add(imgData);
        }
        result.put("images", imageList);

        // ── Prompt pipeline summary ──
        Map<String, Object> pipeline = new LinkedHashMap<>();
        pipeline.put("phase1_actionPlan", stages.stream()
                .filter(sc -> sc.getActionScene() != null && !sc.getActionScene().isBlank())
                .count() + "/6 action scenes generated");
        pipeline.put("phase2_narrative", stages.stream()
                .filter(sc -> sc.getTitle() != null && !sc.getTitle().isBlank())
                .count() + "/6 narratives generated");
        pipeline.put("phase3_imagePrompts", stages.stream()
                .filter(sc -> sc.getImagePrompt() != null && !sc.getImagePrompt().isBlank())
                .count() + "/6 image prompts generated");
        pipeline.put("phase4_images", images.size() + "/6 images generated");
        result.put("pipeline", pipeline);

        return ResponseEntity.ok(result);
    }

    // ── List all cards (summary) ────────────────────────────────────────────

    @GetMapping("/cards")
    @Operation(summary = "List all cards for a user (debug summary)")
    public ResponseEntity<List<Map<String, Object>>> listCards(
            @RequestHeader(value = "X-User-Id", required = false) UUID userId) {

        UUID uid = userId != null ? userId : appProperties.getDefaultUserId();
        List<DestinyCard> cards = cardRepository.findAllByUserIdOrderByUpdatedAtDesc(uid);

        List<Map<String, Object>> result = cards.stream().map(card -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("cardId", card.getId());
            m.put("aspectLabel", card.getAspectLabel());
            m.put("currentStage", card.getCurrentStage().name());
            m.put("promptStatus", card.getPromptStatus().name());
            m.put("imageUrl", card.getImageUrl());
            m.put("createdAt", card.getUpdatedAt());
            return m;
        }).collect(Collectors.toList());

        return ResponseEntity.ok(result);
    }

    // ── All users (debug) ───────────────────────────────────────────────────

    @GetMapping("/users")
    @Operation(summary = "List all users (debug)")
    public ResponseEntity<List<Map<String, Object>>> listUsers() {
        List<AppUser> users = userRepository.findAll();
        List<Map<String, Object>> result = users.stream().map(u -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("userId", u.getId());
            m.put("email", u.getEmail());
            m.put("displayName", u.getDisplayName());
            m.put("onboardingComplete", u.isOnboardingComplete());
            m.put("joinedAt", u.getJoinedAt());
            m.put("cardCount", cardRepository.countByUserId(u.getId()));
            return m;
        }).collect(Collectors.toList());
        return ResponseEntity.ok(result);
    }
}
