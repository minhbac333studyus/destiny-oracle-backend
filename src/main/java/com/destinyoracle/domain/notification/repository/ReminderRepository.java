package com.destinyoracle.domain.notification.repository;

import com.destinyoracle.domain.notification.entity.Reminder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface ReminderRepository extends JpaRepository<Reminder, UUID> {

    List<Reminder> findByUserIdAndCompletedFalseOrderByScheduledAtAsc(UUID userId);

    /**
     * Find reminders that are due NOW and haven't been sent.
     */
    @Query("""
        SELECT r FROM Reminder r
        WHERE r.scheduledAt <= :now
        AND r.notificationSent = false
        AND r.completed = false
        AND (r.snoozedUntil IS NULL OR r.snoozedUntil <= :now)
        """)
    List<Reminder> findDueReminders(@Param("now") LocalDateTime now);

    /**
     * Upcoming reminders within N hours.
     */
    @Query("""
        SELECT r FROM Reminder r
        WHERE r.userId = :userId
        AND r.completed = false
        AND r.scheduledAt BETWEEN :now AND :until
        ORDER BY r.scheduledAt ASC
        """)
    List<Reminder> findUpcoming(
        @Param("userId") UUID userId,
        @Param("now") LocalDateTime now,
        @Param("until") LocalDateTime until);

    /**
     * Count active unseen — used for badge count on push notifications.
     */
    @Query("""
        SELECT COUNT(r) FROM Reminder r
        WHERE r.userId = :userId
        AND r.completed = false
        AND r.notificationSent = true
        """)
    int countActiveUnseen(@Param("userId") UUID userId);
}
