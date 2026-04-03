package com.destinyoracle.domain.card.service;

import com.destinyoracle.dto.response.GenerationJobResponse;
import com.destinyoracle.domain.card.entity.GenerationJob;

import java.util.List;
import java.util.UUID;

public interface GenerationJobService {

    /** Fetch the most recent job for a card — used by UI polling. */
    GenerationJobResponse getLatestJob(UUID userId, UUID cardId);

    /** Fetch a specific job by its own ID. */
    GenerationJobResponse getJob(UUID userId, UUID jobId);

    /** All jobs for a card, newest first. */
    List<GenerationJobResponse> listJobs(UUID userId, UUID cardId);

    /** Internal: map entity → response DTO (used by pipeline services). */
    GenerationJobResponse toResponse(GenerationJob job);
}
