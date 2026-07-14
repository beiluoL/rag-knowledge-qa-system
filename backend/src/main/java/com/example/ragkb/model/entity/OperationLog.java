package com.example.ragkb.model.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

/**
 * 操作日志实体
 * 记录用户的关键操作（上传文档、删除文档、修改角色等），用于审计追踪
 */
@Entity
@Table(name = "operation_logs")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class OperationLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 操作用户 ID */
    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** 操作用户名 */
    @Column(length = 50)
    private String username;

    /** 操作类型：UPLOAD_DOC, DELETE_DOC, TOGGLE_USER, CHANGE_ROLE, LOGIN, LOGOUT 等 */
    @Column(nullable = false, length = 50)
    private String action;

    /** 操作目标类型：DOCUMENT, USER, CONVERSATION 等 */
    @Column(name = "target_type", length = 50)
    private String targetType;

    /** 操作目标 ID */
    @Column(name = "target_id")
    private Long targetId;

    /** 操作详情描述 */
    @Column(length = 500)
    private String detail;

    /** 客户端 IP 地址 */
    @Column(name = "ip_address", length = 50)
    private String ipAddress;

    /** 操作时间 */
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
