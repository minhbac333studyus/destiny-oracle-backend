package com.destinyoracle.dto.request;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class UpdateUserRequest {

    @Size(min = 1, max = 100, message = "Display name must be between 1 and 100 characters")
    private String displayName;

    @Size(max = 64, message = "Timezone must not exceed 64 characters")
    private String timezone;

    private Boolean notificationsEnabled;

    @Pattern(regexp = "^([01]\\d|2[0-3]):[0-5]\\d$",
             message = "Daily reminder time must be in HH:mm format")
    private String dailyReminderTime;

    /** URL of the user's avatar image */
    private String avatarUrl;

    /** URL of the user's chibi image — used as style reference for Gemini Imagen */
    private String chibiUrl;

    /** Marks onboarding as complete */
    private Boolean onboardingComplete;
}
