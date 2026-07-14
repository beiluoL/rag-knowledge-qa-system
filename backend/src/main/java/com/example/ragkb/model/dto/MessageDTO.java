package com.example.ragkb.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MessageDTO {
    private Long id;
    private String role;
    private String content;
    private List<ReferenceDTO> references;
    /** 用户反馈：like/dislike/null */
    private String feedback;
    private LocalDateTime createdAt;
}
