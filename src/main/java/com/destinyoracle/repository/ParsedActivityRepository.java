package com.destinyoracle.repository;

import com.destinyoracle.entity.ParsedActivity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ParsedActivityRepository extends JpaRepository<ParsedActivity, UUID> {

    List<ParsedActivity> findAllByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    List<ParsedActivity> findAllByUserIdAndAspectKeyOrderByCreatedAtDesc(UUID userId, String aspectKey);
}
