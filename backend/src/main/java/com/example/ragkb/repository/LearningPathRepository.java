package com.example.ragkb.repository;

import com.example.ragkb.model.entity.LearningPath;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface LearningPathRepository extends JpaRepository<LearningPath, Long> {
    List<LearningPath> findByUserIdOrderByUpdatedAtDesc(Long userId);
}
