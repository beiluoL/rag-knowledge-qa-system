package com.example.ragkb.model.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 用户学习进度：经验（xp）、等级（level）、连续学习天数（streak）、累计卡片数。
 * 每个用户一条记录（user_id 唯一）。
 */
@Entity
@Table(name = "user_progress")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class UserProgress {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, unique = true)
    private Long userId;

    @Builder.Default
    private Long xp = 0L;

    @Builder.Default
    private Integer level = 1;

    @Builder.Default
    private Integer currentStreak = 0;

    @Builder.Default
    private Integer longestStreak = 0;

    private LocalDate lastStudyDate;

    @Builder.Default
    private Integer cardsStudied = 0;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (xp == null) xp = 0L;
        if (level == null) level = 1;
        if (currentStreak == null) currentStreak = 0;
        if (cardsStudied == null) cardsStudied = 0;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
