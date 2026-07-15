package com.example.ragkb.repository;

import com.example.ragkb.model.entity.LearningPathNode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface LearningPathNodeRepository extends JpaRepository<LearningPathNode, Long> {
    List<LearningPathNode> findByPathIdOrderByOrderIndexAsc(Long pathId);
}
