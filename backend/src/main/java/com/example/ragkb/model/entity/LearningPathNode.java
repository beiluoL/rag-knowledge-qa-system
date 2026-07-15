package com.example.ragkb.model.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

/**
 * 学习路径节点：路径中的一章/一节（有序）。
 * node_type 目前支持 DOCUMENT（基于某篇文档学习），预留 QUIZ / REVIEW / CUSTOM。
 * ref_json 存放节点引用（如 {"documentId": 12, "title": "..."}），结构随 node_type 变化。
 */
@Entity
@Table(name = "learning_path_nodes")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class LearningPathNode {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "path_id", nullable = false)
    private Long pathId;

    @Column(nullable = false, length = 300)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "order_index", nullable = false)
    private Integer orderIndex = 0;

    @Column(name = "node_type", nullable = false, length = 30)
    private String nodeType = "DOCUMENT";

    @Column(name = "ref_json", columnDefinition = "TEXT")
    private String refJson;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (orderIndex == null) orderIndex = 0;
        if (nodeType == null) nodeType = "DOCUMENT";
    }
}
