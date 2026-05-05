package com.destinyoracle.dto.request;

import com.destinyoracle.domain.nutrition.entity.MealType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter @Setter
public class AddFoodLogEntryRequest {
    private Integer fdcId;

    @NotBlank
    private String foodName;

    private Double servingQty = 1.0;
    private String servingUnit;
    private Double calories;
    private Double proteinG;
    private Double fatG;
    private Double carbsG;

    @NotNull
    private MealType mealType;

    @NotNull
    private LocalDate logDate;
}
