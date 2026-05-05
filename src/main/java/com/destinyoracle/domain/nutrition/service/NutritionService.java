package com.destinyoracle.domain.nutrition.service;

import com.destinyoracle.dto.request.*;
import com.destinyoracle.dto.response.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface NutritionService {

    NutritionGoalResponse getOrCreateGoals(UUID userId);
    NutritionGoalResponse updateGoals(UUID userId, UpdateNutritionGoalRequest request);

    List<FoodLogEntryResponse> getFoodLog(UUID userId, LocalDate date);
    FoodLogEntryResponse addFoodLogEntry(UUID userId, AddFoodLogEntryRequest request);
    FoodLogEntryResponse updateFoodLogServingQty(UUID userId, UUID entryId, double newQty);
    void removeFoodLogEntry(UUID userId, UUID entryId);

    DailyMacroSummaryResponse getDailySummary(UUID userId, LocalDate date);

    List<BodyCompositionEntryResponse> getBodyCompHistory(UUID userId);
    BodyCompositionEntryResponse addBodyCompEntry(UUID userId, AddBodyCompEntryRequest request);

    List<FavoriteFoodResponse> getFavorites(UUID userId);
    FavoriteFoodResponse addFavorite(UUID userId, AddFavoriteFoodRequest request);
    void removeFavorite(UUID userId, UUID favId);
}
