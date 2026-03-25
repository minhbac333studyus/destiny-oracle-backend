package com.destinyoracle.service.impl;

import com.destinyoracle.domain.notification.entity.DeviceToken;
import com.destinyoracle.domain.notification.repository.DeviceTokenRepository;
import com.destinyoracle.service.PushNotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * Push notification service.
 * Currently logs notifications. APNs (Pushy) and Web Push
 * can be wired in when the push infrastructure is configured.
 *
 * TODO Phase 3: Wire in Pushy for APNs and web-push library for Web Push.
 */
@Service
public class PushNotificationServiceImpl implements PushNotificationService {

    private static final Logger log = LoggerFactory.getLogger(PushNotificationServiceImpl.class);

    private final DeviceTokenRepository deviceTokenRepo;

    public PushNotificationServiceImpl(DeviceTokenRepository deviceTokenRepo) {
        this.deviceTokenRepo = deviceTokenRepo;
    }

    @Override
    public void sendToUser(UUID userId, String title, String body) {
        sendToUser(userId, title, body, null);
    }

    @Override
    public void sendToUser(UUID userId, String title, String body, String actionUrl) {
        List<DeviceToken> devices = deviceTokenRepo.findByUserIdAndActiveTrue(userId);

        if (devices.isEmpty()) {
            log.debug("No active devices for user {}, skipping push", userId);
            return;
        }

        for (DeviceToken device : devices) {
            try {
                switch (device.getPlatform()) {
                    case IOS -> sendApns(device, title, body, actionUrl);
                    case WEB -> sendWebPush(device, title, body, actionUrl);
                }
            } catch (Exception e) {
                log.error("Push failed for device {} ({}): {}",
                    device.getId(), device.getPlatform(), e.getMessage());
                // Don't deactivate on first failure — could be transient
            }
        }
    }

    private void sendApns(DeviceToken device, String title, String body, String actionUrl) {
        // TODO Phase 3: Implement with Pushy library
        // PushNotificationFuture<SimpleApnsPushNotification, PushNotificationResponse<SimpleApnsPushNotification>>
        log.info("[APNs STUB] → {} | title='{}' body='{}'", device.getDeviceToken(), title, body);
    }

    private void sendWebPush(DeviceToken device, String title, String body, String actionUrl) {
        // TODO Phase 3: Implement with web-push library
        // Notification notification = new Notification(device.getEndpoint(), ...);
        log.info("[WebPush STUB] → {} | title='{}' body='{}'",
            device.getEndpoint() != null ? device.getEndpoint().substring(0, Math.min(50, device.getEndpoint().length())) : "null",
            title, body);
    }
}
