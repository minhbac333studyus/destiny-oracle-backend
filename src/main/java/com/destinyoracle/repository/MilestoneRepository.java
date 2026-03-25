package com.destinyoracle.repository;

import com.destinyoracle.entity.Milestone;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface MilestoneRepository extends JpaRepository<Milestone, UUID> {
    Optional<Milestone> findByIdAndGoalId(UUID id, UUID goalId);
}
