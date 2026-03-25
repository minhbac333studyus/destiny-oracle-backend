package com.destinyoracle.dto.request;

import jakarta.validation.constraints.Size;
import lombok.*;

@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class UpdateCardRequest {

    /** Re-prompted fear text (user edited their original fear) */
    @Size(max = 2000)
    private String fearOriginal;

    /** Dream text filled after re-prompt */
    @Size(max = 2000)
    private String dreamOriginal;

    /**
     * Custom aspects only — users can rename their own aspects.
     * Built-in aspect labels are fixed.
     */
    @Size(max = 100)
    private String aspectLabel;

    /**
     * Custom aspects only — users can change the emoji icon.
     */
    @Size(max = 10)
    private String icon;
}
