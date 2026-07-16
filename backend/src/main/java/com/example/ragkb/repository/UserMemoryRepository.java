package com.example.ragkb.repository;

import com.example.ragkb.model.entity.UserMemory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface UserMemoryRepository extends JpaRepository<UserMemory, Long> {

    List<UserMemory> findByUserIdOrderByImportanceDescCreatedAtDesc(Long userId);

    List<UserMemory> findByUserIdAndMemoryTypeOrderByImportanceDescCreatedAtDesc(
            Long userId, String memoryType);

    void deleteByUserIdAndId(Long userId, Long id);

    /** 记忆数量（用于摘要压缩触发判断） */
    @Query("SELECT COUNT(m) FROM UserMemory m WHERE m.userId = :userId")
    long countByUserId(@Param("userId") Long userId);
}
