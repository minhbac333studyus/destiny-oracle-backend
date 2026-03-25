package com.destinyoracle.repository;

import com.destinyoracle.entity.HabitCompletion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Repository
public interface HabitCompletionRepository extends JpaRepository<HabitCompletion, UUID> {

    boolean existsByHabitIdAndCompletedOn(UUID habitId, LocalDate completedOn);

    void deleteByHabitIdAndCompletedOn(UUID habitId, LocalDate completedOn);

    /** Returns all habit IDs completed today for a given list of habit IDs */
    @Query("""
        SELECT hc.habit.id FROM HabitCompletion hc
        WHERE hc.habit.id IN :habitIds AND hc.completedOn = :date
        """)
    Set<UUID> findCompletedHabitIds(@Param("habitIds") List<UUID> habitIds,
                                    @Param("date") LocalDate date);
}
