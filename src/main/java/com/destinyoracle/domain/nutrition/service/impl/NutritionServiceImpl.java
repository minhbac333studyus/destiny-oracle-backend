package com.destinyoracle.domain.nutrition.service.impl;

import com.destinyoracle.domain.nutrition.entity.*;
import com.destinyoracle.domain.nutrition.repository.*;
import com.destinyoracle.domain.nutrition.service.NutritionService;
import com.destinyoracle.dto.request.*;
import com.destinyoracle.dto.response.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class NutritionServiceImpl implements NutritionService {

    private final NutritionGoalRepository goalRepo;
    private final FoodLogEntryRepository foodLogRepo;
    private final BodyCompositionEntryRepository bodyCompRepo;
    private final FavoriteFoodRepository favoriteRepo;

    @Override
    @Transactional(readOnly = true)
    public NutritionGoalResponse getOrCreateGoals(UUID userId) {
        NutritionGoal goal = goalRepo.findByUserId(userId)
            .orElseGet(() -> goalRepo.save(NutritionGoal.builder().userId(userId).build()));
        return toGoalResponse(goal);
    }

    @Override
    public NutritionGoalResponse updateGoals(UUID userId, UpdateNutritionGoalRequest req) {
        NutritionGoal goal = goalRepo.findByUserId(userId)
            .orElseGet(() -> NutritionGoal.builder().userId(userId).build());

        if (req.getCalorieTarget() != null) goal.setCalorieTarget(req.getCalorieTarget());
        if (req.getProteinGrams() != null) goal.setProteinGrams(req.getProteinGrams());
        if (req.getFatGrams() != null) goal.setFatGrams(req.getFatGrams());
        if (req.getCarbGrams() != null) goal.setCarbGrams(req.getCarbGrams());
        if (req.getTargetWeightKg() != null) goal.setTargetWeightKg(req.getTargetWeightKg());
        if (req.getTargetBodyFatPct() != null) goal.setTargetBodyFatPct(req.getTargetBodyFatPct());
        if (req.getTargetMusclePct() != null) goal.setTargetMusclePct(req.getTargetMusclePct());
        if (req.getGender() != null) goal.setGender(req.getGender());
        if (req.getAge() != null) goal.setAge(req.getAge());
        if (req.getHeightCm() != null) goal.setHeightCm(req.getHeightCm());
        if (req.getActivityLevel() != null) goal.setActivityLevel(req.getActivityLevel());
        if (req.getFitnessGoal() != null) goal.setFitnessGoal(req.getFitnessGoal());

        return toGoalResponse(goalRepo.save(goal));
    }

    @Override
    @Transactional(readOnly = true)
    public List<FoodLogEntryResponse> getFoodLog(UUID userId, LocalDate date) {
        return foodLogRepo.findByUserIdAndLogDateOrderByCreatedAt(userId, date)
            .stream().map(this::toFoodLogResponse).toList();
    }

    @Override
    public FoodLogEntryResponse addFoodLogEntry(UUID userId, AddFoodLogEntryRequest req) {
        FoodLogEntry entry = FoodLogEntry.builder()
            .userId(userId)
            .logDate(req.getLogDate())
            .fdcId(req.getFdcId())
            .foodName(req.getFoodName())
            .servingQty(req.getServingQty())
            .servingUnit(req.getServingUnit())
            .calories(req.getCalories())
            .proteinG(req.getProteinG())
            .fatG(req.getFatG())
            .carbsG(req.getCarbsG())
            .mealType(req.getMealType())
            .build();
        return toFoodLogResponse(foodLogRepo.save(entry));
    }

    @Override
    public FoodLogEntryResponse updateFoodLogServingQty(UUID userId, UUID entryId, double newQty) {
        FoodLogEntry entry = foodLogRepo.findByIdAndUserId(entryId, userId)
            .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException("Food log entry not found"));
        double oldQty = safe(entry.getServingQty());
        double ratio = oldQty > 0 ? newQty / oldQty : 1.0;
        entry.setServingQty(newQty);
        entry.setCalories(safe(entry.getCalories()) * ratio);
        entry.setProteinG(safe(entry.getProteinG()) * ratio);
        entry.setFatG(safe(entry.getFatG()) * ratio);
        entry.setCarbsG(safe(entry.getCarbsG()) * ratio);
        return toFoodLogResponse(foodLogRepo.save(entry));
    }

    @Override
    public void removeFoodLogEntry(UUID userId, UUID entryId) {
        foodLogRepo.deleteByIdAndUserId(entryId, userId);
    }

    @Override
    @Transactional(readOnly = true)
    public DailyMacroSummaryResponse getDailySummary(UUID userId, LocalDate date) {
        List<FoodLogEntry> entries = foodLogRepo.findByUserIdAndLogDateOrderByCreatedAt(userId, date);
        NutritionGoal goal = goalRepo.findByUserId(userId)
            .orElse(NutritionGoal.builder().userId(userId).build());

        double totalCal = entries.stream().mapToDouble(e -> safe(e.getCalories())).sum();
        double totalPro = entries.stream().mapToDouble(e -> safe(e.getProteinG())).sum();
        double totalFat = entries.stream().mapToDouble(e -> safe(e.getFatG())).sum();
        double totalCarb = entries.stream().mapToDouble(e -> safe(e.getCarbsG())).sum();

        return DailyMacroSummaryResponse.builder()
            .totalCalories(totalCal)
            .totalProtein(totalPro)
            .totalFat(totalFat)
            .totalCarbs(totalCarb)
            .calorieTarget(goal.getCalorieTarget())
            .proteinTarget(goal.getProteinGrams())
            .fatTarget(goal.getFatGrams())
            .carbTarget(goal.getCarbGrams())
            .caloriePercent(pct(totalCal, goal.getCalorieTarget()))
            .proteinPercent(pct(totalPro, goal.getProteinGrams()))
            .fatPercent(pct(totalFat, goal.getFatGrams()))
            .carbPercent(pct(totalCarb, goal.getCarbGrams()))
            .build();
    }

    @Override
    @Transactional(readOnly = true)
    public List<BodyCompositionEntryResponse> getBodyCompHistory(UUID userId) {
        return bodyCompRepo.findByUserIdOrderByLogDateDesc(userId)
            .stream().map(this::toBodyCompResponse).toList();
    }

    @Override
    public BodyCompositionEntryResponse addBodyCompEntry(UUID userId, AddBodyCompEntryRequest req) {
        BodyCompositionEntry entry = BodyCompositionEntry.builder()
            .userId(userId)
            .logDate(req.getLogDate())
            .weightKg(req.getWeightKg())
            .bodyFatPct(req.getBodyFatPct())
            .muscleMassPct(req.getMuscleMassPct())
            .notes(req.getNotes())
            .build();
        return toBodyCompResponse(bodyCompRepo.save(entry));
    }

    @Override
    @Transactional(readOnly = true)
    public List<FavoriteFoodResponse> getFavorites(UUID userId) {
        return favoriteRepo.findByUserIdOrderByFoodName(userId)
            .stream().map(this::toFavoriteResponse).toList();
    }

    @Override
    public FavoriteFoodResponse addFavorite(UUID userId, AddFavoriteFoodRequest req) {
        if (req.getFdcId() != null) {
            var existing = favoriteRepo.findByUserIdAndFdcId(userId, req.getFdcId());
            if (existing.isPresent()) return toFavoriteResponse(existing.get());
        }
        FavoriteFood fav = FavoriteFood.builder()
            .userId(userId)
            .fdcId(req.getFdcId())
            .foodName(req.getFoodName())
            .servingQty(req.getServingQty())
            .servingUnit(req.getServingUnit())
            .calories(req.getCalories())
            .proteinG(req.getProteinG())
            .fatG(req.getFatG())
            .carbsG(req.getCarbsG())
            .build();
        return toFavoriteResponse(favoriteRepo.save(fav));
    }

    @Override
    public void removeFavorite(UUID userId, UUID favId) {
        favoriteRepo.deleteByIdAndUserId(favId, userId);
    }

    // ── Mappers ────────────────────────────────────────────────────────────

    private NutritionGoalResponse toGoalResponse(NutritionGoal g) {
        return NutritionGoalResponse.builder()
            .calorieTarget(g.getCalorieTarget())
            .proteinGrams(g.getProteinGrams())
            .fatGrams(g.getFatGrams())
            .carbGrams(g.getCarbGrams())
            .targetWeightKg(g.getTargetWeightKg())
            .targetBodyFatPct(g.getTargetBodyFatPct())
            .targetMusclePct(g.getTargetMusclePct())
            .gender(g.getGender())
            .age(g.getAge())
            .heightCm(g.getHeightCm())
            .activityLevel(g.getActivityLevel())
            .fitnessGoal(g.getFitnessGoal())
            .build();
    }

    private FoodLogEntryResponse toFoodLogResponse(FoodLogEntry e) {
        return FoodLogEntryResponse.builder()
            .id(e.getId())
            .fdcId(e.getFdcId())
            .foodName(e.getFoodName())
            .servingQty(e.getServingQty())
            .servingUnit(e.getServingUnit())
            .calories(e.getCalories())
            .proteinG(e.getProteinG())
            .fatG(e.getFatG())
            .carbsG(e.getCarbsG())
            .mealType(e.getMealType().name())
            .logDate(e.getLogDate())
            .build();
    }

    private BodyCompositionEntryResponse toBodyCompResponse(BodyCompositionEntry e) {
        return BodyCompositionEntryResponse.builder()
            .id(e.getId())
            .logDate(e.getLogDate())
            .weightKg(e.getWeightKg())
            .bodyFatPct(e.getBodyFatPct())
            .muscleMassPct(e.getMuscleMassPct())
            .notes(e.getNotes())
            .build();
    }

    private FavoriteFoodResponse toFavoriteResponse(FavoriteFood f) {
        return FavoriteFoodResponse.builder()
            .id(f.getId())
            .fdcId(f.getFdcId())
            .foodName(f.getFoodName())
            .servingQty(f.getServingQty())
            .servingUnit(f.getServingUnit())
            .calories(f.getCalories())
            .proteinG(f.getProteinG())
            .fatG(f.getFatG())
            .carbsG(f.getCarbsG())
            .build();
    }

    private static double safe(Double v) { return v != null ? v : 0.0; }

    private static int pct(double actual, int target) {
        if (target <= 0) return 0;
        return Math.min(100, (int) (actual / target * 100));
    }
}
