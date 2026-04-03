package com.destinyoracle.domain.card.repository;

import com.destinyoracle.domain.card.entity.AspectDefinition;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface AspectDefinitionRepository extends JpaRepository<AspectDefinition, String> {
    List<AspectDefinition> findAllByIsActiveTrueOrderBySortOrderAsc();
}
