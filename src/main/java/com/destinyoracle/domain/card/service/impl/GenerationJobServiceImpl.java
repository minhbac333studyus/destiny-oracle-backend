package com.destinyoracle.domain.card.service.impl;

import com.destinyoracle.dto.response.GenerationJobResponse;
import com.destinyoracle.dto.response.GenerationJobStepResponse;
import com.destinyoracle.domain.card.entity.GenerationJob;
import com.destinyoracle.exception.ResourceNotFoundException;
import com.destinyoracle.domain.card.repository.GenerationJobRepository;
import com.destinyoracle.domain.card.service.GenerationJobService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class GenerationJobServiceImpl implements GenerationJobService {

    private final GenerationJobRepository jobRepository;

    @Override
    @Transactional(readOnly = true)
    public GenerationJobResponse getLatestJob(UUID userId, UUID cardId) {
        GenerationJob job = jobRepository.findLatestByCardId(cardId)
                .orElseThrow(() -> new ResourceNotFoundException("GenerationJob", "cardId", cardId));
        return toResponse(job);
    }

    @Override
    @Transactional(readOnly = true)
    public GenerationJobResponse getJob(UUID userId, UUID jobId) {
        GenerationJob job = jobRepository.findById(jobId)
                .orElseThrow(() -> new ResourceNotFoundException("GenerationJob", "id", jobId));
        return toResponse(job);
    }

    @Override
    @Transactional(readOnly = true)
    public List<GenerationJobResponse> listJobs(UUID userId, UUID cardId) {
        return jobRepository.findAllByCardIdOrderByCreatedAtDesc(cardId)
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    public GenerationJobResponse toResponse(GenerationJob job) {
        int percent = job.getTotalSteps() == 0
                ? 0
                : (job.getCompletedSteps() * 100) / job.getTotalSteps();

        List<GenerationJobStepResponse> stepResponses = job.getSteps().stream()
                .map(s -> GenerationJobStepResponse.builder()
                        .id(s.getId())
                        .stepOrder(s.getStepOrder())
                        .stepName(s.getStepName())
                        .phase(s.getPhase().name())
                        .stage(s.getStage())
                        .status(s.getStatus().name())
                        .message(s.getMessage())
                        .resultUrl(s.getResultUrl())
                        .startedAt(s.getStartedAt())
                        .completedAt(s.getCompletedAt())
                        .build())
                .collect(Collectors.toList());

        return GenerationJobResponse.builder()
                .id(job.getId())
                .cardId(job.getCardId())
                .userId(job.getUserId())
                .jobLabel(job.getJobLabel())
                .status(job.getStatus().name())
                .totalSteps(job.getTotalSteps())
                .completedSteps(job.getCompletedSteps())
                .progressPercent(percent)
                .createdAt(job.getCreatedAt())
                .startedAt(job.getStartedAt())
                .completedAt(job.getCompletedAt())
                .errorMessage(job.getErrorMessage())
                .steps(stepResponses)
                .build();
    }
}
