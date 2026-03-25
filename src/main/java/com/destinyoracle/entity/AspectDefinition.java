package com.destinyoracle.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "aspect_definitions",
       uniqueConstraints = @UniqueConstraint(columnNames = "aspect_key"))
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class AspectDefinition {

    @Id
    @Builder.Default
    private String aspectKey = "";   // PK — e.g. "health", "career"

    @Column(nullable = false, length = 100)
    private String label;            // default display label

    @Column(nullable = false, length = 10)
    private String icon;             // emoji icon

    @Column(name = "sort_order", nullable = false)
    @Builder.Default
    private int sortOrder = 99;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private boolean isActive = true; // false = hidden from add-aspect list
}
