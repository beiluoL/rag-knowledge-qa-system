package com.example.ragkb.controller;

import com.example.ragkb.service.EvaluationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/admin/evaluation")
public class EvaluationController {

    private final EvaluationService evaluationService;

    public EvaluationController(EvaluationService evaluationService) {
        this.evaluationService = evaluationService;
    }

    /** 评估概览（满意度 + 检索质量 + 被引文档 + 检索不到的问题） */
    @GetMapping
    public ResponseEntity<Map<String, Object>> overview(
            @RequestParam(defaultValue = "10") int topN,
            @RequestParam(defaultValue = "20") int noHitN) {
        return ResponseEntity.ok(evaluationService.getOverview(topN, noHitN));
    }

    @GetMapping("/satisfaction")
    public ResponseEntity<?> satisfaction() {
        return ResponseEntity.ok(evaluationService.getSatisfaction());
    }

    @GetMapping("/retrieval")
    public ResponseEntity<?> retrieval() {
        return ResponseEntity.ok(evaluationService.getRetrievalMetrics());
    }

    @GetMapping("/top-documents")
    public ResponseEntity<?> topDocuments(@RequestParam(defaultValue = "10") int topN) {
        return ResponseEntity.ok(evaluationService.getTopDocuments(topN));
    }

    @GetMapping("/no-hit")
    public ResponseEntity<?> noHit(@RequestParam(defaultValue = "20") int limit) {
        return ResponseEntity.ok(evaluationService.getNoHitQuestions(limit));
    }
}
