package com.destinyoracle.domain.nutrition.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "meal_recipes", indexes = {
    @Index(name = "idx_meal_recipe_user", columnList = "userId")
})
@EntityListeners(AuditingEntityListener.class)
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MealRecipe {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID userId;

    @Column(nullable = false, length = 300)
    private String mealName;

    @Builder.Default
    private Integer servings = 1;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @JoinColumn(name = "meal_recipe_id")
    @Builder.Default
    private List<MealIngredient> ingredients = new ArrayList<>();

    // Cached totals (computed from ingredients)
    private Double totalCalories;
    private Double totalProteinG;
    private Double totalFatG;
    private Double totalCarbsG;
    private Double totalSugarG;

    @CreatedDate
    @Column(updatable = false)
    private LocalDateTime createdAt;

    public void recalcTotals() {
        this.totalCalories = ingredients.stream().mapToDouble(i -> safe(i.getCalories())).sum();
        this.totalProteinG = ingredients.stream().mapToDouble(i -> safe(i.getProteinG())).sum();
        this.totalFatG = ingredients.stream().mapToDouble(i -> safe(i.getFatG())).sum();
        this.totalCarbsG = ingredients.stream().mapToDouble(i -> safe(i.getCarbsG())).sum();
        this.totalSugarG = ingredients.stream().mapToDouble(i -> safe(i.getSugarG())).sum();
    }

    private static double safe(Double v) { return v != null ? v : 0.0; }
}
