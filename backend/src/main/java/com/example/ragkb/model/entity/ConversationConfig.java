package com.example.ragkb.model.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

/**
 * 对话配置（全局单行）：集中管理 AI 模式 / AI 框架 / RAG 过程可视化开关 / 真 SSE 流式开关
 * 以及混合检索开关与 RRF 融合常数。
 * 约定 id=1 为唯一配置行，应用启动时从 DB 回填到内存提供者。
 */
@Entity
@Table(name = "conversation_config")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ConversationConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ai_mode", nullable = false, length = 32)
    private String aiMode;

    @Column(name = "ai_framework", nullable = false, length = 32)
    private String aiFramework;

    @Column(name = "rag_visualization_enabled", nullable = false)
    private Boolean ragVisualizationEnabled;

    @Column(name = "true_sse_streaming_enabled", nullable = false)
    private Boolean trueSseStreamingEnabled;

    @Column(name = "hybrid_enabled", nullable = false)
    private Boolean hybridEnabled;

    @Column(name = "rrf_k", nullable = false)
    private Integer rrfK;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    @PreUpdate
    protected void onTouch() {
        updatedAt = LocalDateTime.now();
    }
}
