package com.example.ragkb.model.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

/**
 * 学习任务：驱动用户成长。可按周期（daily/weekly/monthly）生成，
 * 关联某个知识库，指定学习模式（list/flashcard/swipe/challenge）。
 */
@Entity
@Table(name = "study_tasks")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class StudyTask {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "knowledge_base_id")
    private Long knowledgeBaseId;

    @Column(nullable = false, length = 300)
    private String title;

    /** list / flashcard / swipe / challenge */
    @Column(nullable = false, length = 30)
    private String mode;

    /** daily / weekly / monthly */
    @Column(length = 20)
    private String cycle;

    /** 学习卡片内容（JSON 数组），可为空（运行时从知识库 chunks 生成） */
    @Column(columnDefinition = "TEXT")
    private String content;

    @Column(name = "target_count", nullable = false)
    private Integer targetCount = 0;

    @Column(name = "progress_count", nullable = false)
    private Integer progressCount = 0;

    @Column(nullable = false, length = 20)
    private String status = "ACTIVE";

    @Column(name = "due_at")
    private LocalDateTime dueAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (targetCount == null) targetCount = 0;
        if (progressCount == null) progressCount = 0;
        if (status == null) status = "ACTIVE";
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
