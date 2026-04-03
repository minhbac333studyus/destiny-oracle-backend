package com.destinyoracle.domain.notification.repository;

import com.destinyoracle.domain.notification.entity.ActivityLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ActivityLogRepository extends JpaRepository<ActivityLog, UUID> {

    /** Count activities of a given type today — e.g. water intake count */
    @Query("""
        SELECT COUNT(a) FROM ActivityLog a
        WHERE a.userId = :userId
        AND a.activityType = :type
        AND a.loggedAt >= :since
        """)
    int countByUserIdAndTypeSince(
        @Param("userId") UUID userId,
        @Param("type") String type,
        @Param("since") LocalDateTime since);

    /** Most recent activity of a type — for cooldown checks */
    Optional<ActivityLog> findFirstByUserIdAndActivityTypeOrderByLoggedAtDesc(
        UUID userId, String activityType);

    /** All activities today grouped — for dashboard display */
    @Query("""
        SELECT a.activityType, COUNT(a) FROM ActivityLog a
        WHERE a.userId = :userId
        AND a.loggedAt >= :since
        GROUP BY a.activityType
        """)
    List<Object[]> countByTypeSince(
        @Param("userId") UUID userId,
        @Param("since") LocalDateTime since);
}
