package com.example.ragkb.model.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

/**
 * 知识库（泛化，支持多领域）。
 * 树状结构：parent_id 自关联，顶级为 null；文档经 knowledge_base_id 归属。
 * 分类经 category_id 关联 kb_categories（动态分类）。
 */
@Entity
@Table(name = "knowledge_bases")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class KnowledgeBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(length = 1000)
    private String description;

    @Column(length = 500)
    private String tags;

    /** 父知识库 ID（树状结构，顶级为 null） */
    @Column(name = "parent_id")
    private Long parentId;

    /** 所属分类 ID（关联 kb_categories） */
    @Column(name = "category_id")
    private Long categoryId;

    /** 同级排序 */
    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder = 0;

    @Column(name = "owner_id")
    private Long ownerId;

    /** 系统预置（true 不可删除） */
    @Column(name = "is_system", nullable = false)
    private Boolean isSystem = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (isSystem == null) isSystem = false;
        if (sortOrder == null) sortOrder = 0;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
