package com.example.ragkb.repository;

import com.example.ragkb.model.entity.StudyTask;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface StudyTaskRepository extends JpaRepository<StudyTask, Long> {
    List<StudyTask> findByUserIdOrderByCreatedAtDesc(Long userId);
    List<StudyTask> findByUserIdAndCycle(Long userId, String cycle);
    List<StudyTask> findByUserIdAndStatus(Long userId, String status);
}
