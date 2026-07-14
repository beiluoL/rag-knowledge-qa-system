package com.example.ragkb.controller;

import com.example.ragkb.service.QuizService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 智能出题接口：基于知识库内容生成选择题 / 问答题。
 */
@RestController
@RequestMapping("/api/quiz")
@RequiredArgsConstructor
public class QuizController {

    private final QuizService quizService;

    /**
     * 生成练习题
     * @param body { knowledgeBaseId, type("mc"|"qa"), count }
     */
    @PostMapping("/generate")
    public ResponseEntity<?> generate(@RequestBody Map<String, Object> body,
                                      Authentication authentication) {
        Long kbId = body.get("knowledgeBaseId") != null
                ? Long.valueOf(body.get("knowledgeBaseId").toString()) : null;
        String type = body.get("type") != null ? body.get("type").toString() : "mc";
        int count = body.get("count") != null ? Integer.parseInt(body.get("count").toString()) : 5;
        List<Map<String, Object>> questions = quizService.generate(kbId, type, count);
        return ResponseEntity.ok(Map.of("type", type, "count", questions.size(), "questions", questions));
    }
}
