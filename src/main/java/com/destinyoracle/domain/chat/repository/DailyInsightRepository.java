package com.destinyoracle.domain.chat.repository;

import com.destinyoracle.domain.chat.entity.DailyInsight;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DailyInsightRepository extends JpaRepository<DailyInsight, UUID> {

    Optional<DailyInsight> findByUserIdAndInsightDate(UUID userId, LocalDate date);

    List<DailyInsight> findByUserIdOrderByInsightDateDesc(UUID userId);
}
