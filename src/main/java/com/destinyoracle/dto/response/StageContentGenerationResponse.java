package com.destinyoracle.dto.response;

import lombok.Builder;
import lombok.Data;

import java.util.Map;
import java.util.UUID;

@Data
@Builder
public class StageContentGenerationResponse {
    private UUID   cardId;
    private String aspectKey;
    private String aspectLabel;

    /**
     * stage name → {title, tagline, lore}
     * e.g. "storm" → { title: "The Invisible Worker", tagline: "...", lore: "..." }
     */
    private Map<String, StageContentEntry> stageContent;
    /** Token usage + estimated cost for this Claude call */
    private TokenUsageResponse tokenUsage;
}
