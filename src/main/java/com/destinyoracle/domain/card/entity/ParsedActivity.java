package com.destinyoracle.domain.card.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "parsed_activities")
@EntityListeners(AuditingEntityListener.class)
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class ParsedActivity {

    @Id
    @Builder.Default
    private UUID id = UUID.randomUUID();

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "raw_input", nullable = false, columnDefinition = "TEXT")
    private String rawInput;

    @Column(name = "aspect_key", nullable = false, length = 50)
    private String aspectKey;

    @Column(name = "aspect_label", nullable = false, length = 100)
    private String aspectLabel;

    @Column(name = "activity_summary", nullable = false, columnDefinition = "TEXT")
    private String activitySummary;

    @Column(name = "xp_gained", nullable = false)
    @Builder.Default
    private int xpGained = 10;

    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;
}
