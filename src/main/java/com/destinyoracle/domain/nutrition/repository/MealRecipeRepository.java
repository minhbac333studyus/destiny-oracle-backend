package com.destinyoracle.domain.nutrition.repository;

import com.destinyoracle.domain.nutrition.entity.MealRecipe;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface MealRecipeRepository extends JpaRepository<MealRecipe, UUID> {
    List<MealRecipe> findByUserIdOrderByCreatedAtDesc(UUID userId);
    void deleteByIdAndUserId(UUID id, UUID userId);
}
