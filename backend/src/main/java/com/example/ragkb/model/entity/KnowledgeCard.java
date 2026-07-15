package com.example.ragkb.model.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

/**
 * 知识卡片：用户可手动新增/编辑，或由 AI 一键生成。
 * 形态为「两者结合」——同时包含 标题 / 正面(问题或术语) / 背面(答案或解析) / 分类 / 标签，
 * 既可当闪卡复习，也可当概念笔记收藏。
 */
@Entity
@Table(name = "knowledge_cards")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class KnowledgeCard {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** 卡片标题（概览与列表显示用） */
    @Column(nullable = false, length = 300)
    private String title;

    /** 正面：问题 / 术语（闪卡模式用，可为空以兼容纯概念笔记） */
    @Column(columnDefinition = "TEXT")
    private String front;

    /** 背面：答案 / 解析 / 正文（必填） */
    @Column(nullable = false, columnDefinition = "TEXT")
    private String back;

    /** 分类（可选） */
    @Column(length = 100)
    private String category;

    /** 标签：逗号分隔的字符串（可选） */
    @Column(columnDefinition = "TEXT")
    private String tags;

    /** 来源：MANUAL(手动) / AI(一键生成) */
    @Column(nullable = false, length = 20)
    private String source = "MANUAL";

    /** 关联知识库（可选，预留从知识库抽取能力） */
    @Column(name = "knowledge_base_id")
    private Long knowledgeBaseId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (source == null) source = "MANUAL";
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
