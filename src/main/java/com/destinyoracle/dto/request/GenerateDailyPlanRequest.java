package com.destinyoracle.dto.request;

import java.time.LocalDate;

public record GenerateDailyPlanRequest(
    LocalDate date   // optional — defaults to today
) {}
