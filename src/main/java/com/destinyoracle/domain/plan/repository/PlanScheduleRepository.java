package com.destinyoracle.domain.plan.repository;

import com.destinyoracle.domain.plan.entity.PlanSchedule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PlanScheduleRepository extends JpaRepository<PlanSchedule, UUID> {

    List<PlanSchedule> findByUserIdAndDayOfWeekAndActiveTrue(UUID userId, DayOfWeek day);

    List<PlanSchedule> findByDayOfWeekAndActiveTrue(DayOfWeek day);

    List<PlanSchedule> findBySavedPlanIdAndActiveTrue(UUID savedPlanId);

    /**
     * Find the next scheduled item after a given time today.
     */
    @Query("""
        SELECT ps FROM PlanSchedule ps
        WHERE ps.userId = :userId AND ps.dayOfWeek = :day
        AND ps.timeOfDay > :afterTime AND ps.active = true
        ORDER BY ps.timeOfDay ASC
        """)
    List<PlanSchedule> findNextAfter(
        @Param("userId") UUID userId,
        @Param("day") DayOfWeek day,
        @Param("afterTime") LocalTime afterTime);
}
