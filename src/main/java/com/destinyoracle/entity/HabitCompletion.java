package com.destinyoracle.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "habit_completions",
       uniqueConstraints = @UniqueConstraint(columnNames = {"habit_id", "completed_on"}))
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class HabitCompletion {

    @Id
    @Builder.Default
    private UUID id = UUID.randomUUID();

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "habit_id", nullable = false)
    private Habit habit;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "completed_on", nullable = false)
    @Builder.Default
    private LocalDate completedOn = LocalDate.now();
}
