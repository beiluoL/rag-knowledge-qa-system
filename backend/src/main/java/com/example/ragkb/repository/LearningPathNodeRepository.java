package com.example.ragkb.repository;

import com.example.ragkb.model.entity.LearningPathNode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface LearningPathNodeRepository extends JpaRepository<LearningPathNode, Long> {
    List<LearningPathNode> findByPathIdOrderByOrderIndexAsc(Long pathId);

    @Modifying
    @Query("UPDATE LearningPathNode n SET n.orderIndex = :idx WHERE n.id = :id")
    void updateOrderIndex(@Param("id") Long id, @Param("idx") int orderIndex);
}
