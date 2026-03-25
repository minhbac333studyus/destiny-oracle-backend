package com.destinyoracle.domain.task.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "tasks", indexes = {
    @Index(name = "idx_task_user_status", columnList = "userId, status")
})
@EntityListeners(AuditingEntityListener.class)
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Task {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID userId;

    private UUID cardId;          // FK → destiny_cards. Nullable.
    private UUID conversationId;  // FK → ai_conversations. Nullable.
    private UUID savedPlanId;     // FK → saved_plans. Nullable.

    @Column(nullable = false, length = 255)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TaskCategory category;

    @Column(nullable = false)
    private Integer totalSteps;

    @Column(nullable = false)
    @Builder.Default
    private Integer completedSteps = 0;

    @Column(nullable = false)
    @Builder.Default
    private Integer xpPerStep = 15;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private TaskStatus status = TaskStatus.ACTIVE;

    @OneToMany(mappedBy = "task", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("stepNumber ASC")
    @Builder.Default
    private List<TaskStep> steps = new ArrayList<>();

    @CreatedDate
    @Column(updatable = false)
    private LocalDateTime createdAt;

    public enum TaskCategory {
        WORKOUT, HABIT, SHOPPING, STUDY, MEAL, CUSTOM
    }

    public enum TaskStatus {
        ACTIVE, COMPLETED, ABANDONED
    }

    public void incrementCompleted() {
        this.completedSteps++;
        if (this.completedSteps >= this.totalSteps) {
            this.status = TaskStatus.COMPLETED;
        }
    }

    public void decrementCompleted() {
        if (this.completedSteps > 0) {
            this.completedSteps--;
            if (this.status == TaskStatus.COMPLETED) {
                this.status = TaskStatus.ACTIVE;
            }
        }
    }
}
