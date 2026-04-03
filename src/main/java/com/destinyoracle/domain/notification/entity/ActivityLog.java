package com.destinyoracle.domain.notification.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Simple log entry for quick-action completions (water intake, stand events).
 *
 * Used for:
 * - Counting today's progress: "6/8 glasses"
 * - Cooldown checks: "last water was 15 min ago, skip reminder"
 * - Historical tracking
 */
@Entity
@Table(name = "activity_logs", indexes = {
    @Index(name = "idx_activity_user_type_date", columnList = "userId, activityType, loggedAt")
})
@EntityListeners(AuditingEntityListener.class)
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ActivityLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID userId;

    /** Activity category: WATER, STAND, STRETCH, etc. */
    @Column(nullable = false, length = 30)
    private String activityType;

    /** Which rule triggered this (nullable — can be manually logged) */
    private UUID notificationRuleId;

    /** Which reminder was completed (nullable) */
    private UUID reminderId;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime loggedAt;
}
