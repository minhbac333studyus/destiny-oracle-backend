package com.destinyoracle.dto.response;

import lombok.*;

@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class AvailableAspectResponse {
    /** Aspect key, e.g. "career" */
    private String key;
    /** Default label, e.g. "Career & Purpose" */
    private String label;
    /** Default emoji icon */
    private String icon;
    /** Whether the user has already added this aspect */
    private boolean alreadyAdded;
}
