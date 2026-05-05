package com.destinyoracle.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.util.UUID;

@Getter @Builder
public class FoodLogEntryResponse {
    private UUID id;
    private Integer fdcId;
    private String foodName;
    private Double servingQty;
    private String servingUnit;
    private Double calories;
    private Double proteinG;
    private Double fatG;
    private Double carbsG;
    private String mealType;
    private LocalDate logDate;
}
