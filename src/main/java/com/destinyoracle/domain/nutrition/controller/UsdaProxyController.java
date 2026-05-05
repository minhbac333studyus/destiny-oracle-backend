package com.destinyoracle.domain.nutrition.controller;

import com.destinyoracle.config.AppProperties;
import com.destinyoracle.domain.nutrition.service.FoodSearchSource;
import com.destinyoracle.domain.nutrition.service.OpenFoodFactsService;
import com.destinyoracle.dto.response.ApiResponse;
import com.destinyoracle.dto.response.UsdaSearchResponse;
import com.destinyoracle.dto.response.UsdaSearchResponse.UsdaFoodItem;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/nutrition/usda")
@RequiredArgsConstructor
@Tag(name = "Food Search", description = "Combined food search across all registered sources")
public class UsdaProxyController {

    private final List<FoodSearchSource> foodSources; // Spring auto-collects, ordered by @Order
    private final OpenFoodFactsService openFoodFactsService; // barcode lookup only
    private final AppProperties appProperties;

    @GetMapping("/search")
    @Operation(summary = "Search food databases", description = "Searches all registered food sources and merges results.")
    public ResponseEntity<ApiResponse<UsdaSearchResponse>> searchFoods(
            @RequestParam String query,
            @RequestParam(defaultValue = "15") int pageSize,
            @Parameter(description = "User UUID") @RequestHeader(value = "X-User-Id", required = false) UUID userId) {

        UUID resolvedUserId = resolve(userId);
        List<UsdaFoodItem> merged = new ArrayList<>();
        int totalHits = 0;

        for (FoodSearchSource source : foodSources) {
            UsdaSearchResponse result = source.searchFoods(query, pageSize, resolvedUserId);
            merged.addAll(result.getFoods());
            totalHits += result.getTotalHits() != null ? result.getTotalHits() : 0;
        }

        // Sort by relevance: exact match > starts-with > contains > rest
        merged.sort(relevanceComparator(query));

        return ResponseEntity.ok(ApiResponse.success(
            UsdaSearchResponse.builder().foods(merged).totalHits(totalHits).build()));
    }

    /** Scores lower = better match. Exact > starts-with > contains > rest. */
    private static Comparator<UsdaFoodItem> relevanceComparator(String query) {
        String q = query.toLowerCase(Locale.ROOT).trim();
        return Comparator.comparingInt((UsdaFoodItem item) -> {
            String desc = item.getDescription() != null
                ? item.getDescription().toLowerCase(Locale.ROOT) : "";
            if (desc.equals(q)) return 0;                // exact match
            if (desc.startsWith(q)) return 1;             // starts with query
            if (desc.contains(q)) return 2;               // contains query
            // check each word in description starts with query
            for (String word : desc.split("\\s+")) {
                if (word.startsWith(q)) return 3;
            }
            return 4;                                     // fuzzy / API match
        });
    }

    @GetMapping("/barcode/{barcode}")
    @Operation(summary = "Lookup food by barcode", description = "Looks up a product by barcode via OpenFoodFacts.")
    public ResponseEntity<ApiResponse<UsdaFoodItem>> lookupBarcode(@PathVariable String barcode) {
        UsdaFoodItem item = openFoodFactsService.lookupBarcode(barcode);
        if (item == null) {
            return ResponseEntity.ok(ApiResponse.error("Product not found for barcode: " + barcode));
        }
        return ResponseEntity.ok(ApiResponse.success(item));
    }

    private UUID resolve(UUID header) {
        return header != null ? header : appProperties.getDefaultUserId();
    }
}
