package com.destinyoracle.domain.dailyplan.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.UUID;

@Entity
@Table(name = "plan_history", indexes = {
    @Index(name = "idx_plan_history_item", columnList = "planItemId")
})
@EntityListeners(AuditingEntityListener.class)
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PlanHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID planItemId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private HistoryAction action;

    private LocalTime originalTime;

    private LocalTime newTime;    // for RESCHEDULED

    @CreatedDate
    @Column(updatable = false)
    private LocalDateTime timestamp;

    public enum HistoryAction {
        COMPLETED, SKIPPED, RESCHEDULED, ADDED
    }
}
