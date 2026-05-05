package com.destinyoracle.domain.nutrition.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "favorite_foods",
    uniqueConstraints = @UniqueConstraint(columnNames = {"userId", "fdcId"}))
@EntityListeners(AuditingEntityListener.class)
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FavoriteFood {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID userId;

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

    @CreatedDate
    @Column(updatable = false)
    private LocalDateTime createdAt;
}
