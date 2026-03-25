package com.destinyoracle.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CardDetailResponse {

    private UUID id;
    private String aspectKey;
    private String aspectLabel;
    private String aspectIcon;

    // From stageContent matching currentStage
    private String cardTitle;
    private String cardTagline;
    private String loreText;

    private String imageUrl;
    private Instant lastUpdated;

    private CardStatsResponse stats;
    private List<CardHabitResponse> habits;
    private List<CardImageResponse> imageHistory;

    // Key is stage name (storm/fog/clearing/aura/radiance/legend)
    private Map<String, StageContentEntry> stageContent;

    private String fearOriginal;
    private String dreamOriginal;
}
