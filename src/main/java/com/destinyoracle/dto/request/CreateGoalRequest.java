package com.destinyoracle.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
public class CreateGoalRequest {

    @NotBlank(message = "aspectKey is required")
    private String aspectKey;

    @NotBlank(message = "aspectLabel is required")
    private String aspectLabel;

    @NotBlank(message = "title is required")
    @Size(max = 300, message = "Title must not exceed 300 characters")
    private String title;

    private List<String> milestones;
}
