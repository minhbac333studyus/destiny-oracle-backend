package com.destinyoracle.controller;

import com.destinyoracle.config.AppProperties;
import com.destinyoracle.dto.request.AddCardRequest;
import com.destinyoracle.dto.request.HabitCompleteRequest;
import com.destinyoracle.dto.request.UpdateCardRequest;
import com.destinyoracle.dto.response.*;
import com.destinyoracle.service.CardService;
import com.destinyoracle.service.StageContentGenerationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/cards")
@RequiredArgsConstructor
@Tag(name = "Cards", description = "Destiny card CRUD — spread, detail, habits")
public class CardController {

    private final CardService                   cardService;
    private final StageContentGenerationService stageContentService;
    private final AppProperties                 appProperties;

    @GetMapping
    @Operation(
        summary = "List all cards",
        description = "Returns every destiny card in the user's spread, ordered by sortOrder. " +
                      "Response includes `count` — total number of cards returned."
    )
    public ResponseEntity<ApiResponse<List<SpreadCardSummaryResponse>>> getAllCards(
            @Parameter(description = "User UUID. Omit to use the demo user.")
            @RequestHeader(value = "X-User-Id", required = false) UUID userId) {
        return ResponseEntity.ok(ApiResponse.success(
                cardService.getAllCards(resolve(userId))));
    }

    @GetMapping("/aspects")
    @Operation(
        summary = "Available aspects",
        description = "All 10 built-in aspect definitions with `alreadyAdded: true/false` " +
                      "for the current user. Response includes `count` — total aspects in catalog."
    )
    public ResponseEntity<ApiResponse<List<AvailableAspectResponse>>> getAvailableAspects(
            @Parameter(description = "User UUID. Omit to use the demo user.")
            @RequestHeader(value = "X-User-Id", required = false) UUID userId) {
        return ResponseEntity.ok(ApiResponse.success(
                cardService.getAvailableAspects(resolve(userId))));
    }

    @GetMapping("/{cardId}")
    @Operation(
        summary = "Card detail",
        description = "Full card detail: stage content, habits, image history, and stats."
    )
    public ResponseEntity<ApiResponse<CardDetailResponse>> getCard(
            @Parameter(description = "User UUID. Omit to use the demo user.")
            @RequestHeader(value = "X-User-Id", required = false) UUID userId,
            @PathVariable UUID cardId) {
        return ResponseEntity.ok(ApiResponse.success(
                cardService.getCard(resolve(userId), cardId)));
    }

    @PostMapping
    @Operation(
        summary = "Add aspect card",
        description = "Add a new destiny card from a built-in aspect key (e.g. \"learning\") " +
                      "or set `isCustom: true` with a custom `aspectKey`, `aspectLabel`, and `icon`."
    )
    public ResponseEntity<ApiResponse<CardDetailResponse>> addCard(
            @Parameter(description = "User UUID. Omit to use the demo user.")
            @RequestHeader(value = "X-User-Id", required = false) UUID userId,
            @Valid @RequestBody AddCardRequest request) {
        CardDetailResponse card = cardService.addCard(resolve(userId), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(card));
    }

    @PatchMapping("/{cardId}")
    @Operation(
        summary = "Edit card",
        description = "Update `fearOriginal`, `dreamOriginal`, `aspectLabel`, or `icon`. " +
                      "Only non-null fields are changed."
    )
    public ResponseEntity<ApiResponse<CardDetailResponse>> updateCard(
            @Parameter(description = "User UUID. Omit to use the demo user.")
            @RequestHeader(value = "X-User-Id", required = false) UUID userId,
            @PathVariable UUID cardId,
            @Valid @RequestBody UpdateCardRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                cardService.updateCard(resolve(userId), cardId, request)));
    }

    @DeleteMapping("/{cardId}")
    @Operation(
        summary = "Remove card",
        description = "Permanently removes the card and its habits from the user's spread."
    )
    public ResponseEntity<ApiResponse<Void>> removeCard(
            @Parameter(description = "User UUID. Omit to use the demo user.")
            @RequestHeader(value = "X-User-Id", required = false) UUID userId,
            @PathVariable UUID cardId) {
        cardService.removeCard(resolve(userId), cardId);
        return ResponseEntity.ok(ApiResponse.successVoid());
    }

    @PutMapping("/{cardId}/habits/{habitId}/complete")
    @Operation(
        summary = "Toggle habit completion",
        description = "Mark a habit as done (`completed: true`) or undo it (`completed: false`) for today. Idempotent."
    )
    public ResponseEntity<ApiResponse<Void>> completeHabit(
            @Parameter(description = "User UUID. Omit to use the demo user.")
            @RequestHeader(value = "X-User-Id", required = false) UUID userId,
            @PathVariable UUID cardId,
            @PathVariable UUID habitId,
            @Valid @RequestBody HabitCompleteRequest request) {
        cardService.completeHabit(resolve(userId), cardId, habitId, request.getCompleted());
        return ResponseEntity.ok(ApiResponse.successVoid());
    }

    /**
     * POST /api/v1/cards/{cardId}/generate-stage-content
     *
     * Manually trigger stage content generation (title, tagline, lore × 6 stages).
     * Normally this runs automatically when a card is created via POST /api/v1/cards.
     * Use this endpoint if the auto-generation failed (e.g. no Claude key at creation time).
     */
    @PostMapping("/{cardId}/generate-stage-content")
    @Operation(
        summary = "Generate stage narrative content (Claude)",
        description = "Core AI step: Claude reads the user's fear + dream text and writes " +
                      "title, tagline, and lore for all 6 stages (Storm → Legend). " +
                      "This runs automatically on card creation. Call manually if it failed. " +
                      "Requires ANTHROPIC_API_KEY."
    )
    public ResponseEntity<ApiResponse<StageContentGenerationResponse>> generateStageContent(
            @RequestHeader(value = "X-User-Id", required = false) UUID userId,
            @PathVariable UUID cardId) {
        StageContentGenerationResponse result = stageContentService.generateStageContent(resolve(userId), cardId);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    /**
     * POST /api/v1/cards/{cardId}/regenerate-stage-content
     *
     * Re-generate stage content after the user edits their fear/dream text.
     * Clears existing titles/taglines/lore and writes fresh ones from the new input.
     * Also resets promptStatus to NONE so image prompts are re-generated on next image call.
     */
    @PostMapping("/{cardId}/regenerate-stage-content")
    @Operation(
        summary = "Regenerate stage content after fear/dream edit",
        description = "Re-generates all 6 stage titles, taglines, and lore after the user updates " +
                      "their fear or dream text (PATCH /cards/{cardId}). " +
                      "Also resets promptStatus to NONE so Gemini image prompts are regenerated too. " +
                      "Requires ANTHROPIC_API_KEY."
    )
    public ResponseEntity<ApiResponse<StageContentGenerationResponse>> regenerateStageContent(
            @RequestHeader(value = "X-User-Id", required = false) UUID userId,
            @PathVariable UUID cardId) {
        StageContentGenerationResponse result = stageContentService.regenerateStageContent(resolve(userId), cardId);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    private UUID resolve(UUID header) {
        return header != null ? header : appProperties.getDefaultUserId();
    }
}
