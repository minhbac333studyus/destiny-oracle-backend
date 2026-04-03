package com.destinyoracle.domain.card.service.impl;

import com.destinyoracle.domain.card.entity.*;
import com.destinyoracle.domain.user.entity.*;
import com.destinyoracle.domain.card.repository.GenerationJobRepository;
import com.destinyoracle.domain.card.repository.GenerationJobStepRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Helper bean that saves job/step state in a NEW independent transaction.
 *
 * Using REQUIRES_NEW is essential: step transitions must be committed
 * immediately to disk so the UI polling endpoint sees them in real time,
 * even when the outer pipeline transaction is still running or has rolled back.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class JobStepUpdater {

    private final GenerationJobRepository     jobRepository;
    private final GenerationJobStepRepository stepRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markJobStatus(UUID jobId, JobStatus status) {
        jobRepository.findById(jobId).ifPresent(job -> {
            job.setStatus(status);
            if (status == JobStatus.PROMPTING || status == JobStatus.IMAGING) {
                if (job.getStartedAt() == null) job.setStartedAt(Instant.now());
            }
            if (status == JobStatus.COMPLETED || status == JobStatus.FAILED) {
                job.setCompletedAt(Instant.now());
            }
            jobRepository.save(job);
            log.info("  Job {} → {}", jobId, status);
        });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markJobFailed(UUID jobId, String errorMessage) {
        jobRepository.findById(jobId).ifPresent(job -> {
            job.setStatus(JobStatus.FAILED);
            job.setCompletedAt(Instant.now());
            job.setErrorMessage(errorMessage);
            jobRepository.save(job);
            log.error("  Job {} → FAILED: {}", jobId, errorMessage);
        });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markStepRunning(UUID stepId, String message) {
        stepRepository.findById(stepId).ifPresent(step -> {
            step.setStatus(StepStatus.RUNNING);
            step.setStartedAt(Instant.now());
            step.setMessage(message);
            stepRepository.save(step);
            log.info("  Step[{}] {} → RUNNING", step.getStepOrder(), step.getStepName());
        });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markStepDone(UUID stepId, String message, String resultUrl) {
        stepRepository.findById(stepId).ifPresent(step -> {
            step.setStatus(StepStatus.DONE);
            step.setCompletedAt(Instant.now());
            step.setMessage(message);
            if (resultUrl != null) step.setResultUrl(resultUrl);
            stepRepository.save(step);

            // Increment completedSteps on the parent job
            jobRepository.findById(step.getJob().getId()).ifPresent(job -> {
                job.setCompletedSteps(job.getCompletedSteps() + 1);
                jobRepository.save(job);
            });
            log.info("  Step[{}] {} → DONE", step.getStepOrder(), step.getStepName());
        });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markStepSkipped(UUID stepId, String message) {
        stepRepository.findById(stepId).ifPresent(step -> {
            step.setStatus(StepStatus.SKIPPED);
            step.setStartedAt(Instant.now());
            step.setCompletedAt(Instant.now());
            step.setMessage(message);
            stepRepository.save(step);

            jobRepository.findById(step.getJob().getId()).ifPresent(job -> {
                job.setCompletedSteps(job.getCompletedSteps() + 1);
                jobRepository.save(job);
            });
            log.info("  Step[{}] {} → SKIPPED", step.getStepOrder(), step.getStepName());
        });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markStepFailed(UUID stepId, String errorMessage) {
        stepRepository.findById(stepId).ifPresent(step -> {
            step.setStatus(StepStatus.FAILED);
            step.setCompletedAt(Instant.now());
            step.setMessage("Failed: " + errorMessage);
            stepRepository.save(step);
            log.error("  Step[{}] {} → FAILED: {}", step.getStepOrder(), step.getStepName(), errorMessage);
        });
    }
}
