package com.destinyoracle.dto.response;

import lombok.*;

import java.util.Map;

@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class ImagePromptResponse {
    /** The aspect this set of prompts belongs to */
    private String aspectKey;
    private String aspectLabel;
    /** One image-generation prompt per stage key (storm → legend) */
    private Map<String, String> promptsByStage;
    /** Token usage + estimated cost for this Claude call */
    private TokenUsageResponse tokenUsage;
}
