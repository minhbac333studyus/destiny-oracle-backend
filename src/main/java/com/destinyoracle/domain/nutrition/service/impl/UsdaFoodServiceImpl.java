package com.destinyoracle.domain.nutrition.service.impl;

import com.destinyoracle.domain.nutrition.service.UsdaFoodService;
import com.destinyoracle.dto.response.UsdaSearchResponse;
import com.destinyoracle.dto.response.UsdaSearchResponse.UsdaFoodItem;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.*;

@Slf4j
@Service
@org.springframework.core.annotation.Order(10)
public class UsdaFoodServiceImpl implements UsdaFoodService {

    private final WebClient webClient;
    private final String apiKey;

    public UsdaFoodServiceImpl(
            @Value("${usda.base-url:https://api.nal.usda.gov/fdc/v1}") String baseUrl,
            @Value("${usda.api-key:}") String apiKey) {
        this.apiKey = apiKey;
        this.webClient = WebClient.builder().baseUrl(baseUrl).build();
    }

    @Override
    @Cacheable(value = "usdaSearch", key = "#query + '-' + #pageSize")
    public UsdaSearchResponse searchFoods(String query, int pageSize) {
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("USDA API key not configured — returning empty results");
            return UsdaSearchResponse.builder().foods(List.of()).totalHits(0).build();
        }

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> raw = webClient.get()
                .uri(uri -> uri.path("/foods/search")
                    .queryParam("api_key", apiKey)
                    .queryParam("query", query)
                    .queryParam("pageSize", pageSize)
                    .queryParam("dataType", "Foundation,SR Legacy,Branded")
                    .build())
                .retrieve()
                .bodyToMono(Map.class)
                .block();

            if (raw == null) {
                return UsdaSearchResponse.builder().foods(List.of()).totalHits(0).build();
            }

            Integer totalHits = (Integer) raw.get("totalHits");
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> foodsRaw = (List<Map<String, Object>>) raw.get("foods");
            if (foodsRaw == null) foodsRaw = List.of();

            List<UsdaFoodItem> foods = foodsRaw.stream().map(this::mapFood).toList();

            return UsdaSearchResponse.builder()
                .foods(foods)
                .totalHits(totalHits != null ? totalHits : 0)
                .build();
        } catch (Exception e) {
            log.error("USDA search failed for query '{}': {}", query, e.getMessage());
            return UsdaSearchResponse.builder().foods(List.of()).totalHits(0).build();
        }
    }

    private UsdaFoodItem mapFood(Map<String, Object> raw) {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> nutrients = (List<Map<String, Object>>) raw.get("foodNutrients");
        if (nutrients == null) nutrients = List.of();

        Double calories = extractNutrient(nutrients, 1008);
        Double protein  = extractNutrient(nutrients, 1003);
        Double fat      = extractNutrient(nutrients, 1004);
        Double carbs    = extractNutrient(nutrients, 1005);

        String servingSize = raw.get("servingSize") != null
            ? raw.get("servingSize") + " " + Objects.toString(raw.get("servingSizeUnit"), "g")
            : "100 g";

        return UsdaFoodItem.builder()
            .fdcId(toInt(raw.get("fdcId")))
            .description((String) raw.get("description"))
            .brandOwner((String) raw.get("brandOwner"))
            .calories(calories)
            .proteinG(protein)
            .fatG(fat)
            .carbsG(carbs)
            .servingSize(servingSize)
            .build();
    }

    private Double extractNutrient(List<Map<String, Object>> nutrients, int nutrientId) {
        return nutrients.stream()
            .filter(n -> {
                Object nid = n.get("nutrientId");
                if (nid == null) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> nested = (Map<String, Object>) n.get("nutrient");
                    if (nested != null) nid = nested.get("id");
                }
                return nid != null && toInt(nid) == nutrientId;
            })
            .map(n -> {
                Object v = n.get("value");
                if (v == null) v = n.get("amount");
                return v instanceof Number num ? num.doubleValue() : null;
            })
            .filter(Objects::nonNull)
            .findFirst()
            .orElse(null);
    }

    @Override
    public String sourceCode() { return "USDA"; }

    @Override
    public UsdaSearchResponse searchFoods(String query, int pageSize, UUID userId) {
        return searchFoods(query, pageSize);
    }

    private static int toInt(Object o) {
        if (o instanceof Number n) return n.intValue();
        return 0;
    }
}
