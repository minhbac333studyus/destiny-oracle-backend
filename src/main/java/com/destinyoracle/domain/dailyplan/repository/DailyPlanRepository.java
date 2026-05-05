package com.destinyoracle.domain.dailyplan.repository;

import com.destinyoracle.domain.dailyplan.entity.DailyPlan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DailyPlanRepository extends JpaRepository<DailyPlan, UUID> {

    List<DailyPlan> findByUserIdAndPlanDateOrderByVersionDesc(UUID userId, LocalDate planDate);

    /** Get the latest (highest version) active plan for a date. */
    @Query("""
        SELECT dp FROM DailyPlan dp
        WHERE dp.userId = :userId AND dp.planDate = :date AND dp.status <> 'COMPLETED'
        ORDER BY dp.version DESC LIMIT 1
        """)
    Optional<DailyPlan> findActivePlan(@Param("userId") UUID userId, @Param("date") LocalDate date);

    /** Get the latest plan for a date regardless of status. */
    @Query("""
        SELECT dp FROM DailyPlan dp
        WHERE dp.userId = :userId AND dp.planDate = :date
        ORDER BY dp.version DESC LIMIT 1
        """)
    Optional<DailyPlan> findLatestByUserIdAndDate(@Param("userId") UUID userId, @Param("date") LocalDate date);
}
