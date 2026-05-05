package com.destinyoracle.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter @Setter
public class AddBodyCompEntryRequest {
    @NotNull
    private LocalDate logDate;

    private Double weightKg;
    private Double bodyFatPct;
    private Double muscleMassPct;
    private String notes;
}
