package com.destinyoracle.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.util.UUID;

@Getter @Builder
public class BodyCompositionEntryResponse {
    private UUID id;
    private LocalDate logDate;
    private Double weightKg;
    private Double bodyFatPct;
    private Double muscleMassPct;
    private String notes;
}
