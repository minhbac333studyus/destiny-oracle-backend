package com.destinyoracle.domain.notification.repository;

import com.destinyoracle.domain.notification.entity.DeviceToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface DeviceTokenRepository extends JpaRepository<DeviceToken, UUID> {

    List<DeviceToken> findByUserIdAndActiveTrue(UUID userId);

    List<DeviceToken> findByUserIdAndPlatformAndActiveTrue(
        UUID userId, DeviceToken.Platform platform);
}
