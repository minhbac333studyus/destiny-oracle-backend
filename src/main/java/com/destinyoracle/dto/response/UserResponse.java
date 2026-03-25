package com.destinyoracle.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserResponse {

    private UUID id;
    private String email;
    private String displayName;
    private String avatarUrl;
    private String chibiUrl;
    private boolean onboardingComplete;
    private Instant joinedAt;
    private String timezone;
    private boolean notificationsEnabled;
    private String dailyReminderTime;
}
