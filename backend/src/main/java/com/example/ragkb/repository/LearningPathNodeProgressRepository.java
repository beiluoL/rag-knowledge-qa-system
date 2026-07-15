package com.example.ragkb.repository;

import com.example.ragkb.model.entity.LearningPathNodeProgress;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface LearningPathNodeProgressRepository extends JpaRepository<LearningPathNodeProgress, Long> {
    Optional<LearningPathNodeProgress> findByNodeIdAndUserId(Long nodeId, Long userId);

    List<LearningPathNodeProgress> findByNodeIdInAndUserId(List<Long> nodeIds, Long userId);
}
