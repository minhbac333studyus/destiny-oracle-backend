package com.destinyoracle.domain.task.repository;

import com.destinyoracle.domain.task.entity.Task;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface TaskRepository extends JpaRepository<Task, UUID> {

    List<Task> findByUserIdAndStatusOrderByCreatedAtDesc(UUID userId, Task.TaskStatus status);

    List<Task> findByUserIdAndStatus(UUID userId, Task.TaskStatus status);

    /**
     * Count tasks completed today — used for daily insights.
     */
    @Query(value = """
        SELECT COUNT(DISTINCT t.id) FROM tasks t
        JOIN task_steps ts ON ts.task_id = t.id
        WHERE t.user_id = :userId
        AND ts.completed = true
        AND ts.completed_at >= :since
        """, nativeQuery = true)
    int countCompletedSince(@Param("userId") UUID userId, @Param("since") LocalDateTime since);
}
