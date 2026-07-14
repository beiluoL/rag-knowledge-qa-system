package com.example.ragkb.repository;

import com.example.ragkb.model.entity.OperationLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * 操作日志数据访问层
 */
@Repository
public interface OperationLogRepository extends JpaRepository<OperationLog, Long> {

    /** 分页查询所有操作日志（按时间倒序） */
    Page<OperationLog> findAllByOrderByCreatedAtDesc(Pageable pageable);

    /** 按用户 ID 查询操作日志 */
    Page<OperationLog> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    /** 按操作类型查询操作日志 */
    Page<OperationLog> findByActionOrderByCreatedAtDesc(String action, Pageable pageable);
}
