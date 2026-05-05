package com.destinyoracle.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class AddCustomFoodRequest {
    @NotBlank
    private String foodName;

    private Double servingSize = 100.0;
    private String servingUnit = "g";
    private Double calories;
    private Double proteinG;
    private Double fatG;
    private Double carbsG;
    private Double sugarG;
    private Boolean favorite = false;
}
