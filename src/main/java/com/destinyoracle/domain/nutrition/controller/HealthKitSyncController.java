package com.destinyoracle.domain.nutrition.controller;

import com.destinyoracle.config.AppProperties;
import com.destinyoracle.domain.nutrition.service.HealthKitSyncService;
import com.destinyoracle.dto.request.HealthKitSyncRequest;
import com.destinyoracle.dto.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/nutrition/healthkit")
@RequiredArgsConstructor
@Tag(name = "HealthKit Sync", description = "Receive health data from Apple Health via iOS Shortcuts")
public class HealthKitSyncController {

    private final HealthKitSyncService healthKitSyncService;
    private final AppProperties appProperties;

    @PostMapping("/sync")
    @Operation(summary = "Sync HealthKit samples",
        description = "Receives body composition and nutrition data from iOS Shortcuts. "
            + "Samples are mapped to body composition entries and food log entries.")
    public ResponseEntity<ApiResponse<Map<String, Integer>>> syncSamples(
            @RequestHeader(value = "X-User-Id", required = false) UUID userId,
            @Valid @RequestBody HealthKitSyncRequest request) {
        int count = healthKitSyncService.syncSamples(resolve(userId), request);
        return ResponseEntity.ok(ApiResponse.success(Map.of("syncedSamples", count)));
    }

    private UUID resolve(UUID header) {
        return header != null ? header : appProperties.getDefaultUserId();
    }
}
