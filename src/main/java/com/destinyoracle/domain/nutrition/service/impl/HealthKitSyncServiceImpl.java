package com.destinyoracle.domain.nutrition.service.impl;

import com.destinyoracle.domain.nutrition.entity.BodyCompositionEntry;
import com.destinyoracle.domain.nutrition.entity.FoodLogEntry;
import com.destinyoracle.domain.nutrition.entity.MealType;
import com.destinyoracle.domain.nutrition.repository.BodyCompositionEntryRepository;
import com.destinyoracle.domain.nutrition.repository.FoodLogEntryRepository;
import com.destinyoracle.domain.nutrition.service.HealthKitSyncService;
import com.destinyoracle.dto.request.HealthKitSyncRequest;
import com.destinyoracle.dto.request.HealthKitSyncRequest.HealthKitSample;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class HealthKitSyncServiceImpl implements HealthKitSyncService {

    private final BodyCompositionEntryRepository bodyCompRepo;
    private final FoodLogEntryRepository foodLogRepo;

    // HealthKit type identifiers (shortened names used in Shortcuts)
    private static final Set<String> BODY_COMP_TYPES = Set.of(
        "bodyMass", "bodyFatPercentage", "leanBodyMass", "muscleMass",
        "bmi", "bodyMassIndex", "metabolicAge", "visceralFat"
    );

    private static final Set<String> NUTRITION_TYPES = Set.of(
        "dietaryEnergyConsumed", "dietaryProtein", "dietaryFatTotal",
        "dietaryCarbohydrates", "dietaryFiber", "dietarySugar"
    );

    @Override
    public int syncSamples(UUID userId, HealthKitSyncRequest request) {
        if (request.getSamples() == null || request.getSamples().isEmpty()) return 0;

        // Group samples by date
        Map<LocalDate, List<HealthKitSample>> byDate = new LinkedHashMap<>();
        for (HealthKitSample s : request.getSamples()) {
            LocalDate date = parseDate(s);
            if (date == null) continue;
            byDate.computeIfAbsent(date, k -> new ArrayList<>()).add(s);
        }

        int count = 0;
        for (var entry : byDate.entrySet()) {
            LocalDate date = entry.getKey();
            List<HealthKitSample> samples = entry.getValue();

            // Process body composition samples
            List<HealthKitSample> bodyComp = samples.stream()
                .filter(s -> BODY_COMP_TYPES.contains(s.getType()))
                .toList();
            if (!bodyComp.isEmpty()) {
                syncBodyComp(userId, date, bodyComp);
                count += bodyComp.size();
            }

            // Process nutrition samples → food log entries
            List<HealthKitSample> nutrition = samples.stream()
                .filter(s -> NUTRITION_TYPES.contains(s.getType()))
                .toList();
            if (!nutrition.isEmpty()) {
                syncNutrition(userId, date, nutrition);
                count += nutrition.size();
            }
        }

        log.info("Synced {} HealthKit samples for user {}", count, userId);
        return count;
    }

    private void syncBodyComp(UUID userId, LocalDate date, List<HealthKitSample> samples) {
        // Check if there's already an entry for this date from HealthKit
        var existing = bodyCompRepo.findByUserIdOrderByLogDateDesc(userId).stream()
            .filter(e -> e.getLogDate().equals(date) && e.getNotes() != null && e.getNotes().contains("[HealthKit]"))
            .findFirst();

        BodyCompositionEntry entry = existing.orElseGet(() ->
            BodyCompositionEntry.builder().userId(userId).logDate(date).build());

        for (HealthKitSample s : samples) {
            switch (s.getType()) {
                case "bodyMass" -> entry.setWeightKg(convertToKg(s));
                case "bodyFatPercentage" -> entry.setBodyFatPct(s.getValue());
                case "leanBodyMass", "muscleMass" -> entry.setMuscleMassPct(s.getValue());
            }
        }

        String source = samples.stream()
            .map(HealthKitSample::getSourceName)
            .filter(Objects::nonNull)
            .findFirst().orElse("HealthKit");
        entry.setNotes("[HealthKit] via " + source);

        bodyCompRepo.save(entry);
    }

    private void syncNutrition(UUID userId, LocalDate date, List<HealthKitSample> samples) {
        Double calories = null, protein = null, fat = null, carbs = null;
        String source = "HealthKit";

        for (HealthKitSample s : samples) {
            switch (s.getType()) {
                case "dietaryEnergyConsumed" -> calories = s.getValue();
                case "dietaryProtein" -> protein = s.getValue();
                case "dietaryFatTotal" -> fat = s.getValue();
                case "dietaryCarbohydrates" -> carbs = s.getValue();
            }
            if (s.getSourceName() != null) source = s.getSourceName();
        }

        // Only create a food log entry if we have at least calories
        if (calories == null || calories <= 0) return;

        // Check for existing HealthKit entry for this date
        var existingEntries = foodLogRepo.findByUserIdAndLogDateOrderByCreatedAt(userId, date);
        boolean alreadySynced = existingEntries.stream()
            .anyMatch(e -> e.getFoodName() != null && e.getFoodName().startsWith("[HealthKit]"));

        if (alreadySynced) {
            // Update existing
            var entry = existingEntries.stream()
                .filter(e -> e.getFoodName() != null && e.getFoodName().startsWith("[HealthKit]"))
                .findFirst().get();
            entry.setCalories(calories);
            entry.setProteinG(protein);
            entry.setFatG(fat);
            entry.setCarbsG(carbs);
            foodLogRepo.save(entry);
        } else {
            // Create new
            foodLogRepo.save(FoodLogEntry.builder()
                .userId(userId)
                .logDate(date)
                .foodName("[HealthKit] Daily intake via " + source)
                .servingQty(1.0)
                .servingUnit("day")
                .calories(calories)
                .proteinG(protein)
                .fatG(fat)
                .carbsG(carbs)
                .mealType(MealType.SNACK)
                .build());
        }
    }

    private Double convertToKg(HealthKitSample s) {
        if (s.getValue() == null) return null;
        if ("lb".equalsIgnoreCase(s.getUnit()) || "lbs".equalsIgnoreCase(s.getUnit())) {
            return s.getValue() * 0.453592;
        }
        return s.getValue(); // assume kg
    }

    private LocalDate parseDate(HealthKitSample s) {
        try {
            if (s.getDate() != null) return LocalDate.parse(s.getDate());
            if (s.getStartDate() != null) return LocalDate.parse(s.getStartDate().substring(0, 10));
        } catch (Exception e) {
            log.warn("Could not parse date from sample: {}", e.getMessage());
        }
        return null;
    }
}
