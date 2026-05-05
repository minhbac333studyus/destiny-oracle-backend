package com.destinyoracle.domain.nutrition.service;

import com.destinyoracle.dto.response.UsdaSearchResponse;

public interface UsdaFoodService extends FoodSearchSource {
    UsdaSearchResponse searchFoods(String query, int pageSize);
}
