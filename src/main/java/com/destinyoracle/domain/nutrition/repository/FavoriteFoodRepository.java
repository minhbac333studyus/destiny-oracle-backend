package com.destinyoracle.domain.nutrition.repository;

import com.destinyoracle.domain.nutrition.entity.FavoriteFood;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FavoriteFoodRepository extends JpaRepository<FavoriteFood, UUID> {
    List<FavoriteFood> findByUserIdOrderByFoodName(UUID userId);
    Optional<FavoriteFood> findByUserIdAndFdcId(UUID userId, Integer fdcId);
    void deleteByIdAndUserId(UUID id, UUID userId);
}
