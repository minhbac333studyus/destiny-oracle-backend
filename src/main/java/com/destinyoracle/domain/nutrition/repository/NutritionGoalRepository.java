package com.destinyoracle.domain.nutrition.repository;

import com.destinyoracle.domain.nutrition.entity.NutritionGoal;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface NutritionGoalRepository extends JpaRepository<NutritionGoal, UUID> {
    Optional<NutritionGoal> findByUserId(UUID userId);
}
