package com.example.ragkb.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReferenceDTO {
    private Long documentId;
    private String documentTitle;
    private Long chunkId;
    private String contentSnippet;
    private Double score;
}
