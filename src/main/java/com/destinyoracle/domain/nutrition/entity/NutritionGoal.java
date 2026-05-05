package com.destinyoracle.domain.nutrition.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "nutrition_goals",
    uniqueConstraints = @UniqueConstraint(columnNames = "userId"))
@EntityListeners(AuditingEntityListener.class)
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NutritionGoal {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID userId;

    @Builder.Default
    private Integer calorieTarget = 2000;

    @Builder.Default
    private Integer proteinGrams = 150;

    @Builder.Default
    private Integer fatGrams = 65;

    @Builder.Default
    private Integer carbGrams = 250;

    private Double targetWeightKg;
    private Double targetBodyFatPct;
    private Double targetMusclePct;

    // User profile for TDEE calculation
    @Column(length = 10)
    private String gender;

    private Integer age;

    private Double heightCm;

    @Column(length = 20)
    private String activityLevel;

    @Column(length = 20)
    private String fitnessGoal;

    @CreatedDate
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;
}
