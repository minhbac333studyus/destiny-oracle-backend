package com.destinyoracle.service;

import com.destinyoracle.dto.request.RegisterDeviceRequest;

import java.util.UUID;

public interface DeviceTokenService {

    void registerDevice(UUID userId, RegisterDeviceRequest request);

    void unregisterDevice(UUID userId, UUID deviceId);

    int getActiveDeviceCount(UUID userId);
}
