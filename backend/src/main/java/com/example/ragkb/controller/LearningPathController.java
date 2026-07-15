package com.example.ragkb.controller;

import com.example.ragkb.model.entity.LearningPath;
import com.example.ragkb.service.LearningPathService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 学习路径接口（需登录）。提供路径的列表 / 创建 / 从知识库生成 / 详情 / 节点进度更新 / 删除。
 * 路径按用户隔离（service 层校验归属），路径下节点进度同样按用户记录。
 */
@RestController
@RequestMapping("/api/learning/paths")
@RequiredArgsConstructor
public class LearningPathController {

    private final LearningPathService pathService;

    private Long uid(Authentication auth) {
        return Long.parseLong(auth.getPrincipal().toString());
    }

    /** 当前用户的所有学习路径（按更新时间倒序） */
    @GetMapping
    public ResponseEntity<List<LearningPath>> list(Authentication authentication) {
        return ResponseEntity.ok(pathService.listPaths(uid(authentication)));
    }

    /** 手动创建空白路径 */
    @PostMapping
    public ResponseEntity<LearningPath> create(@RequestBody Map<String, Object> body,
                                               Authentication authentication) {
        Long kbId = body.get("knowledgeBaseId") != null
                ? Long.valueOf(body.get("knowledgeBaseId").toString()) : null;
        LearningPath p = pathService.createPath(
                uid(authentication),
                (String) body.get("title"),
                (String) body.get("description"),
                kbId);
        return ResponseEntity.ok(p);
    }

    /** 从知识库一键生成有序学习路线 */
    @PostMapping("/generate")
    public ResponseEntity<LearningPath> generate(@RequestBody Map<String, Object> body,
                                                 Authentication authentication) {
        Long kbId = body.get("knowledgeBaseId") != null
                ? Long.valueOf(body.get("knowledgeBaseId").toString()) : null;
        String title = body.get("title") != null ? body.get("title").toString() : null;
        return ResponseEntity.ok(pathService.generateFromKnowledgeBase(uid(authentication), kbId, title));
    }

    /** 路径详情：节点列表（有序）+ 当前用户各节点进度 + 路径完成度 */
    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> detail(@PathVariable Long id,
                                                      Authentication authentication) {
        return ResponseEntity.ok(pathService.getPathDetail(id, uid(authentication)));
    }

    /** 更新某节点进度（status: NOT_STARTED / IN_PROGRESS / COMPLETED；可选 score） */
    @PutMapping("/{id}/nodes/{nodeId}/progress")
    public ResponseEntity<Map<String, Object>> updateProgress(@PathVariable Long id,
                                                               @PathVariable Long nodeId,
                                                               @RequestBody Map<String, Object> body,
                                                               Authentication authentication) {
        String status = body.get("status") != null ? body.get("status").toString() : null;
        Integer score = body.get("score") != null
                ? Integer.valueOf(body.get("score").toString()) : null;
        return ResponseEntity.ok(pathService.updateNodeProgress(uid(authentication), nodeId, status, score));
    }

    /** 删除路径（级联清理节点与进度） */
    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> delete(@PathVariable Long id,
                                                      Authentication authentication) {
        pathService.deletePath(uid(authentication), id);
        return ResponseEntity.ok(Map.of("deleted", true));
    }
}
