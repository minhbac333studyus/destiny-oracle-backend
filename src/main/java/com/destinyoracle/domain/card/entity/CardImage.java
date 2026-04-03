package com.destinyoracle.domain.card.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Tracks generated images per card stage.
 * Each row = one image generation result stored in GCS.
 */
@Entity
@Table(name = "card_images")
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CardImage {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "card_id", nullable = false)
    private DestinyCard card;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CardStage stage;

    @Column(name = "image_url")
    private String imageUrl;

    @Column(name = "prompt_summary", columnDefinition = "TEXT")
    private String promptSummary;

    @Column(name = "generated_at")
    @Builder.Default
    private Instant generatedAt = Instant.now();
}
