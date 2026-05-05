package com.destinyoracle.domain.nutrition.controller;

import com.destinyoracle.config.AppProperties;
import com.destinyoracle.domain.nutrition.repository.FoodLogEntryRepository;
import com.destinyoracle.domain.nutrition.service.NutritionService;
import com.destinyoracle.dto.request.*;
import com.destinyoracle.dto.response.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/nutrition")
@RequiredArgsConstructor
@Tag(name = "Nutrition", description = "Calorie/macro tracking, body composition, favorites")
public class NutritionController {

    private final NutritionService nutritionService;
    private final FoodLogEntryRepository foodLogEntryRepo;
    private final AppProperties appProperties;

    // ── Goals ──────────────────────────────────────────────────────────────

    @GetMapping("/goals")
    @Operation(summary = "Get nutrition goals", description = "Returns current macro targets. Creates defaults if none exist.")
    public ResponseEntity<ApiResponse<NutritionGoalResponse>> getGoals(
            @Parameter(description = "User UUID") @RequestHeader(value = "X-User-Id", required = false) UUID userId) {
        return ResponseEntity.ok(ApiResponse.success(nutritionService.getOrCreateGoals(resolve(userId))));
    }

    @PutMapping("/goals")
    @Operation(summary = "Update nutrition goals", description = "Partial update — only non-null fields are changed.")
    public ResponseEntity<ApiResponse<NutritionGoalResponse>> updateGoals(
            @RequestHeader(value = "X-User-Id", required = false) UUID userId,
            @Valid @RequestBody UpdateNutritionGoalRequest request) {
        return ResponseEntity.ok(ApiResponse.success(nutritionService.updateGoals(resolve(userId), request)));
    }

    // ── Food Log ──────────────────────────────────────────────────────────

    @GetMapping("/food-log")
    @Operation(summary = "Get food log for a date")
    public ResponseEntity<ApiResponse<List<FoodLogEntryResponse>>> getFoodLog(
            @RequestHeader(value = "X-User-Id", required = false) UUID userId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ResponseEntity.ok(ApiResponse.success(nutritionService.getFoodLog(resolve(userId), date)));
    }

    @PostMapping("/food-log")
    @Operation(summary = "Add food log entry")
    public ResponseEntity<ApiResponse<FoodLogEntryResponse>> addFoodLogEntry(
            @RequestHeader(value = "X-User-Id", required = false) UUID userId,
            @Valid @RequestBody AddFoodLogEntryRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.success(nutritionService.addFoodLogEntry(resolve(userId), request)));
    }

    @PatchMapping("/food-log/{entryId}/serving-qty")
    @Operation(summary = "Update serving quantity for a food log entry")
    public ResponseEntity<ApiResponse<FoodLogEntryResponse>> updateFoodLogServingQty(
            @RequestHeader(value = "X-User-Id", required = false) UUID userId,
            @PathVariable UUID entryId,
            @RequestParam double qty) {
        return ResponseEntity.ok(ApiResponse.success(
            nutritionService.updateFoodLogServingQty(resolve(userId), entryId, qty)));
    }

    @DeleteMapping("/food-log/{entryId}")
    @Operation(summary = "Remove food log entry")
    public ResponseEntity<ApiResponse<Void>> removeFoodLogEntry(
            @RequestHeader(value = "X-User-Id", required = false) UUID userId,
            @PathVariable UUID entryId) {
        nutritionService.removeFoodLogEntry(resolve(userId), entryId);
        return ResponseEntity.ok(ApiResponse.successVoid());
    }

    // ── Daily Summary ─────────────────────────────────────────────────────

    @GetMapping("/daily-summary")
    @Operation(summary = "Aggregated macros vs goals for a date")
    public ResponseEntity<ApiResponse<DailyMacroSummaryResponse>> getDailySummary(
            @RequestHeader(value = "X-User-Id", required = false) UUID userId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ResponseEntity.ok(ApiResponse.success(nutritionService.getDailySummary(resolve(userId), date)));
    }

    // ── Body Composition ──────────────────────────────────────────────────

    @GetMapping("/body-composition")
    @Operation(summary = "Get body composition history")
    public ResponseEntity<ApiResponse<List<BodyCompositionEntryResponse>>> getBodyCompHistory(
            @RequestHeader(value = "X-User-Id", required = false) UUID userId) {
        return ResponseEntity.ok(ApiResponse.success(nutritionService.getBodyCompHistory(resolve(userId))));
    }

    @PostMapping("/body-composition")
    @Operation(summary = "Log body composition entry")
    public ResponseEntity<ApiResponse<BodyCompositionEntryResponse>> addBodyCompEntry(
            @RequestHeader(value = "X-User-Id", required = false) UUID userId,
            @Valid @RequestBody AddBodyCompEntryRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.success(nutritionService.addBodyCompEntry(resolve(userId), request)));
    }

    // ── Favorites ─────────────────────────────────────────────────────────

    @GetMapping("/favorites")
    @Operation(summary = "Get favorite foods")
    public ResponseEntity<ApiResponse<List<FavoriteFoodResponse>>> getFavorites(
            @RequestHeader(value = "X-User-Id", required = false) UUID userId) {
        return ResponseEntity.ok(ApiResponse.success(nutritionService.getFavorites(resolve(userId))));
    }

    @PostMapping("/favorites")
    @Operation(summary = "Add food to favorites")
    public ResponseEntity<ApiResponse<FavoriteFoodResponse>> addFavorite(
            @RequestHeader(value = "X-User-Id", required = false) UUID userId,
            @Valid @RequestBody AddFavoriteFoodRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.success(nutritionService.addFavorite(resolve(userId), request)));
    }

    @DeleteMapping("/favorites/{favId}")
    @Operation(summary = "Remove food from favorites")
    public ResponseEntity<ApiResponse<Void>> removeFavorite(
            @RequestHeader(value = "X-User-Id", required = false) UUID userId,
            @PathVariable UUID favId) {
        nutritionService.removeFavorite(resolve(userId), favId);
        return ResponseEntity.ok(ApiResponse.successVoid());
    }

    // ── Recent Foods ───────────────────────────────────────────────────

    @GetMapping("/food-log/recent")
    @Operation(summary = "Get recently logged foods (deduped by name)")
    public ResponseEntity<ApiResponse<List<FoodLogEntryResponse>>> getRecentFoods(
            @RequestHeader(value = "X-User-Id", required = false) UUID userId,
            @RequestParam(defaultValue = "50") int limit) {
        var raw = foodLogEntryRepo.findRecentByUserId(
            resolve(userId), org.springframework.data.domain.PageRequest.of(0, limit));
        // Dedupe by food name, keep most recent
        var seen = new java.util.LinkedHashSet<String>();
        var deduped = raw.stream()
            .filter(e -> seen.add(e.getFoodName()))
            .map(e -> FoodLogEntryResponse.builder()
                .id(e.getId()).fdcId(e.getFdcId()).foodName(e.getFoodName())
                .servingQty(e.getServingQty()).servingUnit(e.getServingUnit())
                .calories(e.getCalories()).proteinG(e.getProteinG())
                .fatG(e.getFatG()).carbsG(e.getCarbsG())
                .mealType(e.getMealType().name()).logDate(e.getLogDate())
                .build())
            .toList();
        return ResponseEntity.ok(ApiResponse.success(deduped));
    }

    private UUID resolve(UUID header) {
        return header != null ? header : appProperties.getDefaultUserId();
    }
}
