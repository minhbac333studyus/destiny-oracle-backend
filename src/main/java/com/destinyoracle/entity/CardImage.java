package com.destinyoracle.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "card_images",
       uniqueConstraints = @UniqueConstraint(columnNames = {"card_id", "stage"}))
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class CardImage {

    @Id
    @Builder.Default
    private UUID id = UUID.randomUUID();

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "card_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)   // DB-level cascade — safe card deletion
    private DestinyCard card;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CardStage stage;

    @Column(name = "image_url", nullable = false, columnDefinition = "TEXT")
    private String imageUrl;

    @Column(name = "prompt_summary", columnDefinition = "TEXT")
    @Builder.Default
    private String promptSummary = "";

    @Column(name = "generated_at", nullable = false)
    @Builder.Default
    private Instant generatedAt = Instant.now();
}
