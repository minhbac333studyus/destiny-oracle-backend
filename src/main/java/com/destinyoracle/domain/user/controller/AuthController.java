package com.destinyoracle.domain.user.controller;

import com.destinyoracle.dto.request.LoginRequest;
import com.destinyoracle.dto.response.ApiResponse;
import com.destinyoracle.dto.response.UserResponse;
import com.destinyoracle.domain.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Auth", description = "Simple dev-mode authentication")
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;

    @PostMapping("/login")
    @Operation(
        summary = "Login or register by email",
        description = "Finds an existing user by email or creates a new one. " +
                      "Returns the full user object including ID and onboardingComplete status."
    )
    public ResponseEntity<ApiResponse<UserResponse>> login(
            @Valid @RequestBody LoginRequest request) {
        UserResponse user = userService.findOrCreateByEmail(request.getEmail());
        return ResponseEntity.ok(ApiResponse.success(user));
    }
}
