package com.destinyoracle.domain.notification.repository;

import com.destinyoracle.domain.notification.entity.NotificationRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface NotificationRuleRepository extends JpaRepository<NotificationRule, UUID> {

    List<NotificationRule> findByUserIdAndActiveTrueOrderByPriorityAsc(UUID userId);

    List<NotificationRule> findByUserIdOrderByPriorityAsc(UUID userId);
}
