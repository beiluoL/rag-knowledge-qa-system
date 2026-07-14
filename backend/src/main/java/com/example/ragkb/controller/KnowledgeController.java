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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/knowledge")
@RequiredArgsConstructor
public class KnowledgeController {

    private final DocumentService documentService;

    // ═══════════════ 上传 ═══════════════

    @PostMapping("/documents/upload")
    public ResponseEntity<Map<String, Object>> uploadDocument(
            @RequestParam("file") MultipartFile file,
            @RequestParam(required = false) Integer chunkSize,
            @RequestParam(required = false) Integer chunkOverlap,
            @RequestParam(required = false) Long knowledgeBaseId,
            Authentication authentication) {
        Long userId = Long.parseLong(authentication.getPrincipal().toString());
        Document doc;
        if (chunkSize != null || chunkOverlap != null) {
            doc = documentService.uploadDocument(file, userId, chunkSize, chunkOverlap, knowledgeBaseId);
        } else {
            doc = documentService.uploadDocument(file, userId, knowledgeBaseId);
        }
        return ResponseEntity.ok(Map.of(
                "id", doc.getId(), "title", doc.getTitle(),
                "status", doc.getStatus().name(), "message", "文档上传成功，正在后台处理中"
        ));
    }

    @PostMapping("/documents/upload-batch")
    public ResponseEntity<Map<String, Object>> uploadDocuments(
            @RequestParam("files") List<MultipartFile> files,
            @RequestParam(required = false) Long knowledgeBaseId,
            Authentication authentication) {
        Long userId = Long.parseLong(authentication.getPrincipal().toString());
        List<Map<String, Object>> results = new ArrayList<>();
        int success = 0, failed = 0;
        for (MultipartFile file : files) {
            try {
                Document doc = documentService.uploadDocument(file, userId, knowledgeBaseId);
                results.add(Map.of("fileName", file.getOriginalFilename(), "id", doc.getId(), "status", "OK"));
                success++;
            } catch (Exception e) {
                results.add(Map.of("fileName", file.getOriginalFilename(), "status", "FAILED", "error", e.getMessage()));
                failed++;
            }
        }
        return ResponseEntity.ok(Map.of("total", files.size(), "success", success, "failed", failed, "results", results));
    }

    @PostMapping("/documents/import-url")
    public ResponseEntity<Map<String, Object>> importFromUrl(
            @RequestBody Map<String, String> body, Authentication authentication) {
        String url = body.get("url");
        String mode = body.getOrDefault("mode", "text");
        Long knowledgeBaseId = body.get("knowledgeBaseId") != null
                ? Long.valueOf(body.get("knowledgeBaseId")) : null;
        Long userId = Long.parseLong(authentication.getPrincipal().toString());
        if (url == null || url.isBlank()) return ResponseEntity.badRequest().body(Map.of("error", "URL 不能为空"));
        Document doc = documentService.importFromUrl(url, mode, userId, knowledgeBaseId);
        return ResponseEntity.ok(Map.of("id", doc.getId(), "title", doc.getTitle(),
                "status", doc.getStatus().name(), "message", "网页内容已导入"));
    }

    // ═══════════════ 查询 ═══════════════

    /**
     * 获取文档列表（支持分页、关键词搜索、状态筛选）
     *
     * @param page    页码（从 0 开始）
     * @param size    每页数量
     * @param keyword 搜索关键词（匹配标题或标签）
     * @param status  状态筛选（COMPLETED/PROCESSING/FAILED/PENDING）
     */
    @GetMapping("/documents")
    public ResponseEntity<Page<Document>> getDocuments(
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status) {
        if (keyword != null && !keyword.isBlank()) {
            return ResponseEntity.ok(documentService.searchDocuments(keyword, PageRequest.of(page, size)));
        }
        return ResponseEntity.ok(documentService.getDocuments(PageRequest.of(page, size), status));
    }

