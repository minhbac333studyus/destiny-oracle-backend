package com.destinyoracle.domain.nutrition.repository;

import com.destinyoracle.domain.nutrition.entity.CustomFood;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CustomFoodRepository extends JpaRepository<CustomFood, UUID> {
    List<CustomFood> findByUserIdOrderByFoodName(UUID userId);
    List<CustomFood> findByUserIdAndFoodNameContainingIgnoreCaseOrderByFoodName(UUID userId, String query);
    List<CustomFood> findByUserIdAndFavoriteTrue(UUID userId);
    void deleteByIdAndUserId(UUID id, UUID userId);
}
