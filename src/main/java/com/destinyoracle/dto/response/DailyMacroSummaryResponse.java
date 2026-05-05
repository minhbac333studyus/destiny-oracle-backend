package com.destinyoracle.dto.response;

import lombok.Builder;
import lombok.Getter;

@Getter @Builder
public class DailyMacroSummaryResponse {
    private Double totalCalories;
    private Double totalProtein;
    private Double totalFat;
    private Double totalCarbs;
    private Integer calorieTarget;
    private Integer proteinTarget;
    private Integer fatTarget;
    private Integer carbTarget;
    private Integer caloriePercent;
    private Integer proteinPercent;
    private Integer fatPercent;
    private Integer carbPercent;
}
