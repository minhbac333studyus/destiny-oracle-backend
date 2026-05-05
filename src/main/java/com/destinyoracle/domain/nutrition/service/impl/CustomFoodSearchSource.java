package com.destinyoracle.domain.nutrition.service.impl;

import com.destinyoracle.domain.nutrition.entity.CustomFood;
import com.destinyoracle.domain.nutrition.repository.CustomFoodRepository;
import com.destinyoracle.domain.nutrition.service.FoodSearchSource;
import com.destinyoracle.dto.response.UsdaSearchResponse;
import com.destinyoracle.dto.response.UsdaSearchResponse.UsdaFoodItem;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@Order(0) // user's own foods appear first
@RequiredArgsConstructor
public class CustomFoodSearchSource implements FoodSearchSource {

    private final CustomFoodRepository customFoodRepo;

    @Override
    public String sourceCode() { return "MY"; }

    @Override
    public UsdaSearchResponse searchFoods(String query, int pageSize, UUID userId) {
        if (userId == null) {
            return UsdaSearchResponse.builder().foods(List.of()).totalHits(0).build();
        }

        List<UsdaFoodItem> foods = customFoodRepo
            .findByUserIdAndFoodNameContainingIgnoreCaseOrderByFoodName(userId, query)
            .stream()
            .map(this::map)
            .toList();

        return UsdaSearchResponse.builder().foods(foods).totalHits(foods.size()).build();
    }

    private UsdaFoodItem map(CustomFood cf) {
        String servingSize = (cf.getServingSize() != null ? cf.getServingSize().intValue() : 100)
            + " " + (cf.getServingUnit() != null ? cf.getServingUnit() : "g");
        return UsdaFoodItem.builder()
            .fdcId(null)
            .description(cf.getFoodName())
            .brandOwner("My Foods")
            .calories(cf.getCalories())
            .proteinG(cf.getProteinG())
            .fatG(cf.getFatG())
            .carbsG(cf.getCarbsG())
            .servingSize(servingSize)
            .source("MY")
            .build();
    }
}
