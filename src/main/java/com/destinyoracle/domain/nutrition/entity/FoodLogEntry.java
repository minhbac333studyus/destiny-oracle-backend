package com.destinyoracle.domain.nutrition.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "food_log_entries", indexes = {
    @Index(name = "idx_food_log_user_date", columnList = "userId, logDate")
})
@EntityListeners(AuditingEntityListener.class)
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FoodLogEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID userId;

    @Column(nullable = false)
    private LocalDate logDate;

    private Integer fdcId;

    @Column(nullable = false, length = 300)
    private String foodName;

    @Builder.Default
    private Double servingQty = 1.0;

    @Column(length = 50)
    private String servingUnit;

    private Double calories;
    private Double proteinG;
    private Double fatG;
    private Double carbsG;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private MealType mealType = MealType.SNACK;

    @CreatedDate
    @Column(updatable = false)
    private LocalDateTime createdAt;
}
