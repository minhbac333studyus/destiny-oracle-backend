package com.destinyoracle.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class AddFavoriteFoodRequest {
    private Integer fdcId;

    @NotBlank
    private String foodName;

    private Double servingQty = 1.0;
    private String servingUnit;
    private Double calories;
    private Double proteinG;
    private Double fatG;
    private Double carbsG;
}
