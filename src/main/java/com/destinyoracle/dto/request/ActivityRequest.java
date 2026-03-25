package com.destinyoracle.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class ActivityRequest {

    @NotBlank(message = "rawInput is required")
    @Size(max = 1000, message = "rawInput must not exceed 1000 characters")
    private String rawInput;
}
