package com.destinyoracle.domain.nutrition.service;

import com.destinyoracle.dto.request.HealthKitSyncRequest;

import java.util.UUID;

public interface HealthKitSyncService {
    int syncSamples(UUID userId, HealthKitSyncRequest request);
}
