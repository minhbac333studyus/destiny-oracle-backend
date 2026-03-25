package com.destinyoracle.controller;

import com.destinyoracle.dto.request.UpdateAspectDefinitionRequest;
import com.destinyoracle.dto.response.ApiResponse;
import com.destinyoracle.entity.AspectDefinition;
import com.destinyoracle.exception.ResourceNotFoundException;
import com.destinyoracle.repository.AspectDefinitionRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/aspects")
@RequiredArgsConstructor
@Tag(name = "Aspect Definitions", description = "Global aspect catalog — list and edit built-in aspect definitions")
public class AspectDefinitionController {

    private final AspectDefinitionRepository aspectDefinitionRepository;

    @GetMapping
    @Operation(
        summary = "List all aspect definitions",
        description = "Returns all 10 built-in aspect definitions (active and inactive), " +
                      "ordered by sortOrder. Response includes `count` — total definitions in catalog."
    )
    public ResponseEntity<ApiResponse<List<AspectDefinition>>> listAll() {
        List<AspectDefinition> all = aspectDefinitionRepository
                .findAll(Sort.by("sortOrder"));
        return ResponseEntity.ok(ApiResponse.success(all));
    }

    @PatchMapping("/{key}")
    @Operation(
        summary = "Update aspect definition",
        description = "Edit the default `label`, `icon`, or `isActive` flag for a built-in aspect. " +
                      "Setting `isActive: false` hides it from the add-aspect picker. " +
                      "Only non-null fields are updated."
    )
    public ResponseEntity<ApiResponse<AspectDefinition>> update(
            @PathVariable String key,
            @RequestBody UpdateAspectDefinitionRequest request) {

        AspectDefinition def = aspectDefinitionRepository.findById(key)
                .orElseThrow(() -> new ResourceNotFoundException("AspectDefinition", "key", key));

        if (request.getLabel() != null && !request.getLabel().isBlank()) {
            def.setLabel(request.getLabel());
        }
        if (request.getIcon() != null && !request.getIcon().isBlank()) {
            def.setIcon(request.getIcon());
        }
        if (request.getIsActive() != null) {
            def.setActive(request.getIsActive());
        }

        return ResponseEntity.ok(ApiResponse.success(aspectDefinitionRepository.save(def)));
    }
}
