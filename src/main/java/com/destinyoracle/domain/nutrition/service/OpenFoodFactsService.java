package com.destinyoracle.domain.nutrition.service;

import com.destinyoracle.dto.response.UsdaSearchResponse;

public interface OpenFoodFactsService extends FoodSearchSource {
    UsdaSearchResponse searchFoods(String query, int pageSize);
    UsdaSearchResponse.UsdaFoodItem lookupBarcode(String barcode);
}
