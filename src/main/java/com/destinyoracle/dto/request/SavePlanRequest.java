package com.destinyoracle.dto.request;

import com.destinyoracle.domain.plan.entity.SavedPlan;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record SavePlanRequest(
    @NotBlank @Size(max = 255) String name,
    @NotNull SavedPlan.PlanType type,
    String description,
    @NotBlank String content,    // JSON content of the plan
    String slug                  // optional — auto-generated from name if null
) {}
