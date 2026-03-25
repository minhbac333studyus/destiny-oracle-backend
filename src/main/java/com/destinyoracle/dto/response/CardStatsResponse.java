package com.destinyoracle.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CardStatsResponse {

    private String currentStage;
    private int stageProgressPercent;
    private int totalCheckIns;
    private int longestStreak;
    private int currentStreak;
    private int daysAtCurrentStage;
}
