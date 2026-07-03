package com.example.ragkb.model.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ChatRequest {

    private Long conversationId;

    @NotBlank(message = "问题不能为空")
    private String question;
}
