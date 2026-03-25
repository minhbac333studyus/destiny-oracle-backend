package com.destinyoracle.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "destiny_cards",
       uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "aspect_key"}))
@EntityListeners(AuditingEntityListener.class)
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class DestinyCard {

    @Id
    @Builder.Default
    private UUID id = UUID.randomUUID();

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    @Column(name = "aspect_key", nullable = false, length = 50)
    private String aspectKey;

    @Column(name = "aspect_label", nullable = false, length = 100)
    private String aspectLabel;

    @Column(name = "aspect_icon", nullable = false, length = 10)
    @Builder.Default
    private String aspectIcon = "✨";

    @Column(name = "is_custom_aspect", nullable = false)
    @Builder.Default
    private boolean isCustomAspect = false;

    @Column(name = "sort_order", nullable = false)
    @Builder.Default
    private int sortOrder = 0;

    @Column(name = "fear_original", nullable = false, columnDefinition = "TEXT")
    @Builder.Default
    private String fearOriginal = "";

    @Column(name = "dream_original", nullable = false, columnDefinition = "TEXT")
    @Builder.Default
    private String dreamOriginal = "";

    @Enumerated(EnumType.STRING)
    @Column(name = "current_stage", nullable = false, length = 20)
    @Builder.Default
    private CardStage currentStage = CardStage.storm;

    @Column(name = "stage_progress_percent", nullable = false)
    @Builder.Default
    private int stageProgressPercent = 0;

    @Column(name = "total_check_ins", nullable = false)
    @Builder.Default
    private int totalCheckIns = 0;

    @Column(name = "longest_streak", nullable = false)
    @Builder.Default
    private int longestStreak = 0;

    @Column(name = "current_streak", nullable = false)
    @Builder.Default
    private int currentStreak = 0;

    @Column(name = "days_at_current_stage", nullable = false)
    @Builder.Default
    private int daysAtCurrentStage = 0;

    @Column(name = "image_url", nullable = false, columnDefinition = "TEXT")
    @Builder.Default
    private String imageUrl = "";

    /**
     * Tracks whether Claude has generated and persisted prompts for all 6 stages.
     * NONE       → never generated; generate-images will call Claude first
     * GENERATING → Claude is currently running (prevents duplicate calls)
     * READY      → all 6 prompts saved in card_stage_content.image_prompt; Claude skipped on next call
     * FAILED     → last Claude call failed; retry allowed
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "prompt_status", nullable = false, length = 20)
    @Builder.Default
    private PromptStatus promptStatus = PromptStatus.NONE;

    @Column(name = "last_updated", nullable = false)
    @Builder.Default
    private Instant lastUpdated = Instant.now();

    @LastModifiedDate
    @Column(name = "updated_at")
    private Instant updatedAt;

    // ── XP / Stage Progression (AI Assistant extension) ───
    @Column(name = "current_xp", nullable = false)
    @Builder.Default
    private Integer currentXp = 0;

    @Column(name = "xp_to_next_stage", nullable = false)
    @Builder.Default
    private Integer xpToNextStage = 100;

    @Column(name = "stage_advanced_at")
    private java.time.LocalDateTime stageAdvancedAt;

    // ── Relationships ─────────────────────────────────────
    //
    // stageContents — PERSIST+MERGE only, NO orphanRemoval.
    //   Stage history is a permanent record. Removing a stage from the list
    //   must never silently delete content rows — use an explicit repository call.
    @OneToMany(mappedBy = "card",
               cascade = {CascadeType.PERSIST, CascadeType.MERGE},
               fetch = FetchType.LAZY)
    @OrderBy("stage ASC")
    @Builder.Default
    private List<CardStageContent> stageContents = new ArrayList<>();

    // imageHistory — PERSIST+MERGE only, NO orphanRemoval.
    //   Generated images are a permanent audit trail (GCS files may still exist
    //   after a list change). Use explicit delete when truly needed.
    @OneToMany(mappedBy = "card",
               cascade = {CascadeType.PERSIST, CascadeType.MERGE},
               fetch = FetchType.LAZY)
    @OrderBy("generatedAt ASC")
    @Builder.Default
    private List<CardImage> imageHistory = new ArrayList<>();

    // habits — ALL + orphanRemoval: habits are fully owned by this card.
    //   Adding/removing habits in the list is the authoritative lifecycle action.
    @OneToMany(mappedBy = "card",
               cascade = CascadeType.ALL,
               orphanRemoval = true,
               fetch = FetchType.LAZY)
    @Builder.Default
    private List<Habit> habits = new ArrayList<>();
}
