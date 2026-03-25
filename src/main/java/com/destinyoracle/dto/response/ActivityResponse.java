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
public class ActivityResponse {

    private UUID id;
    private String rawInput;
    private String aspectKey;
    private String aspectLabel;
    private String activitySummary;
    private int xpGained;
    private Instant createdAt;
}
