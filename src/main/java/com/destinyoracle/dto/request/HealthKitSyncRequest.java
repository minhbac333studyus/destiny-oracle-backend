package com.destinyoracle.dto.request;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter @Setter
public class HealthKitSyncRequest {
    private List<HealthKitSample> samples;

    @Getter @Setter
    public static class HealthKitSample {
        private String type;       // e.g. "bodyMass", "bodyFatPercentage", "leanBodyMass", "dietaryEnergyConsumed", "dietaryProtein", "dietaryFatTotal", "dietaryCarbohydrates"
        private Double value;
        private String unit;       // e.g. "kg", "%", "kcal", "g"
        private String date;       // ISO date string "2026-04-04"
        private String startDate;  // ISO datetime (optional, for more precision)
        private String sourceName; // e.g. "Renpho", "MyFitnessPal"
    }
}
