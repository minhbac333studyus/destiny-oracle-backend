package com.destinyoracle.domain.dailyplan.repository;

import com.destinyoracle.domain.dailyplan.entity.PlanHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PlanHistoryRepository extends JpaRepository<PlanHistory, UUID> {

    List<PlanHistory> findByPlanItemIdOrderByTimestampDesc(UUID planItemId);
}
