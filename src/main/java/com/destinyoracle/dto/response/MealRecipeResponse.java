package com.destinyoracle.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.util.List;
import java.util.UUID;

@Getter @Builder
public class MealRecipeResponse {
    private UUID id;
    private String mealName;
    private Integer servings;
    private Double totalCalories;
    private Double totalProteinG;
    private Double totalFatG;
    private Double totalCarbsG;
    private Double totalSugarG;
    // Per-serving values
    private Double caloriesPerServing;
    private Double proteinPerServing;
    private Double fatPerServing;
    private Double carbsPerServing;
    private List<IngredientResponse> ingredients;

    @Getter @Builder
    public static class IngredientResponse {
        private UUID id;
        private String foodName;
        private Double qty;
        private String unit;
        private Double calories;
        private Double proteinG;
        private Double fatG;
        private Double carbsG;
        private Double sugarG;
    }
}
