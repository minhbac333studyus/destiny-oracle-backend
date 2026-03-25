package com.destinyoracle.domain.notification.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "reminders", indexes = {
    @Index(name = "idx_reminder_user_active",
        columnList = "userId, completed, notificationSent"),
    @Index(name = "idx_reminder_scheduled",
        columnList = "scheduledAt, notificationSent, completed")
})
@EntityListeners(AuditingEntityListener.class)
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Reminder {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID userId;

    private UUID taskId;          // Nullable — linked task
    private UUID taskStepId;      // Nullable — linked step
    private UUID conversationId;  // Nullable — where it was created

    @Column(nullable = false, length = 255)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String body;

    @Column(nullable = false)
    private LocalDateTime scheduledAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private RepeatType repeatType = RepeatType.NONE;

    @Column(length = 100)
    private String repeatCron;  // For CUSTOM repeat

    @Column(nullable = false)
    @Builder.Default
    private Boolean notificationSent = false;

    @Column(nullable = false)
    @Builder.Default
    private Boolean completed = false;

    private LocalDateTime snoozedUntil;  // Nullable — snooze support

    @CreatedDate
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;

    public enum RepeatType {
        NONE, DAILY, WEEKLY, MONTHLY, CUSTOM
    }

    public boolean isDue(LocalDateTime now) {
        if (completed || notificationSent) return false;
        if (snoozedUntil != null && now.isBefore(snoozedUntil)) return false;
        return !now.isBefore(scheduledAt);
    }
}
