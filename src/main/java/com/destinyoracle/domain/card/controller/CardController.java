package com.destinyoracle.domain.card.controller;

import com.destinyoracle.config.AppProperties;
import com.destinyoracle.dto.request.AddCardRequest;
import com.destinyoracle.dto.request.UpdateCardRequest;
import com.destinyoracle.dto.response.*;
import com.destinyoracle.domain.card.service.CardService;
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

    private final CardService   cardService;
    private final AppProperties appProperties;

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


    private UUID resolve(UUID header) {
        return header != null ? header : appProperties.getDefaultUserId();
    }
}
