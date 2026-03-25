package com.destinyoracle.domain.plan.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.UUID;

@Entity
@Table(name = "plan_schedules", indexes = {
    @Index(name = "idx_schedule_user_day", columnList = "userId, dayOfWeek, active")
})
@EntityListeners(AuditingEntityListener.class)
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PlanSchedule {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID userId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "saved_plan_id", nullable = false)
    private SavedPlan savedPlan;

    @Enumerated(EnumType.STRING)
    private DayOfWeek dayOfWeek;  // MONDAY, TUESDAY, etc. Null = one-time

    private LocalTime timeOfDay;  // 08:00, 14:30. Null = any time

    @Column(nullable = false, length = 20)
    @Builder.Default
    private String repeatType = "WEEKLY";  // WEEKLY, DAILY, ONE_TIME, CUSTOM

    @Column(length = 100)
    private String repeatCron;  // For CUSTOM repeat

    @Column(nullable = false)
    @Builder.Default
    private Boolean notifyBefore = true;

    @Column(nullable = false)
    @Builder.Default
    private Integer notifyMinutesBefore = 15;

    @Column(nullable = false)
    @Builder.Default
    private Boolean active = true;

    @CreatedDate
    @Column(updatable = false)
    private LocalDateTime createdAt;
}
