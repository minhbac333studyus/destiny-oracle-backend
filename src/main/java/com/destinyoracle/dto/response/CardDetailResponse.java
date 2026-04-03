package com.destinyoracle.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
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

    private String imageUrl;
    private Instant lastUpdated;

    private CardStatsResponse stats;

    private String fearOriginal;
    private String dreamOriginal;
}
