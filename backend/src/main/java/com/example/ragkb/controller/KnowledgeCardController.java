package com.example.ragkb.controller;

import com.example.ragkb.model.dto.KnowledgeCardRequest;
import com.example.ragkb.model.entity.KnowledgeCard;
import com.example.ragkb.service.KnowledgeCardService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/cards")
@RequiredArgsConstructor
public class KnowledgeCardController {

    private final KnowledgeCardService cardService;

    private Long uid(Authentication auth) {
        return Long.parseLong(auth.getPrincipal().toString());
    }

    /** 列表：支持 ?category=分类 & keyword=关键词 & knowledgeBaseId=知识库 */
    @GetMapping
    public ResponseEntity<List<KnowledgeCard>> list(
            Authentication auth,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long knowledgeBaseId) {
        return ResponseEntity.ok(cardService.listCards(uid(auth), category, keyword, knowledgeBaseId));
    }

    /** 新建 */
    @PostMapping
    public ResponseEntity<KnowledgeCard> create(
            Authentication auth,
            @Valid @RequestBody KnowledgeCardRequest req) {
        return ResponseEntity.ok(cardService.createCard(uid(auth), req));
    }

    /** 详情 */
    @GetMapping("/{id}")
    public ResponseEntity<KnowledgeCard> detail(Authentication auth, @PathVariable Long id) {
        return ResponseEntity.ok(cardService.getCard(uid(auth), id));
    }

    /** 更新 */
    @PutMapping("/{id}")
    public ResponseEntity<KnowledgeCard> update(
            Authentication auth,
            @PathVariable Long id,
            @Valid @RequestBody KnowledgeCardRequest req) {
        return ResponseEntity.ok(cardService.updateCard(uid(auth), id, req));
    }

    /** 删除 */
    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, String>> delete(Authentication auth, @PathVariable Long id) {
        cardService.deleteCard(uid(auth), id);
        return ResponseEntity.ok(Map.of("message", "已删除"));
    }

    /** 批量删除 */
    @PostMapping("/batch-delete")
    public ResponseEntity<Map<String, Object>> batchDelete(
            Authentication auth,
            @RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<Integer> ids = (List<Integer>) body.get("ids");
        if (ids == null || ids.isEmpty()) return ResponseEntity.badRequest().body(Map.of("message", "ids 不能为空"));
        List<Long> longIds = ids.stream().map(Long::valueOf).toList();
        cardService.batchDelete(uid(auth), longIds);
        return ResponseEntity.ok(Map.of("message", "已删除 " + ids.size() + " 张卡片"));
    }

    /** 批量移动到知识库 */
    @PostMapping("/batch-move")
    public ResponseEntity<Map<String, Object>> batchMove(
            Authentication auth,
            @RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<Integer> ids = (List<Integer>) body.get("ids");
        Long kbId = body.get("knowledgeBaseId") == null ? null : Long.valueOf(body.get("knowledgeBaseId").toString());
        if (ids == null || ids.isEmpty()) return ResponseEntity.badRequest().body(Map.of("message", "ids 不能为空"));
        List<Long> longIds = ids.stream().map(Long::valueOf).toList();
        cardService.batchMove(uid(auth), longIds, kbId);
        return ResponseEntity.ok(Map.of("message", "已移动 " + ids.size() + " 张卡片"));
    }

    /** AI 一键生成：根据主题生成若干卡片并落库 */
    @PostMapping("/generate")
    public ResponseEntity<List<KnowledgeCard>> generate(
            Authentication auth,
            @RequestBody Map<String, Object> body) {
        String topic = body.get("topic") == null ? null : String.valueOf(body.get("topic"));
        Integer count = body.get("count") == null ? null : Integer.valueOf(body.get("count").toString());
        String category = body.get("category") == null ? null : String.valueOf(body.get("category"));
        List<KnowledgeCard> cards = cardService.generateFromTopic(uid(auth), topic, count, category);
        return ResponseEntity.ok(cards);
    }

    /** AI 从知识库文档抽取卡片 */
    @PostMapping("/extract-from-kb")
    public ResponseEntity<List<KnowledgeCard>> extractFromKb(
            Authentication auth,
            @RequestParam Long knowledgeBaseId,
            @RequestParam(required = false, defaultValue = "10") Integer count,
            @RequestParam(required = false) String category) {
        List<KnowledgeCard> cards = cardService.extractFromKnowledgeBase(uid(auth), knowledgeBaseId, count, category);
        return ResponseEntity.ok(cards);
    }
}
