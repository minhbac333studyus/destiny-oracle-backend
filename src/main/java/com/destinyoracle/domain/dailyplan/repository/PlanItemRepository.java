package com.destinyoracle.domain.dailyplan.repository;

import com.destinyoracle.domain.dailyplan.entity.PlanItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

public interface PlanItemRepository extends JpaRepository<PlanItem, UUID> {

    /** Top-level items (no parent) for a plan, ordered by sortOrder. */
    List<PlanItem> findByDailyPlanIdAndParentItemIsNullOrderBySortOrderAsc(UUID dailyPlanId);

    /** Children of a specific parent item. */
    List<PlanItem> findByParentItemIdOrderBySortOrderAsc(UUID parentItemId);

    /** Items with due reminders for push/in-app notifications. */
    @Query("""
        SELECT pi FROM PlanItem pi
        JOIN pi.dailyPlan dp
        WHERE dp.userId = :userId
          AND dp.planDate = CURRENT_DATE
          AND dp.status <> 'COMPLETED'
          AND pi.reminderTime IS NOT NULL
          AND pi.reminderDismissed = false
          AND pi.status = 'PENDING'
          AND pi.reminderTime <= :now
          AND pi.parentItem IS NULL
        ORDER BY pi.reminderTime ASC
        """)
    List<PlanItem> findDueReminders(@Param("userId") UUID userId, @Param("now") LocalTime now);
}
