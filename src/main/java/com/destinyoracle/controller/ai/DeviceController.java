package com.destinyoracle.controller.ai;

import com.destinyoracle.dto.request.RegisterDeviceRequest;
import com.destinyoracle.service.DeviceTokenService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/devices")
@Tag(name = "Devices", description = "Push notification device token management")
public class DeviceController {

    private final DeviceTokenService deviceService;

    public DeviceController(DeviceTokenService deviceService) {
        this.deviceService = deviceService;
    }

    @PostMapping("/register")
    @Operation(summary = "Register a device for push notifications")
    public ResponseEntity<Void> registerDevice(
        @RequestHeader("X-User-Id") UUID userId,
        @Valid @RequestBody RegisterDeviceRequest request
    ) {
        deviceService.registerDevice(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @DeleteMapping("/{deviceId}")
    @Operation(summary = "Unregister a device")
    public ResponseEntity<Void> unregisterDevice(
        @RequestHeader("X-User-Id") UUID userId,
        @PathVariable UUID deviceId
    ) {
        deviceService.unregisterDevice(userId, deviceId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/count")
    @Operation(summary = "Get count of active devices")
    public ResponseEntity<Map<String, Integer>> getDeviceCount(
        @RequestHeader("X-User-Id") UUID userId
    ) {
        return ResponseEntity.ok(Map.of("activeDevices", deviceService.getActiveDeviceCount(userId)));
    }
}
