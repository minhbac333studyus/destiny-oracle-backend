package com.destinyoracle.service;

import com.destinyoracle.dto.response.UserResponse;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

public interface AvatarService {

    UserResponse uploadAvatarAndGenerateChibi(UUID userId, MultipartFile file);

    UserResponse generateChibiFromAvatar(UUID userId);
}
