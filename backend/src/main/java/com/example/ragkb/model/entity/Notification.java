package com.example.ragkb.model.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "notifications")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** null 表示全员公告 */
    @Column(name = "user_id")
    private Long userId;

    /** document_processed / study_reminder / system_announcement */
    @Column(nullable = false, length = 30)
    private String type;

    @Column(length = 200)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String content;

    /** 关联业务类型：document / conversation / knowledge_base 等 */
    @Column(name = "ref_type", length = 30)
    private String refType;

    @Column(name = "ref_id")
    private Long refId;

    @Column(name = "is_read", nullable = false)
    private Boolean isRead = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (isRead == null) isRead = false;
    }
}
