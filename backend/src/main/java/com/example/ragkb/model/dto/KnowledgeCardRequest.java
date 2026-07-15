package com.example.ragkb.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 知识卡片创建/更新请求 */
@Data
public class KnowledgeCardRequest {

    /** 标题（必填，最大 300 字符） */
    @NotBlank(message = "标题不能为空")
    @Size(max = 300, message = "标题最多 300 个字符")
    private String title;

    /** 正面：问题 / 术语（可选） */
    private String front;

    /** 背面：答案 / 解析 / 正文（必填） */
    @NotBlank(message = "背面内容不能为空")
    private String back;

    /** 分类（可选） */
    @Size(max = 100, message = "分类最多 100 个字符")
    private String category;

    /** 标签：逗号分隔（可选） */
    private String tags;
}
