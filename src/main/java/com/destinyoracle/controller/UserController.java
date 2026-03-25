package com.destinyoracle.controller;

import com.destinyoracle.dto.request.UpdateUserRequest;
import com.destinyoracle.dto.response.ApiResponse;
import com.destinyoracle.dto.response.UserResponse;
import com.destinyoracle.service.AvatarService;
import com.destinyoracle.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final AvatarService avatarService;

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<UserResponse>> getUser(@PathVariable UUID id) {
        UserResponse user = userService.getUser(id);
        return ResponseEntity.ok(ApiResponse.success(user));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<UserResponse>> updateUser(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateUserRequest request) {
        UserResponse user = userService.updateUser(id, request);
        return ResponseEntity.ok(ApiResponse.success("User updated", user));
    }

    @PostMapping(value = "/{id}/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<UserResponse>> uploadAvatar(
            @PathVariable UUID id,
            @RequestParam("file") MultipartFile file) {
        UserResponse user = avatarService.uploadAvatarAndGenerateChibi(id, file);
        return ResponseEntity.ok(ApiResponse.success(user));
    }

    @PostMapping("/{id}/generate-chibi")
    public ResponseEntity<ApiResponse<UserResponse>> generateChibi(@PathVariable UUID id) {
        UserResponse user = avatarService.generateChibiFromAvatar(id);
        return ResponseEntity.ok(ApiResponse.success(user));
    }
}
