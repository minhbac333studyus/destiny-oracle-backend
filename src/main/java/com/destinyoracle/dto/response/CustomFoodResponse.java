package com.destinyoracle.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.util.UUID;

@Getter @Builder
public class CustomFoodResponse {
    private UUID id;
    private String foodName;
    private Double servingSize;
    private String servingUnit;
    private Double calories;
    private Double proteinG;
    private Double fatG;
    private Double carbsG;
    private Double sugarG;
    private Boolean favorite;
}
