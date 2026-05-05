package com.destinyoracle.domain.dailyplan.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.UUID;

@Entity
@Table(name = "schedule_templates",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_template_user_daytype",
        columnNames = {"userId", "dayType"}
    ))
@EntityListeners(AuditingEntityListener.class)
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ScheduleTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private DayType dayType = DayType.WEEKDAY;

    @Column(length = 100)
    private String terminalGoal;       // e.g. "Sleep"

    private LocalTime terminalGoalTime; // e.g. 21:00

    @Column(columnDefinition = "TEXT")
    private String fixedBlocks;         // JSON: [{"name":"Work","start":"09:00","end":"17:00"}]

    @Column(columnDefinition = "TEXT")
    private String mealTimes;           // JSON: [{"name":"Lunch","time":"12:00"}]

    @Column(columnDefinition = "TEXT")
    private String recurringReminders;  // JSON: [{"name":"Water","intervalHours":2}]

    @CreatedDate
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;

    public enum DayType {
        WEEKDAY, WEEKEND, TRAVEL
    }
}
