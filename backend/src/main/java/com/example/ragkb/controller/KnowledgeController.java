package com.example.ragkb.controller;

import com.example.ragkb.model.entity.Chunk;
import com.example.ragkb.model.entity.Document;
import com.example.ragkb.service.DocumentService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/knowledge")
@RequiredArgsConstructor
public class KnowledgeController {

    private final DocumentService documentService;

    /**
     * 上传文档
     */
    @PostMapping("/documents/upload")
    public ResponseEntity<Map<String, Object>> uploadDocument(
            @RequestParam("file") MultipartFile file,
            Authentication authentication) {
        Long userId = Long.parseLong(authentication.getPrincipal().toString());
        Document doc = documentService.uploadDocument(file, userId);
        return ResponseEntity.ok(Map.of(
                "id", doc.getId(),
                "title", doc.getTitle(),
                "status", doc.getStatus().name(),
                "message", "文档上传成功，正在后台处理中"
        ));
    }

    /**
     * 文档列表（分页）
     */
    @GetMapping("/documents")
    public ResponseEntity<Page<Document>> getDocuments(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(documentService.getDocuments(PageRequest.of(page, size)));
    }

    /**
     * 文档详情
     */
    @GetMapping("/documents/{id}")
    public ResponseEntity<Map<String, Object>> getDocumentDetail(@PathVariable Long id) {
        Document doc = documentService.getDocumentDetail(id);
        List<Chunk> chunks = documentService.getDocumentChunks(id);
        return ResponseEntity.ok(Map.of(
                "document", doc,
                "chunks", chunks
        ));
    }

    /**
     * 删除文档
     */
    @DeleteMapping("/documents/{id}")
    public ResponseEntity<Map<String, String>> deleteDocument(@PathVariable Long id) {
        documentService.deleteDocument(id);
        return ResponseEntity.ok(Map.of("message", "文档已删除"));
    }

    /**
     * 重新处理文档
     */
    @PostMapping("/documents/{id}/reprocess")
    public ResponseEntity<Map<String, String>> reprocessDocument(@PathVariable Long id) {
        documentService.processDocumentAsync(id);
        return ResponseEntity.ok(Map.of("message", "已开始重新处理"));
    }

    /**
     * 知识库统计
     */
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getStats() {
        return ResponseEntity.ok(documentService.getStats());
    }
}
