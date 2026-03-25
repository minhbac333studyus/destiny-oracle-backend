package com.destinyoracle.repository;

import com.destinyoracle.entity.Habit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface HabitRepository extends JpaRepository<Habit, UUID> {
    List<Habit> findAllByCardId(UUID cardId);
    Optional<Habit> findByIdAndCardId(UUID id, UUID cardId);
}
