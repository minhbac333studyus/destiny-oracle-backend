package com.destinyoracle.domain.nutrition.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "custom_foods", indexes = {
    @Index(name = "idx_custom_food_user", columnList = "userId")
})
@EntityListeners(AuditingEntityListener.class)
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CustomFood {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID userId;

    @Column(nullable = false, length = 300)
    private String foodName;

    @Builder.Default
    private Double servingSize = 100.0;

    @Column(length = 20)
    @Builder.Default
    private String servingUnit = "g";

    private Double calories;
    private Double proteinG;
    private Double fatG;
    private Double carbsG;
    private Double sugarG;
    private Double fiberG;


    @Column(nullable = false)
    @Builder.Default
    private Boolean favorite = false;

    @CreatedDate
    @Column(updatable = false)
    private LocalDateTime createdAt;
}
