package com.example.ragkb.controller;

import com.example.ragkb.model.entity.KnowledgeBase;
import com.example.ragkb.service.KnowledgeBaseService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 知识库管理接口（泛化多领域，支持树状结构与动态分类）。
 * 登录用户即可浏览 / 创建；删除非系统库时仅解除文档归属，不删除文档。
 */
@RestController
@RequestMapping("/api/knowledge-bases")
@RequiredArgsConstructor
public class KnowledgeBaseController {

    private final KnowledgeBaseService knowledgeBaseService;

    @GetMapping
    public ResponseEntity<List<KnowledgeBase>> list() {
        return ResponseEntity.ok(knowledgeBaseService.listAll());
    }

    @GetMapping("/tree")
    public ResponseEntity<List<KnowledgeBaseService.KbTreeItem>> tree() {
        return ResponseEntity.ok(knowledgeBaseService.getTree());
    }

    @GetMapping("/{id}")
    public ResponseEntity<KnowledgeBase> get(@PathVariable Long id) {
        return ResponseEntity.ok(knowledgeBaseService.getById(id));
    }

    @PostMapping
    public ResponseEntity<KnowledgeBase> create(@RequestBody Map<String, Object> body,
                                                Authentication authentication) {
        Long userId = Long.parseLong(authentication.getPrincipal().toString());
        Long categoryId = toLong(body.get("categoryId"));
        Long parentId = toLong(body.get("parentId"));
        KnowledgeBase kb = knowledgeBaseService.create(
                (String) body.get("name"), (String) body.get("description"),
                categoryId, parentId, (String) body.get("tags"), userId, false);
        return ResponseEntity.ok(kb);
    }

    @PutMapping("/{id}")
    public ResponseEntity<KnowledgeBase> update(@PathVariable Long id,
                                                @RequestBody Map<String, Object> body) {
        Long categoryId = toLong(body.get("categoryId"));
        Long parentId = toLong(body.get("parentId"));
        return ResponseEntity.ok(knowledgeBaseService.update(
                id, (String) body.get("name"), (String) body.get("description"),
                categoryId, parentId, (String) body.get("tags")));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, String>> delete(@PathVariable Long id) {
        knowledgeBaseService.delete(id);
        return ResponseEntity.ok(Map.of("message", "删除成功"));
    }

    @GetMapping("/{id}/stats")
    public ResponseEntity<Map<String, Object>> stats(@PathVariable Long id) {
        return ResponseEntity.ok(knowledgeBaseService.stats(id));
    }

    private Long toLong(Object v) {
        if (v == null) return null;
        if (v instanceof Number n) return n.longValue();
        String s = v.toString().trim();
        return s.isEmpty() ? null : Long.valueOf(s);
    }
}
