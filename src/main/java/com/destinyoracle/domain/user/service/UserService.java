package com.destinyoracle.domain.user.service;

import com.destinyoracle.dto.request.UpdateUserRequest;
import com.destinyoracle.dto.response.UserResponse;

import java.util.UUID;

public interface UserService {

    UserResponse getUser(UUID userId);

    UserResponse updateUser(UUID userId, UpdateUserRequest request);

    /** Find user by email or create a new one. Used for simple dev-mode login. */
    UserResponse findOrCreateByEmail(String email);
}
