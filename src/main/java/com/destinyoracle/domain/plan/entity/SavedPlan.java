package com.destinyoracle.domain.plan.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "saved_plans", uniqueConstraints = {
    @UniqueConstraint(name = "uq_plan_user_slug", columnNames = {"userId", "slug", "active"})
}, indexes = {
    @Index(name = "idx_plan_user_active", columnList = "userId, active")
})
@EntityListeners(AuditingEntityListener.class)
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SavedPlan {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID userId;

    @Column(nullable = false, length = 255)
    private String name;  // "Leg day", "Morning routine", "Grocery run"

    @Column(nullable = false, length = 100)
    private String slug;  // "leg-day" — unique per user (when active)

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PlanType type;

    @Column(columnDefinition = "TEXT")
    private String description;  // AI-generated summary

    @Column(nullable = false, columnDefinition = "jsonb")
    private String content;  // The actual plan — structured JSON

    @Column(nullable = false)
    @Builder.Default
    private Integer version = 1;

    @Column(nullable = false)
    @Builder.Default
    private Boolean active = true;

    private UUID parentPlanId;  // Points to previous version if forked

    @OneToMany(mappedBy = "savedPlan", cascade = CascadeType.ALL)
    @Builder.Default
    private List<PlanSchedule> schedules = new ArrayList<>();

    @CreatedDate
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;

    public enum PlanType {
        WORKOUT, MEAL, ROUTINE, SHOPPING, CUSTOM
    }

    /**
     * Generate slug from name.
     * "Leg Day v2" → "leg-day-v2"
     */
    public static String slugify(String name) {
        return name.toLowerCase()
            .replaceAll("[^a-z0-9\\s-]", "")
            .replaceAll("\\s+", "-")
            .replaceAll("-+", "-")
            .replaceAll("^-|-$", "");
    }
}