    @GetMapping("/documents/{id}")
    public ResponseEntity<Map<String, Object>> getDocumentDetail(@PathVariable Long id) {
        Document doc = documentService.getDocumentDetail(id);
        List<Map<String, Object>> chunks = documentService.getDocumentChunksWithStatus(id);
        return ResponseEntity.ok(Map.of("document", doc, "chunks", chunks));
    }

    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getStats() {
        return ResponseEntity.ok(documentService.getStats());
    }

    // ═══════════════ 编辑 ═══════════════

    /**
     * 更新文档信息（标题、标签、描述）
     */
    @PutMapping("/documents/{id}")
    public ResponseEntity<Map<String, Object>> updateDocument(
            @PathVariable Long id, @RequestBody Map<String, String> body) {
        Document doc = documentService.updateDocument(id, body.get("title"), body.get("tags"), body.get("description"));
        return ResponseEntity.ok(Map.of("id", doc.getId(), "title", doc.getTitle(),
                "tags", doc.getTags() != null ? doc.getTags() : "",
                "description", doc.getDescription() != null ? doc.getDescription() : "",
                "message", "更新成功"));
    }

    @PostMapping("/documents/{id}/reprocess")
    public ResponseEntity<Map<String, String>> reprocessDocument(@PathVariable Long id) {
        documentService.processDocumentAsync(id);
        return ResponseEntity.ok(Map.of("message", "已开始重新处理"));
    }

    // ═══════════════ 分块操作 ═══════════════

    @PutMapping("/chunks/{chunkId}")
    public ResponseEntity<Map<String, Object>> updateChunk(
            @PathVariable Long chunkId, @RequestBody Map<String, String> body) {
        String content = body.get("content");
        if (content == null || content.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "内容不能为空"));
        }
        Chunk chunk = documentService.updateChunk(chunkId, content);
        return ResponseEntity.ok(Map.of("id", chunk.getId(), "message", "分块已更新"));
    }

    @DeleteMapping("/chunks/{chunkId}")
    public ResponseEntity<Map<String, String>> deleteChunk(@PathVariable Long chunkId) {
        documentService.deleteChunk(chunkId);
        return ResponseEntity.ok(Map.of("message", "分块已删除"));
    }

    // ═══════════════ 批量操作 ═══════════════

    @PostMapping("/documents/batch-delete")
    public ResponseEntity<Map<String, Object>> batchDelete(@RequestBody Map<String, List<Long>> body) {
        return ResponseEntity.ok(documentService.batchDelete(body.get("ids")));
    }

    @DeleteMapping("/documents/clear-all")
    public ResponseEntity<Map<String, Object>> clearAll() {
        return ResponseEntity.ok(documentService.clearAll());
    }

    // ═══════════════ 文件预览 ═══════════════
    @GetMapping("/documents/{id}/content")
    public ResponseEntity<byte[]> getDocumentContent(@PathVariable Long id) {
        Document doc = documentService.getDocumentDetail(id);
        try {
            java.nio.file.Path filePath = java.nio.file.Paths.get(doc.getFilePath());
            byte[] content = java.nio.file.Files.readAllBytes(filePath);
            String mimeType = switch (doc.getFileType().toLowerCase()) {
                case "pdf" -> "application/pdf";
                case "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
                case "doc" -> "application/msword";
                case "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
                case "xls" -> "application/vnd.ms-excel";
                case "csv" -> "text/csv";
                case "png" -> "image/png";
                case "jpg", "jpeg" -> "image/jpeg";
                default -> "application/octet-stream";
            };
            return ResponseEntity.ok()
                    .header("Content-Type", mimeType)
                    .header("Content-Disposition", "inline; filename=\"" + doc.getFileName() + "\"")
                    .body(content);
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }

    // ═══════════════ 导出 ═══════════════

    @GetMapping("/documents/export")
    public ResponseEntity<List<Map<String, Object>>> exportDocuments(
            @RequestParam(defaultValue = "json") String format) {
        return ResponseEntity.ok(documentService.exportAll());
    }
}
