package com.destinyoracle.repository;

import com.destinyoracle.entity.Goal;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface GoalRepository extends JpaRepository<Goal, UUID> {

    @Query("SELECT g FROM Goal g LEFT JOIN FETCH g.milestones WHERE g.userId = :userId ORDER BY g.createdAt DESC")
    List<Goal> findAllByUserIdWithMilestones(@Param("userId") UUID userId);

    @Query("SELECT g FROM Goal g LEFT JOIN FETCH g.milestones WHERE g.id = :id AND g.userId = :userId")
    Optional<Goal> findByIdAndUserIdWithMilestones(@Param("id") UUID id, @Param("userId") UUID userId);
}
