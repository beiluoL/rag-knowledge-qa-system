package com.example.ragkb.model.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

/**
 * 成就定义（种子数据由 V8 迁移写入）。
 * metric 可为 cards / xp / streak / level，达到 threshold 即解锁。
 */
@Entity
@Table(name = "achievements")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class Achievement {

    @Id
    @Column(length = 50)
    private String code;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 300)
    private String description;

    @Column(length = 50)
    private String icon;

    /** cards / xp / streak / level */
    @Column(length = 30)
    private String metric;

    @Builder.Default
    private Long threshold = 0L;
}
