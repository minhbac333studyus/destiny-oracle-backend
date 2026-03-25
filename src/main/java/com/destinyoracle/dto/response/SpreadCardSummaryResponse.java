package com.destinyoracle.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SpreadCardSummaryResponse {

    private UUID id;
    private String aspectKey;
    private String aspectLabel;
    private String aspectIcon;
    private String cardTitle;
    private String stage;
    private String imageUrl;
    private int progressPercent;
    private int streakDays;
    private boolean hasUnreadUpdate;
}
