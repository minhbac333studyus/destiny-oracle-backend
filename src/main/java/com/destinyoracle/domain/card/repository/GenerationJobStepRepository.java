package com.destinyoracle.domain.card.repository;

import com.destinyoracle.domain.card.entity.GenerationJobStep;
import com.destinyoracle.domain.card.entity.JobPhase;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface GenerationJobStepRepository extends JpaRepository<GenerationJobStep, UUID> {

    List<GenerationJobStep> findAllByJobIdOrderByStepOrderAsc(UUID jobId);

    @Query("SELECT s FROM GenerationJobStep s WHERE s.job.id = :jobId AND s.phase = :phase ORDER BY s.stepOrder ASC")
    List<GenerationJobStep> findByJobIdAndPhase(@Param("jobId") UUID jobId, @Param("phase") JobPhase phase);
}
