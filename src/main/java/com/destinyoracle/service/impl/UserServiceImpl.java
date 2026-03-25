package com.destinyoracle.service.impl;

import com.destinyoracle.dto.request.UpdateUserRequest;
import com.destinyoracle.dto.response.UserResponse;
import com.destinyoracle.entity.AppUser;
import com.destinyoracle.exception.ResourceNotFoundException;
import com.destinyoracle.repository.UserRepository;
import com.destinyoracle.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;

    @Override
    @Transactional(readOnly = true)
    public UserResponse getUser(UUID userId) {
        AppUser user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));
        return toResponse(user);
    }

    @Override
    @Transactional
    public UserResponse updateUser(UUID userId, UpdateUserRequest request) {
        AppUser user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));

        if (request.getDisplayName() != null) {
            user.setDisplayName(request.getDisplayName());
        }
        if (request.getTimezone() != null) {
            user.setTimezone(request.getTimezone());
        }
        if (request.getNotificationsEnabled() != null) {
            user.setNotificationsEnabled(request.getNotificationsEnabled());
        }
        if (request.getDailyReminderTime() != null) {
            user.setDailyReminderTime(request.getDailyReminderTime());
        }
        if (request.getAvatarUrl() != null) {
            user.setAvatarUrl(request.getAvatarUrl());
        }
        if (request.getChibiUrl() != null) {
            user.setChibiUrl(request.getChibiUrl());
        }
        if (request.getOnboardingComplete() != null) {
            user.setOnboardingComplete(request.getOnboardingComplete());
        }

        AppUser saved = userRepository.save(user);
        log.info("Updated user {}", userId);
        return toResponse(saved);
    }

    @Override
    @Transactional
    public UserResponse findOrCreateByEmail(String email) {
        String normalizedEmail = email.trim().toLowerCase();
        return userRepository.findByEmail(normalizedEmail)
                .map(existing -> {
                    log.info("Login: existing user found for email={} id={}", normalizedEmail, existing.getId());
                    return toResponse(existing);
                })
                .orElseGet(() -> {
                    String displayName = normalizedEmail.contains("@")
                            ? normalizedEmail.substring(0, normalizedEmail.indexOf('@'))
                            : normalizedEmail;

                    AppUser newUser = AppUser.builder()
                            .email(normalizedEmail)
                            .displayName(displayName)
                            .onboardingComplete(false)
                            .build();

                    AppUser saved = userRepository.save(newUser);
                    log.info("Login: created new user for email={} id={}", normalizedEmail, saved.getId());
                    return toResponse(saved);
                });
    }

    private UserResponse toResponse(AppUser user) {
        return UserResponse.builder()
                .id(user.getId())
                .email(user.getEmail())
                .displayName(user.getDisplayName())
                .avatarUrl(user.getAvatarUrl())
                .chibiUrl(user.getChibiUrl())
                .onboardingComplete(user.isOnboardingComplete())
                .joinedAt(user.getJoinedAt())
                .timezone(user.getTimezone())
                .notificationsEnabled(user.isNotificationsEnabled())
                .dailyReminderTime(user.getDailyReminderTime())
                .build();
    }
}
