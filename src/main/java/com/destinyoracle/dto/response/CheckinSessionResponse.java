package com.destinyoracle.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CheckinSessionResponse {

    private UUID id;
    private LocalDate sessionDate;
    private Instant completedAt;
    private int totalHabits;
    private int completedCount;
    private List<CheckinItemResponse> items;
}
