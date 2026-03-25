package com.destinyoracle.domain.task.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "task_steps", indexes = {
    @Index(name = "idx_step_task", columnList = "task_id, stepNumber")
})
@EntityListeners(AuditingEntityListener.class)
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TaskStep {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "task_id", nullable = false)
    private Task task;

    @Column(nullable = false)
    private Integer stepNumber;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(columnDefinition = "jsonb")
    private String payload;  // Exercises, ingredients, items — flexible JSON

    @Column(nullable = false)
    @Builder.Default
    private Boolean completed = false;

    private LocalDateTime completedAt;

    private LocalDate scheduledDate;

    public void toggle() {
        if (this.completed) {
            this.completed = false;
            this.completedAt = null;
        } else {
            this.completed = true;
            this.completedAt = LocalDateTime.now();
        }
    }
}
