package com.destinyoracle.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TokenUsageResponse {
    private long   inputTokens;
    private long   outputTokens;
    private long   totalTokens;
    private double inputCostUsd;
    private double outputCostUsd;
    private double totalCostUsd;
    private String model;
}
