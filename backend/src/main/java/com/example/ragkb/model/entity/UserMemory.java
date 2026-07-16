package com.example.ragkb.model.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "user_memories")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class UserMemory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** preference(用户偏好，如"关注手机品类") / fact(跨会话事实) / summary(对话摘要) */
    @Column(name = "memory_type", nullable = false, length = 20)
    private String memoryType;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    /** 该记忆源自哪个会话（summary 类型有值，便于追溯） */
    @Column(name = "source_conversation_id")
    private Long sourceConversationId;

    /** 重要性 1~5，越高越优先注入 Prompt */
    @Column(nullable = false)
    private Integer importance = 1;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (importance == null) importance = 1;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
