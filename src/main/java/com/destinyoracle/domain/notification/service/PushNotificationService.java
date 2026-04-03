package com.destinyoracle.domain.notification.service;

import java.util.UUID;

public interface PushNotificationService {

    /**
     * Send push notification to all active devices for a user.
     */
    void sendToUser(UUID userId, String title, String body);

    /**
     * Send push notification with action buttons.
     */
    void sendToUser(UUID userId, String title, String body, String actionUrl);
}
