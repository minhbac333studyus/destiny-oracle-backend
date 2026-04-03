package com.destinyoracle.domain.card.controller;

import com.destinyoracle.config.AppProperties;
import com.destinyoracle.dto.request.ActivityRequest;
import com.destinyoracle.dto.response.ActivityResponse;
import com.destinyoracle.dto.response.ApiResponse;
import com.destinyoracle.domain.card.service.ActivityService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/activities")
@RequiredArgsConstructor
public class ActivityController {

    private final ActivityService activityService;
    private final AppProperties appProperties;

    @PostMapping
    public ResponseEntity<ApiResponse<ActivityResponse>> logActivity(
            @RequestHeader(value = "X-User-Id", required = false) UUID userIdHeader,
            @Valid @RequestBody ActivityRequest request) {
        UUID userId = resolveUserId(userIdHeader);
        ActivityResponse response = activityService.logActivity(userId, request);
        return ResponseEntity.ok(ApiResponse.success("Activity logged", response));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<ActivityResponse>>> getActivities(
            @RequestHeader(value = "X-User-Id", required = false) UUID userIdHeader,
            @RequestParam(required = false) String aspectKey) {
        UUID userId = resolveUserId(userIdHeader);
        List<ActivityResponse> activities = activityService.getActivities(userId, aspectKey);
        return ResponseEntity.ok(ApiResponse.success(activities));
    }

    private UUID resolveUserId(UUID header) {
        return header != null ? header : appProperties.getDefaultUserId();
    }
}
