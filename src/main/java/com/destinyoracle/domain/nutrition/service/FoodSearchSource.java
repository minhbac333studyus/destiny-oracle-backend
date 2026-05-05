package com.destinyoracle.domain.nutrition.service;

import com.destinyoracle.dto.response.UsdaSearchResponse;

import java.util.UUID;

/**
 * Plugin interface for food search providers.
 * Implement this + @Service to auto-register a new food source.
 */
public interface FoodSearchSource {

    /** Short code shown as a badge in the UI (e.g. "USDA", "OFF", "MY"). */
    String sourceCode();

    /** Search this source for foods matching the query. */
    UsdaSearchResponse searchFoods(String query, int pageSize, UUID userId);
}
