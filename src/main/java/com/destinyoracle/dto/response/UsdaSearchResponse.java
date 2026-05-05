package com.destinyoracle.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter @Builder
public class UsdaSearchResponse {
    private List<UsdaFoodItem> foods;
    private Integer totalHits;

    @Getter @Builder
    public static class UsdaFoodItem {
        private Integer fdcId;
        private String description;
        private String brandOwner;
        private Double calories;
        private Double proteinG;
        private Double fatG;
        private Double carbsG;
        private String servingSize;
        @Builder.Default
        private String source = "USDA";
    }
}
