package com.example.ragkb.model.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ChatRequest {

    private Long conversationId;

    /** 指定检索的知识库（可选；为空则检索全部文档） */
    private Long knowledgeBaseId;

    @NotBlank(message = "问题不能为空")
    private String question;
}
