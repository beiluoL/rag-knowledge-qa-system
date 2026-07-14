package com.example.ragkb.controller;

import com.example.ragkb.service.WritingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 智能写作接口：基于知识库内容辅助撰写学习总结 / 报告 / 文章。
 */
@RestController
@RequestMapping("/api/writing")
@RequiredArgsConstructor
public class WritingController {

    private final WritingService writingService;

    /**
     * 撰写文章
     * @param body { topic, knowledgeBaseId, outline, style, length }
     */
    @PostMapping("/compose")
    public ResponseEntity<?> compose(@RequestBody Map<String, Object> body,
                                     Authentication authentication) {
        String topic = body.get("topic") != null ? body.get("topic").toString() : null;
        Long kbId = body.get("knowledgeBaseId") != null
                ? Long.valueOf(body.get("knowledgeBaseId").toString()) : null;
        String outline = body.get("outline") != null ? body.get("outline").toString() : null;
        String style = body.get("style") != null ? body.get("style").toString() : null;
        int length = body.get("length") != null ? Integer.parseInt(body.get("length").toString()) : 800;
        Map<String, Object> result = writingService.compose(topic, kbId, outline, style, length);
        return ResponseEntity.ok(result);
    }
}
