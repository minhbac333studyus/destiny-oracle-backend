package com.destinyoracle.dto.request;

import lombok.*;
import java.util.List;
import java.util.UUID;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class BatchImageRequest {
    /** List of card IDs to generate images for. Max 10 at once. */
    private List<UUID> cardIds;
}
