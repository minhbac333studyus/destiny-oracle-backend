package com.destinyoracle.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CardHabitResponse {

    private UUID id;
    private String text;
    private String frequency;
    private boolean completedToday;
    private int streakDays;
}
