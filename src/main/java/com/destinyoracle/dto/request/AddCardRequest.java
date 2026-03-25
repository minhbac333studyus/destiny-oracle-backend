package com.destinyoracle.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class AddCardRequest {

    /**
     * User-defined label for this life aspect — completely free-form.
     * e.g. "My battle with lymphoma", "Being present for my kids", "Stop the 3am anxiety"
     * aspect_key will be auto-generated from this as a slug.
     */
    @NotBlank(message = "aspectLabel is required — describe what this aspect means to you")
    @Size(max = 100, message = "aspectLabel must be 100 chars or less")
    private String aspectLabel;

    /** Optional emoji icon — defaults to ✨ if not provided */
    @Size(max = 10)
    private String icon;

    /**
     * User's darkest fear for this aspect — the storm stage.
     * e.g. "I'm afraid I'll never recover and watch my kids grow up without me"
     */
    @NotBlank(message = "fearText is required")
    @Size(max = 2000)
    private String fearText;

    /**
     * User's greatest dream for this aspect — the legend stage.
     * e.g. "I want to run a 5k after remission and feel stronger than before"
     */
    @NotBlank(message = "dreamText is required")
    @Size(max = 2000)
    private String dreamText;
}
