package com.destinyoracle.dto.response;

import lombok.*;
import java.util.List;
import java.util.UUID;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class BatchImageResult {
    private UUID   cardId;
    private String aspectKey;
    private String status;     // "completed" | "failed"
    private String error;      // null if successful
    private List<GeneratedImageResponse> images; // 6 results per card
}
