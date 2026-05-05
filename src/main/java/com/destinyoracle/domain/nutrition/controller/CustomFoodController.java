package com.destinyoracle.domain.nutrition.controller;

import com.destinyoracle.config.AppProperties;
import com.destinyoracle.domain.nutrition.entity.CustomFood;
import com.destinyoracle.domain.nutrition.entity.MealIngredient;
import com.destinyoracle.domain.nutrition.entity.MealRecipe;
import com.destinyoracle.domain.nutrition.repository.CustomFoodRepository;
import com.destinyoracle.domain.nutrition.repository.MealRecipeRepository;
import com.destinyoracle.dto.request.AddCustomFoodRequest;
import com.destinyoracle.dto.request.AddMealRecipeRequest;
import com.destinyoracle.dto.response.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/nutrition")
@RequiredArgsConstructor
@Tag(name = "Custom Foods & Meals", description = "User's custom food database and meal recipes")
public class CustomFoodController {

    private final CustomFoodRepository customFoodRepo;
    private final MealRecipeRepository mealRecipeRepo;
    private final AppProperties appProperties;

    // ── Custom Foods ──────────────────────────────────────────────────────

    @GetMapping("/custom-foods")
    @Operation(summary = "List all custom foods")
    public ResponseEntity<ApiResponse<List<CustomFoodResponse>>> getCustomFoods(
            @RequestHeader(value = "X-User-Id", required = false) UUID userId) {
        List<CustomFoodResponse> foods = customFoodRepo.findByUserIdOrderByFoodName(resolve(userId))
            .stream().map(this::toCustomFoodResponse).toList();
        return ResponseEntity.ok(ApiResponse.success(foods));
    }

    @GetMapping("/custom-foods/search")
    @Operation(summary = "Search custom foods by name")
    public ResponseEntity<ApiResponse<List<CustomFoodResponse>>> searchCustomFoods(
            @RequestHeader(value = "X-User-Id", required = false) UUID userId,
            @RequestParam String query) {
        List<CustomFoodResponse> foods = customFoodRepo
            .findByUserIdAndFoodNameContainingIgnoreCaseOrderByFoodName(resolve(userId), query)
            .stream().map(this::toCustomFoodResponse).toList();
        return ResponseEntity.ok(ApiResponse.success(foods));
    }

