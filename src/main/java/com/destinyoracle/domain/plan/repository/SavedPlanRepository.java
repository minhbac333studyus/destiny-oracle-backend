package com.destinyoracle.domain.plan.repository;

import com.destinyoracle.domain.plan.entity.SavedPlan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SavedPlanRepository extends JpaRepository<SavedPlan, UUID> {

    List<SavedPlan> findByUserIdAndActiveTrue(UUID userId);

    Optional<SavedPlan> findByUserIdAndSlugAndActiveTrue(UUID userId, String slug);

    @Query("SELECT p FROM SavedPlan p WHERE p.userId = :userId AND p.type = :type AND p.active = true")
    List<SavedPlan> findByUserIdAndTypeActive(
        @Param("userId") UUID userId,
        @Param("type") SavedPlan.PlanType type);

    /**
     * Version history for a plan slug.
     */
    @Query("SELECT p FROM SavedPlan p WHERE p.userId = :userId AND p.slug = :slug ORDER BY p.version DESC")
    List<SavedPlan> findVersionHistory(
        @Param("userId") UUID userId,
        @Param("slug") String slug);
}
