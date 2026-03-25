package com.destinyoracle.domain.plan.repository;

import com.destinyoracle.domain.plan.entity.NudgeState;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface NudgeStateRepository extends JpaRepository<NudgeState, UUID> {

    Optional<NudgeState> findByUserIdAndPlanScheduleIdAndNudgeDate(
        UUID userId, UUID planScheduleId, LocalDate nudgeDate);

    /**
     * Find items that are overdue today (not completed, not skipped).
     */
    @Query("""
        SELECT ns FROM NudgeState ns
        WHERE ns.userId = :userId AND ns.nudgeDate = :date
        AND ns.completed = false AND ns.skipped = false
        AND ns.nudgeLevel > 0
        """)
    List<NudgeState> findOverdueToday(
        @Param("userId") UUID userId,
        @Param("date") LocalDate date);
}
