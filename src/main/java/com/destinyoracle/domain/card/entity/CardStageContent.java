package com.destinyoracle.domain.card.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "card_stage_content",
       uniqueConstraints = @UniqueConstraint(columnNames = {"card_id", "stage"}))
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class CardStageContent {

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

    /** Claude-generated image prompt for this stage. Null until generate-prompts is called. */
    @Column(name = "image_prompt", columnDefinition = "TEXT")
    private String imagePrompt;

    @Column(name = "generated_at", nullable = false)
    @Builder.Default
    private Instant generatedAt = Instant.now();
}
