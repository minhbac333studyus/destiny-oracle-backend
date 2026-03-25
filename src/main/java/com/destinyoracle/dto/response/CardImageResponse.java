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
public class CardImageResponse {

    private UUID id;
    private String imageUrl;
    private String stage;
    private Instant generatedAt;
    private String promptSummary;
}
