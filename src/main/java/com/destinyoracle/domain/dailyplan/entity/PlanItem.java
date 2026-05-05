package com.destinyoracle.domain.dailyplan.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "plan_items", indexes = {
    @Index(name = "idx_plan_item_plan", columnList = "daily_plan_id, sortOrder"),
    @Index(name = "idx_plan_item_parent", columnList = "parent_item_id")
})
@EntityListeners(AuditingEntityListener.class)
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PlanItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "daily_plan_id", nullable = false)
    private DailyPlan dailyPlan;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_item_id")
    private PlanItem parentItem;         // null = top-level item

    @OneToMany(mappedBy = "parentItem", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder ASC")
    @Builder.Default
    private List<PlanItem> children = new ArrayList<>();

    @Column(nullable = false, length = 255)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private ItemCategory category = ItemCategory.OTHER;

    private LocalTime scheduledTime;           // null for child items (no dedicated time)

    private Integer estimatedDurationMinutes;  // nullable

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private ItemStatus status = ItemStatus.PENDING;

    @Column(nullable = false)
    @Builder.Default
    private Boolean isPrep = false;

    private LocalDate prepForDate;             // if cooking for tomorrow

    private LocalTime reminderTime;            // when to fire reminder; null = no reminder
    @Column(nullable = false)
    @Builder.Default
    private Boolean reminderDismissed = false;

    @Column(nullable = false)
    @Builder.Default
    private Integer sortOrder = 0;

    @Column(nullable = false)
    @Builder.Default
    private Boolean aiGenerated = true;

    @Column(nullable = false)
    @Builder.Default
    private Boolean userModified = false;

    @CreatedDate
    @Column(updatable = false)
    private LocalDateTime createdAt;

    public enum ItemCategory {
        MEAL_PREP, MEAL, EXERCISE, WORK, HYDRATION, CHORE, SELF_CARE, SHOPPING, OTHER
    }

    public enum ItemStatus {
        PENDING, DONE, SKIPPED, RESCHEDULED
    }

    /** Check if this item's reminder is due. */
    public boolean isReminderDue(LocalTime now) {
        if (reminderTime == null || reminderDismissed || status != ItemStatus.PENDING) return false;
        return !now.isBefore(reminderTime);
    }
}
