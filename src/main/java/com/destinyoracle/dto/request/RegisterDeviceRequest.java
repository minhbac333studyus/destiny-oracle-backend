package com.destinyoracle.dto.request;

import com.destinyoracle.domain.notification.entity.DeviceToken;
import jakarta.validation.constraints.NotNull;

public record RegisterDeviceRequest(
    @NotNull DeviceToken.Platform platform,
    String deviceToken,     // iOS APNs token
    String endpoint,        // Web Push endpoint
    String p256dhKey,       // Web Push p256dh key
    String authKey          // Web Push auth key
) {}
