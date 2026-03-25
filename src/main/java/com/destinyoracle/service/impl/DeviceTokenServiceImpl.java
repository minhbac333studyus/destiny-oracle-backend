package com.destinyoracle.service.impl;

import com.destinyoracle.domain.notification.entity.DeviceToken;
import com.destinyoracle.domain.notification.repository.DeviceTokenRepository;
import com.destinyoracle.dto.request.RegisterDeviceRequest;
import com.destinyoracle.service.DeviceTokenService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class DeviceTokenServiceImpl implements DeviceTokenService {

    private final DeviceTokenRepository tokenRepo;

    public DeviceTokenServiceImpl(DeviceTokenRepository tokenRepo) {
        this.tokenRepo = tokenRepo;
    }

    @Override
    @Transactional
    public void registerDevice(UUID userId, RegisterDeviceRequest request) {
        DeviceToken token = DeviceToken.builder()
            .userId(userId)
            .platform(request.platform())
            .deviceToken(request.deviceToken())
            .endpoint(request.endpoint())
            .p256dhKey(request.p256dhKey())
            .authKey(request.authKey())
            .build();
        tokenRepo.save(token);
    }

    @Override
    @Transactional
    public void unregisterDevice(UUID userId, UUID deviceId) {
        var token = tokenRepo.findById(deviceId)
            .orElseThrow(() -> new RuntimeException("Device not found"));
        if (!token.getUserId().equals(userId)) throw new RuntimeException("Access denied");
        token.setActive(false);
        tokenRepo.save(token);
    }

    @Override
    public int getActiveDeviceCount(UUID userId) {
        return tokenRepo.findByUserIdAndActiveTrue(userId).size();
    }
}
