package com.destinyoracle.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class MilestoneStatusRequest {

    @NotBlank(message = "status is required")
    private String status;
}
