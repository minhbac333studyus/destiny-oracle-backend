package com.destinyoracle.domain.notification.entity;

import com.destinyoracle.domain.task.entity.Task;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.UUID;

/**
 * User-configurable rule that drives the SmartNotificationScheduler.
 *
 * Each rule defines WHAT to notify about, WHEN to fire, and HOW to create
 * the resulting Task or Reminder. The scheduler evaluates all active rules
 * every 5 minutes and fires those that are due.
 *
 * Water intake rules use {@code bedtime}/{@code wakeTime} to auto-calculate
 * optimal reminder times (wake+30m, midday, bedtime-3h).
 */
@Entity
@Table(name = "notification_rules", indexes = {
    @Index(name = "idx_notif_rule_user_active", columnList = "userId, active")
})
@EntityListeners(AuditingEntityListener.class)
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotificationRule {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID userId;

    /** Short display name: "Drink water", "Evening workout" */
    @Column(nullable = false, length = 255)
    private String name;

    /** AI context for TASK-type rules: "Push day with chest and triceps focus" */
    @Column(columnDefinition = "TEXT")
    private String description;

    /** Category maps to Task.TaskCategory for TASK outputs */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Task.TaskCategory category;

    /** What to create when this rule fires */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private OutputType outputType = OutputType.REMINDER;

    /** Schedule type determines how {@code shouldFire()} evaluates timing */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private ScheduleType schedule = ScheduleType.DAILY;

    /** For INTERVAL schedule: fire every N minutes */
    private Integer intervalMinutes;

    /** For DAILY/WEEKLY: preferred time of day. For WATER: overridden by bedtime calc */
    private LocalTime timeOfDay;

    /** For WEEKLY schedule: which day */
    @Enumerated(EnumType.STRING)
    private DayOfWeek dayOfWeek;

    // ── Sleep-aware timing (for WATER rules) ──────────────────────────────

    /** User's typical bedtime — used to calculate last water cutoff (bedtime - 3h) */
    private LocalTime bedtime;

    /** User's typical wake time — used to calculate first water reminder (wake + 30m) */
    private LocalTime wakeTime;

    // ── Smart suppression ─────────────────────────────────────────────────

    /** No notifications after this time */
    private LocalTime quietStart;

    /** No notifications before this time */
    private LocalTime quietEnd;

    /** Max fires per day. Null = unlimited. Water default: 3 */
    private Integer dailyQuota;

    /** Minimum minutes between fires. Null = no cooldown */
    private Integer cooldownMinutes;

    /** 1=highest, 5=lowest. Used for display ordering */
    @Column(nullable = false)
    @Builder.Default
    private Integer priority = 3;

    /** If true, show one-tap "Done" button (for water, stand) */
    @Column(nullable = false)
    @Builder.Default
    private Boolean quickAction = false;

    /** If true, skip notification when a WORKOUT task has recent activity */
    @Column(nullable = false)
    @Builder.Default
    private Boolean suppressDuringWorkout = false;

    @Column(nullable = false)
    @Builder.Default
    private Boolean active = true;

    /** Counter reset daily by scheduler */
    @Column(nullable = false)
    @Builder.Default
    private Integer firedToday = 0;

    /** Last date the firedToday counter was reset */
    private java.time.LocalDate firedTodayDate;

    private LocalDateTime lastFiredAt;

    /** Cached AI-generated task steps JSON — reused on recurring fires to save tokens */
    @Column(columnDefinition = "TEXT")
    private String cachedSteps;

    @CreatedDate
    @Column(updatable = false)
    private LocalDateTime createdAt;

    // ── Enums ─────────────────────────────────────────────────────────────

    public enum OutputType {
        TASK, REMINDER
    }

    public enum ScheduleType {
        INTERVAL, DAILY, WEEKLY, WATER, CUSTOM
    }
}