    @PostMapping("/custom-foods")
    @Operation(summary = "Add a custom food to your database")
    public ResponseEntity<ApiResponse<CustomFoodResponse>> addCustomFood(
            @RequestHeader(value = "X-User-Id", required = false) UUID userId,
            @Valid @RequestBody AddCustomFoodRequest req) {
        CustomFood food = CustomFood.builder()
            .userId(resolve(userId))
            .foodName(req.getFoodName())
            .servingSize(req.getServingSize())
            .servingUnit(req.getServingUnit())
            .calories(req.getCalories())
            .proteinG(req.getProteinG())
            .fatG(req.getFatG())
            .carbsG(req.getCarbsG())
            .sugarG(req.getSugarG())
            .favorite(req.getFavorite() != null ? req.getFavorite() : false)
            .build();
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.success(toCustomFoodResponse(customFoodRepo.save(food))));
    }

    @PutMapping("/custom-foods/{id}")
    @Operation(summary = "Update a custom food")
    @Transactional
    public ResponseEntity<ApiResponse<CustomFoodResponse>> updateCustomFood(
            @RequestHeader(value = "X-User-Id", required = false) UUID userId,
            @PathVariable UUID id,
            @Valid @RequestBody AddCustomFoodRequest req) {
        CustomFood food = customFoodRepo.findById(id)
            .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException("Custom food not found"));
        food.setFoodName(req.getFoodName());
        food.setServingSize(req.getServingSize());
        food.setServingUnit(req.getServingUnit());
        food.setCalories(req.getCalories());
        food.setProteinG(req.getProteinG());
        food.setFatG(req.getFatG());
        food.setCarbsG(req.getCarbsG());
        food.setSugarG(req.getSugarG());
        if (req.getFavorite() != null) food.setFavorite(req.getFavorite());
        return ResponseEntity.ok(ApiResponse.success(toCustomFoodResponse(customFoodRepo.save(food))));
    }

    @PatchMapping("/custom-foods/{id}/toggle-favorite")
    @Operation(summary = "Toggle favorite status of a custom food")
    @Transactional
    public ResponseEntity<ApiResponse<CustomFoodResponse>> toggleFavorite(
            @RequestHeader(value = "X-User-Id", required = false) UUID userId,
            @PathVariable UUID id) {
        CustomFood food = customFoodRepo.findById(id)
            .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException("Custom food not found"));
        food.setFavorite(!Boolean.TRUE.equals(food.getFavorite()));
        return ResponseEntity.ok(ApiResponse.success(toCustomFoodResponse(customFoodRepo.save(food))));
    }

    @GetMapping("/custom-foods/favorites")
    @Operation(summary = "List favorite custom foods")
    public ResponseEntity<ApiResponse<List<CustomFoodResponse>>> getFavorites(
            @RequestHeader(value = "X-User-Id", required = false) UUID userId) {
        List<CustomFoodResponse> foods = customFoodRepo.findByUserIdAndFavoriteTrue(resolve(userId))
            .stream().map(this::toCustomFoodResponse).toList();
        return ResponseEntity.ok(ApiResponse.success(foods));
    }

    @DeleteMapping("/custom-foods/{id}")
    @Operation(summary = "Delete a custom food")
    @Transactional
    public ResponseEntity<ApiResponse<Void>> deleteCustomFood(
            @RequestHeader(value = "X-User-Id", required = false) UUID userId,
            @PathVariable UUID id) {
        customFoodRepo.deleteByIdAndUserId(id, resolve(userId));
        return ResponseEntity.ok(ApiResponse.successVoid());
    }

    // ── Meal Recipes ──────────────────────────────────────────────────────

    @GetMapping("/meals")
    @Operation(summary = "List all meal recipes")
    public ResponseEntity<ApiResponse<List<MealRecipeResponse>>> getMeals(
            @RequestHeader(value = "X-User-Id", required = false) UUID userId) {
        List<MealRecipeResponse> meals = mealRecipeRepo.findByUserIdOrderByCreatedAtDesc(resolve(userId))
            .stream().map(this::toMealResponse).toList();
        return ResponseEntity.ok(ApiResponse.success(meals));
    }

    @PostMapping("/meals")
    @Operation(summary = "Create a meal recipe with ingredients")
    @Transactional
    public ResponseEntity<ApiResponse<MealRecipeResponse>> addMeal(
            @RequestHeader(value = "X-User-Id", required = false) UUID userId,
            @Valid @RequestBody AddMealRecipeRequest req) {
        MealRecipe meal = MealRecipe.builder()
            .userId(resolve(userId))
            .mealName(req.getMealName())
            .servings(req.getServings() != null ? req.getServings() : 1)
            .build();

        for (var ing : req.getIngredients()) {
            meal.getIngredients().add(MealIngredient.builder()
                .foodName(ing.getFoodName())
                .qty(ing.getQty())
                .unit(ing.getUnit())
                .calories(ing.getCalories())
                .proteinG(ing.getProteinG())
                .fatG(ing.getFatG())
                .carbsG(ing.getCarbsG())
                .sugarG(ing.getSugarG())
                .build());
        }

        meal.recalcTotals();
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.success(toMealResponse(mealRecipeRepo.save(meal))));
    }

    @PutMapping("/meals/{id}")
    @Operation(summary = "Update a meal recipe")
    @Transactional
    public ResponseEntity<ApiResponse<MealRecipeResponse>> updateMeal(
            @RequestHeader(value = "X-User-Id", required = false) UUID userId,
            @PathVariable UUID id,
            @Valid @RequestBody AddMealRecipeRequest req) {
        MealRecipe meal = mealRecipeRepo.findById(id)
            .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException("Meal not found"));
        meal.setMealName(req.getMealName());
        meal.setServings(req.getServings() != null ? req.getServings() : 1);
        meal.getIngredients().clear();
        for (var ing : req.getIngredients()) {
            meal.getIngredients().add(MealIngredient.builder()
                .foodName(ing.getFoodName())
                .qty(ing.getQty())
                .unit(ing.getUnit())
                .calories(ing.getCalories())
                .proteinG(ing.getProteinG())
                .fatG(ing.getFatG())
                .carbsG(ing.getCarbsG())
                .sugarG(ing.getSugarG())
                .build());
        }
        meal.recalcTotals();
        return ResponseEntity.ok(ApiResponse.success(toMealResponse(mealRecipeRepo.save(meal))));
    }

    @DeleteMapping("/meals/{id}")
    @Operation(summary = "Delete a meal recipe")
    @Transactional
    public ResponseEntity<ApiResponse<Void>> deleteMeal(
            @RequestHeader(value = "X-User-Id", required = false) UUID userId,
            @PathVariable UUID id) {
        mealRecipeRepo.deleteByIdAndUserId(id, resolve(userId));
        return ResponseEntity.ok(ApiResponse.successVoid());
    }

    // ── Mappers ───────────────────────────────────────────────────────────

    private CustomFoodResponse toCustomFoodResponse(CustomFood f) {
        return CustomFoodResponse.builder()
            .id(f.getId())
            .foodName(f.getFoodName())
            .servingSize(f.getServingSize())
            .servingUnit(f.getServingUnit())
            .calories(f.getCalories())
            .proteinG(f.getProteinG())
            .fatG(f.getFatG())
            .carbsG(f.getCarbsG())
            .sugarG(f.getSugarG())
            .favorite(f.getFavorite())
            .build();
    }

    private MealRecipeResponse toMealResponse(MealRecipe m) {
        int s = m.getServings() != null && m.getServings() > 0 ? m.getServings() : 1;
        return MealRecipeResponse.builder()
            .id(m.getId())
            .mealName(m.getMealName())
            .servings(s)
            .totalCalories(m.getTotalCalories())
            .totalProteinG(m.getTotalProteinG())
            .totalFatG(m.getTotalFatG())
            .totalCarbsG(m.getTotalCarbsG())
            .totalSugarG(m.getTotalSugarG())
            .caloriesPerServing(safe(m.getTotalCalories()) / s)
            .proteinPerServing(safe(m.getTotalProteinG()) / s)
            .fatPerServing(safe(m.getTotalFatG()) / s)
            .carbsPerServing(safe(m.getTotalCarbsG()) / s)
            .ingredients(m.getIngredients().stream().map(i ->
                MealRecipeResponse.IngredientResponse.builder()
                    .id(i.getId())
                    .foodName(i.getFoodName())
                    .qty(i.getQty())
                    .unit(i.getUnit())
                    .calories(i.getCalories())
                    .proteinG(i.getProteinG())
                    .fatG(i.getFatG())
                    .carbsG(i.getCarbsG())
                    .sugarG(i.getSugarG())
                    .build()
            ).toList())
            .build();
    }

    private static double safe(Double v) { return v != null ? v : 0.0; }

    private UUID resolve(UUID header) {
        return header != null ? header : appProperties.getDefaultUserId();
    }
}
