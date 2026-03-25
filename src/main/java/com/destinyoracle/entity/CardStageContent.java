package com.destinyoracle.entity;

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

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, length = 300)
    private String tagline;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String lore;

    /** Concrete physical action the character takes at this stage (Phase 1 of 2-phase prompt). */
    @Column(name = "action_scene", columnDefinition = "TEXT")
    private String actionScene;

    /** Claude-generated Gemini image prompt for this stage. Null until generate-prompts is called. */
    @Column(name = "image_prompt", columnDefinition = "TEXT")
    private String imagePrompt;

    @Column(name = "generated_at", nullable = false)
    @Builder.Default
    private Instant generatedAt = Instant.now();
}
