package com.destinyoracle.domain.nutrition.service.impl;

import com.destinyoracle.domain.nutrition.service.OpenFoodFactsService;
import com.destinyoracle.dto.response.UsdaSearchResponse;
import com.destinyoracle.dto.response.UsdaSearchResponse.UsdaFoodItem;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.*;

@Slf4j
@Service
@org.springframework.core.annotation.Order(20)
public class OpenFoodFactsServiceImpl implements OpenFoodFactsService {

    private final WebClient webClient;

    public OpenFoodFactsServiceImpl() {
        this.webClient = WebClient.builder()
            .baseUrl("https://world.openfoodfacts.org")
            .defaultHeader("User-Agent", "DestinyOracle/1.0")
            .build();
    }

    @Override
    @Cacheable(value = "offSearch", key = "#query + '-' + #pageSize")
    public UsdaSearchResponse searchFoods(String query, int pageSize) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> raw = webClient.get()
                .uri(uri -> uri.path("/cgi/search.pl")
                    .queryParam("search_terms", query)
                    .queryParam("search_simple", 1)
                    .queryParam("action", "process")
                    .queryParam("json", 1)
                    .queryParam("page_size", pageSize)
                    .queryParam("fields", "code,product_name,brands,nutriments,serving_size")
                    .build())
                .retrieve()
                .bodyToMono(Map.class)
                .block();

            if (raw == null) {
                return UsdaSearchResponse.builder().foods(List.of()).totalHits(0).build();
            }

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> products = (List<Map<String, Object>>) raw.get("products");
            if (products == null) products = List.of();

            Object countObj = raw.get("count");
            int totalHits = countObj instanceof Number n ? n.intValue() : 0;

            List<UsdaFoodItem> foods = products.stream()
                .map(this::mapProduct)
                .filter(f -> f.getDescription() != null && !f.getDescription().isBlank())
                .toList();

            return UsdaSearchResponse.builder()
                .foods(foods)
                .totalHits(totalHits)
                .build();
        } catch (Exception e) {
            log.error("OpenFoodFacts search failed for '{}': {}", query, e.getMessage());
            return UsdaSearchResponse.builder().foods(List.of()).totalHits(0).build();
        }
    }

    private UsdaFoodItem mapProduct(Map<String, Object> product) {
        @SuppressWarnings("unchecked")
        Map<String, Object> nutriments = (Map<String, Object>) product.get("nutriments");
        if (nutriments == null) nutriments = Map.of();

        String code = Objects.toString(product.get("code"), null);
        Integer fdcId = null;
        if (code != null) {
            try { fdcId = Integer.parseInt(code.substring(0, Math.min(code.length(), 9))); }
            catch (NumberFormatException ignored) {}
        }

        String servingSize = Objects.toString(product.get("serving_size"), "100 g");

        return UsdaFoodItem.builder()
            .fdcId(fdcId)
            .description((String) product.get("product_name"))
            .brandOwner((String) product.get("brands"))
            .calories(toDouble(nutriments.get("energy-kcal_100g")))
            .proteinG(toDouble(nutriments.get("proteins_100g")))
            .fatG(toDouble(nutriments.get("fat_100g")))
            .carbsG(toDouble(nutriments.get("carbohydrates_100g")))
            .servingSize(servingSize)
            .source("OFF")
            .build();
    }

    @Override
    @Cacheable(value = "offBarcode", key = "#barcode")
    public UsdaFoodItem lookupBarcode(String barcode) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> raw = webClient.get()
                .uri("/api/v0/product/{barcode}.json", barcode)
                .retrieve()
                .bodyToMono(Map.class)
                .block();

            if (raw == null) return null;

            Object status = raw.get("status");
            if (status instanceof Number n && n.intValue() == 0) {
                log.info("Barcode {} not found in OpenFoodFacts", barcode);
                return null;
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> product = (Map<String, Object>) raw.get("product");
            if (product == null) return null;

            return mapProduct(product);
        } catch (Exception e) {
            log.error("Barcode lookup failed for '{}': {}", barcode, e.getMessage());
            return null;
        }
    }

    @Override
    public String sourceCode() { return "OFF"; }

    @Override
    public UsdaSearchResponse searchFoods(String query, int pageSize, UUID userId) {
        return searchFoods(query, pageSize);
    }

    private static Double toDouble(Object o) {
        if (o instanceof Number n) return n.doubleValue();
        return null;
    }
}
