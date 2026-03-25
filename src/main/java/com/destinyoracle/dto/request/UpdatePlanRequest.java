package com.destinyoracle.dto.request;

import jakarta.validation.constraints.Size;

public record UpdatePlanRequest(
    @Size(max = 255) String name,
    String description,
    String content,                 // JSON content
    boolean overwrite               // true = overwrite, false = new version
) {}
