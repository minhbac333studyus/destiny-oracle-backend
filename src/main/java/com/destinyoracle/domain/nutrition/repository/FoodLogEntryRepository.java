package com.destinyoracle.domain.nutrition.repository;

import com.destinyoracle.domain.nutrition.entity.FoodLogEntry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface FoodLogEntryRepository extends JpaRepository<FoodLogEntry, UUID> {
    List<FoodLogEntry> findByUserIdAndLogDateOrderByCreatedAt(UUID userId, LocalDate logDate);
    java.util.Optional<FoodLogEntry> findByIdAndUserId(UUID id, UUID userId);
    void deleteByIdAndUserId(UUID id, UUID userId);

    @org.springframework.data.jpa.repository.Query(
        "SELECT f FROM FoodLogEntry f WHERE f.userId = :userId " +
        "AND f.foodName NOT LIKE '[HealthKit]%' " +
        "ORDER BY f.createdAt DESC")
    List<FoodLogEntry> findRecentByUserId(@org.springframework.data.repository.query.Param("userId") UUID userId,
                                          org.springframework.data.domain.Pageable pageable);
}
