package com.destinyoracle.domain.plan.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "nudge_states", uniqueConstraints = {
    @UniqueConstraint(name = "uq_nudge_user_schedule_date",
        columnNames = {"userId", "planScheduleId", "nudgeDate"})
})
@EntityListeners(AuditingEntityListener.class)
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NudgeState {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID userId;

    @Column(nullable = false)
    private UUID planScheduleId;

    @Column(nullable = false)
    private LocalDate nudgeDate;

    @Column(nullable = false)
    @Builder.Default
    private Integer nudgeLevel = 0;  // 0=none, 1=first, 2=escalation, 3=final

    private LocalDateTime lastNudgeAt;

    @Column(nullable = false)
    @Builder.Default
    private Boolean completed = false;

    @Column(nullable = false)
    @Builder.Default
    private Boolean skipped = false;

    @Column(length = 50)
    private String resolution;  // "completed", "skipped", "rescheduled"

    @CreatedDate
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;
}
