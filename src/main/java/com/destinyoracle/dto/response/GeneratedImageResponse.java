package com.destinyoracle.dto.response;

import lombok.*;

@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class GeneratedImageResponse {
    private String aspectKey;
    private String stage;
    /** Public URL of the generated image (stored in GCS) */
    private String imageUrl;
    /** The image prompt that was used */
    private String promptUsed;
    /** Status: "generated" | "fallback" (if Gemini call failed) */
    private String status;
}
