package com.destinyoracle.dto.request;

import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class UpdateNutritionGoalRequest {
    private Integer calorieTarget;
    private Integer proteinGrams;
    private Integer fatGrams;
    private Integer carbGrams;
    private Double targetWeightKg;
    private Double targetBodyFatPct;
    private Double targetMusclePct;
    private String gender;
    private Integer age;
    private Double heightCm;
    private String activityLevel;
    private String fitnessGoal;
}
