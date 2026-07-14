package com.example.ragkb.service;

import com.example.ragkb.model.entity.OperationLog;
import com.example.ragkb.repository.OperationLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * 操作日志服务
 * 异步记录用户关键操作，不影响主业务流程性能
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OperationLogService {

    private final OperationLogRepository operationLogRepository;

    /**
     * 异步记录操作日志（不阻塞主流程）
     *
     * @param userId     操作用户 ID
     * @param username   操作用户名
     * @param action     操作类型
     * @param targetType 目标类型
     * @param targetId   目标 ID
     * @param detail     详情
     * @param ipAddress  客户端 IP
     */
    @Async("documentTaskExecutor")
    public void log(Long userId, String username, String action,
                    String targetType, Long targetId, String detail, String ipAddress) {
        try {
            OperationLog opLog = OperationLog.builder()
                    .userId(userId)
                    .username(username)
                    .action(action)
                    .targetType(targetType)
                    .targetId(targetId)
                    .detail(detail)
                    .ipAddress(ipAddress)
                    .build();
            operationLogRepository.save(opLog);
        } catch (Exception e) {
            log.warn("记录操作日志失败: action={}, userId={}", action, userId, e);
        }
    }

    /**
     * 分页查询操作日志
     *
     * @param pageable 分页参数
     * @return 操作日志分页
     */
    public Page<OperationLog> getLogs(Pageable pageable) {
        return operationLogRepository.findAllByOrderByCreatedAtDesc(pageable);
    }

    /**
     * 按用户查询操作日志
     *
     * @param userId   用户 ID
     * @param pageable 分页参数
     * @return 操作日志分页
     */
    public Page<OperationLog> getLogsByUser(Long userId, Pageable pageable) {
        return operationLogRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable);
    }
}
