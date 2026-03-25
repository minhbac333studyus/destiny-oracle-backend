package com.destinyoracle.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.UUID;

/** Typed wrapper for app.* properties from application.yml */
@Component
public class AppProperties {

    @Value("${app.default-user-id:00000000-0000-0000-0000-000000000001}")
    private String defaultUserId;

    public UUID getDefaultUserId() {
        return UUID.fromString(defaultUserId);
    }
}
