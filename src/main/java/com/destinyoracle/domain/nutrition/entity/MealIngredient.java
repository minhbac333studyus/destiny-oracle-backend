package com.destinyoracle.domain.nutrition.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "meal_ingredients")
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MealIngredient {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 300)
    private String foodName;

    @Builder.Default
    private Double qty = 1.0;

    @Column(length = 20)
    @Builder.Default
    private String unit = "g";

    private Double calories;
    private Double proteinG;
    private Double fatG;
    private Double carbsG;
    private Double sugarG;
}
