package com.destinyoracle.domain.dailyplan.repository;

import com.destinyoracle.domain.dailyplan.entity.ScheduleTemplate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ScheduleTemplateRepository extends JpaRepository<ScheduleTemplate, UUID> {

    List<ScheduleTemplate> findByUserId(UUID userId);

    Optional<ScheduleTemplate> findByUserIdAndDayType(UUID userId, ScheduleTemplate.DayType dayType);
}
