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
public class CheckinItemResponse {

    private UUID habitId;
    private String habitText;
    private String aspectLabel;
    private String aspectKey;
    private boolean isChecked;
}
