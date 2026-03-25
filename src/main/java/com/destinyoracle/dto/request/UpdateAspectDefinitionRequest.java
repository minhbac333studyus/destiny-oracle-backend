package com.destinyoracle.dto.request;

import lombok.*;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class UpdateAspectDefinitionRequest {
    private String label;
    private String icon;
    private Boolean isActive;
}
