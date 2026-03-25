package com.destinyoracle.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class HabitCompleteRequest {

    @NotNull(message = "completed field is required")
    private Boolean completed;
}
