package com.destinyoracle.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter @Setter
public class AddMealRecipeRequest {
    @NotBlank
    private String mealName;

    private Integer servings = 1;

    @NotEmpty
    private List<IngredientInput> ingredients;

    @Getter @Setter
    public static class IngredientInput {
        private String foodName;
        private Double qty = 1.0;
        private String unit = "g";
        private Double calories;
        private Double proteinG;
        private Double fatG;
        private Double carbsG;
        private Double sugarG;
    }
}
