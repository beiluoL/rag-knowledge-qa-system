package com.example.ragkb.model.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

/**
 * 学习路径节点进度（按用户）：记录某用户对某节点的完成状态与评分。
 * (node_id, user_id) 唯一，保证每用户每节点只有一条进度记录。
 */
@Entity
@Table(name = "learning_path_node_progress")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class LearningPathNodeProgress {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "node_id", nullable = false)
    private Long nodeId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** NOT_STARTED / IN_PROGRESS / COMPLETED */
    @Column(nullable = false, length = 20)
    private String status = "NOT_STARTED";

    private Integer score;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (status == null) status = "NOT_STARTED";
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
