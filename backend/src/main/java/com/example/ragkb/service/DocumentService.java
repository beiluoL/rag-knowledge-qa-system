package com.example.ragkb.service;

import com.example.ragkb.exception.BusinessException;
import com.example.ragkb.model.entity.Chunk;
import com.example.ragkb.model.entity.Document;
import com.example.ragkb.model.entity.KbCategory;
import com.example.ragkb.model.entity.KnowledgeBase;
import com.example.ragkb.model.enums.DocumentStatus;
import com.example.ragkb.repository.ChunkEmbeddingRepository;
import com.example.ragkb.repository.ChunkRepository;
import com.example.ragkb.repository.DocumentRepository;
import com.example.ragkb.repository.KbCategoryRepository;
import com.example.ragkb.repository.KnowledgeBaseRepository;
import com.example.ragkb.util.TextSplitter;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.sax.BodyContentHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class DocumentService {

    private final DocumentRepository documentRepository;
    private final ChunkRepository chunkRepository;
    private final ChunkEmbeddingRepository embeddingRepository;
    private final EmbeddingService embeddingService;
    private final TextSplitter textSplitter;
    private final DynamicAiProvider aiProvider;
    private final KnowledgeBaseRepository knowledgeBaseRepository;
    private final KbCategoryRepository kbCategoryRepository;
    private final org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;
    /** AI 框架路由器：用于自动加工（摘要 / 关键词）调用大模型 */
    private final AiFrameworkRouter aiRouter;
    private final ObjectMapper objectMapper;

    @Value("${app.ai.auto-enrich:true}")
    private boolean autoEnrich;

    /** 由知识库的分类 ID 解析分类名称（用于文档冗余字段） */
    private String categoryNameOf(KnowledgeBase kb) {
        if (kb == null || kb.getCategoryId() == null) return null;
        return kbCategoryRepository.findById(kb.getCategoryId())
                .map(KbCategory::getName).orElse(null);
    }

    @Value("${app.storage.upload-dir}")
    private String uploadDir;

    @Value("${app.rag.chunk-size}")
    private int chunkSize;

    @Value("${app.rag.chunk-overlap}")
    private int chunkOverlap;

    /**
     * 上传文档（不指定知识库）
     */
    @Transactional
    public Document uploadDocument(MultipartFile file, Long userId) {
        return uploadDocument(file, userId, null);
    }

    /**
     * 上传文档并归属到知识库（泛化多领域）
     */
    @Transactional
    public Document uploadDocument(MultipartFile file, Long userId, Long knowledgeBaseId) {
        // 校验文件
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || originalFilename.isBlank()) {
            throw new BusinessException("文件名不能为空");
        }

        String fileType = getFileType(originalFilename);
        if (!isSupportedType(fileType)) {
            throw new BusinessException("不支持的文件类型: " + fileType + "，支持: pdf, txt, md, docx, xlsx");
        }

        // 保存文件
        String savedPath = saveFile(file);

        // 创建文档记录
        Document.DocumentBuilder builder = Document.builder()
                .title(originalFilename)
                .fileName(originalFilename)
                .fileType(fileType)
                .filePath(savedPath)
                .fileSize(file.getSize())
                .status(DocumentStatus.PENDING)
                .chunkCount(0)
                .uploadedBy(userId);
        if (knowledgeBaseId != null) {
            knowledgeBaseRepository.findById(knowledgeBaseId).ifPresent(kb -> {
                builder.knowledgeBaseId(knowledgeBaseId);
                builder.category(categoryNameOf(kb));
            });
        }
        Document doc = builder.build();

        doc = documentRepository.save(doc);
        log.info("文档上传成功: {} (id: {})", originalFilename, doc.getId());

        // 异步处理文档
        processDocumentAsync(doc.getId());

        return doc;
    }

    /**
     * 异步处理文档：解析 → 分块 → 向量化
     */
    @Async("documentTaskExecutor")
    public void processDocumentAsync(Long documentId) {
        Document doc = documentRepository.findById(documentId).orElse(null);
        if (doc == null) return;

        try {
            // 更新状态为处理中
            doc.setStatus(DocumentStatus.PROCESSING);
            documentRepository.save(doc);

            // 1. 使用 Tika 解析文档
            String text = parseDocument(doc.getFilePath(), doc.getFileType());
            if (text == null || text.isBlank()) {
                throw new BusinessException("文档内容为空或无法解析");
            }

            // 2~5. 文本切分 → 向量化 → 落库（同步复用）
            embedAndStore(doc, text);

            // 6. AI 自动加工：生成摘要 + 提取关键词标签（仅对真实上传文档，跳过种子文本）
            if (!"text".equals(doc.getFileType())) {
                enrichDocument(doc, text);
            }
            log.info("文档处理完成: id={}, chunks={}", documentId, doc.getChunkCount());

        } catch (Exception e) {
            log.error("文档处理失败: id={}", documentId, e);
            doc.setStatus(DocumentStatus.FAILED);
            documentRepository.save(doc);
        }
    }

    /**
     * 文本切分 → 向量化 → 落库（同步）。供异步处理与种子初始化复用。
     */
    private void embedAndStore(Document doc, String text) {
        List<String> chunks = textSplitter.split(text, chunkSize, chunkOverlap);
        log.info("文档 id={} 切分为 {} 个分块", doc.getId(), chunks.size());

        // 保存分块
        List<Chunk> chunkEntities = new ArrayList<>();
        for (int i = 0; i < chunks.size(); i++) {
            chunkEntities.add(Chunk.builder()
                    .documentId(doc.getId())
                    .chunkIndex(i)
                    .content(chunks.get(i))
                    .tokenCount(textSplitter.estimateTokenCount(chunks.get(i)))
                    .build());
        }
        chunkEntities = chunkRepository.saveAll(chunkEntities);

        // 向量化并存储
        List<String> vectors = embeddingService.embedBatchInChunks(chunks, 16);
        for (int i = 0; i < chunkEntities.size() && i < vectors.size(); i++) {
            embeddingRepository.saveEmbedding(chunkEntities.get(i).getId(), vectors.get(i));
        }

        // 更新文档状态
        doc.setChunkCount(chunks.size());
        doc.setStatus(DocumentStatus.COMPLETED);
        documentRepository.save(doc);
    }

    /**
     * AI 自动加工：在向量化完成后，调用大模型为文档生成一句话摘要（description）
     * 并提取关键词标签（tags）。失败不影响主流程（文档已 COMPLETED）。
     */
    private void enrichDocument(Document doc, String text) {
        if (!autoEnrich) return;
        try {
            String excerpt = text.length() > 3000 ? text.substring(0, 3000) : text;
            String system = "你是知识库文档助理。只输出一个 JSON 对象，不要包含 Markdown 代码块标记：" +
                    "{\"summary\":\"一句话中文摘要，不超过60字\",\"keywords\":[\"关键词1\",\"关键词2\"，最多8个]}。";
            String user = "文档标题：" + doc.getTitle() + "\n文档内容：\n" + excerpt;
            String raw = aiRouter.chat(system, user);

            String json = raw;
            int s = json.indexOf('{');
            int e = json.lastIndexOf('}');
            if (s >= 0 && e > s) json = json.substring(s, e + 1);

            Map<String, Object> parsed = objectMapper.readValue(json,
                    new TypeReference<Map<String, Object>>() {});
            String summary = parsed.get("summary") != null ? parsed.get("summary").toString() : null;
            List<String> keywords = new ArrayList<>();
            Object kw = parsed.get("keywords");
            if (kw instanceof List) {
                for (Object o : (List<?>) kw) {
                    if (o != null) keywords.add(o.toString().trim());
                }
            }

            Document toUpdate = documentRepository.findById(doc.getId()).orElse(null);
            if (toUpdate != null) {
                if (summary != null && !summary.isBlank()) toUpdate.setDescription(summary);
                if (!keywords.isEmpty()) toUpdate.setTags(String.join(",", keywords));
                documentRepository.save(toUpdate);
                log.info("文档自动加工完成: id={}, 关键词={}", doc.getId(), keywords.size());
            }
        } catch (Exception ex) {
            log.warn("文档自动加工失败（不影响主流程）: id={}", doc.getId(), ex);
        }
    }

    /**
     * 直接以文本灌入一篇示例文档（用于预置知识库初始化，同步完成分块与向量化）。
     * 文件相关字段置空，状态直接为 COMPLETED。
     */
    @Transactional
    public void seedTextDocument(String title, String content, Long knowledgeBaseId, Long userId) {
        KnowledgeBase kb = knowledgeBaseRepository.findById(knowledgeBaseId).orElse(null);
        Document doc = Document.builder()
                .title(title)
                .fileName(title)
                .fileType("text")
                .filePath("")
                .fileSize((long) content.length())
                .status(DocumentStatus.COMPLETED)
                .knowledgeBaseId(knowledgeBaseId)
                .category(kb != null ? categoryNameOf(kb) : null)
                .chunkCount(0)
                .uploadedBy(userId)
                .build();
        doc = documentRepository.save(doc);
        embedAndStore(doc, content);
    }

    /** 统计某知识库下的文档数（供示例灌库幂等判断） */
    public long countDocs(Long knowledgeBaseId) {
        return documentRepository.countByKnowledgeBaseId(knowledgeBaseId);
    }

    /**
     * 解析文档文本（使用 Apache Tika）
     */
    private String parseDocument(String filePath, String fileType) {
        try {
            java.io.InputStream inputStream = Files.newInputStream(Path.of(filePath));
            BodyContentHandler handler = new BodyContentHandler(-1); // 无长度限制
            AutoDetectParser parser = new AutoDetectParser();
            org.apache.tika.metadata.Metadata metadata = new org.apache.tika.metadata.Metadata();
            metadata.set(org.apache.tika.metadata.TikaCoreProperties.RESOURCE_NAME_KEY,
                    java.nio.file.Paths.get(filePath).getFileName().toString());
            parser.parse(inputStream, handler, metadata);
            inputStream.close();
            return handler.toString().trim();
        } catch (Exception e) {
            log.error("Tika 解析失败: {}", filePath, e);
            // 如果 Tika 解析失败，尝试直接读取纯文本
            try {
                return Files.readString(Path.of(filePath));
            } catch (IOException ex) {
                throw new BusinessException("文档解析失败: " + e.getMessage());
            }
        }
    }

    /**
     * 获取文档列表（分页，支持状态筛选）
     *
     * @param pageable 分页参数
     * @param status   文档状态筛选（COMPLETED/PROCESSING/FAILED/PENDING），null 表示不筛选
     */
    public Page<Document> getDocuments(Pageable pageable, String status) {
        return getDocuments(pageable, status, null);
    }

    /**
     * 获取文档列表（分页，支持状态筛选 + 知识库筛选）
     */
    public Page<Document> getDocuments(Pageable pageable, String status, Long knowledgeBaseId) {
        if (knowledgeBaseId != null) {
            if (status != null && !status.isBlank()) {
                return documentRepository.findByStatusAndKnowledgeBaseIdOrderByCreatedAtDesc(status, knowledgeBaseId, pageable);
            }
            return documentRepository.findByKnowledgeBaseIdOrderByCreatedAtDesc(knowledgeBaseId, pageable);
        }
        if (status != null && !status.isBlank()) {
            return documentRepository.findByStatusOrderByCreatedAtDesc(status, pageable);
        }
        return documentRepository.findAllByOrderByCreatedAtDesc(pageable);
    }

    /**
     * 获取文档详情及分块
     */
    public Document getDocumentDetail(Long id) {
        return documentRepository.findById(id)
                .orElseThrow(() -> new BusinessException("文档不存在"));
    }

    public List<Chunk> getDocumentChunks(Long documentId) {
        return chunkRepository.findByDocumentIdOrderByChunkIndexAsc(documentId);
    }

    /**
     * 删除文档及其关联数据
     */
    @Transactional
    public void deleteDocument(Long documentId) {
        Document doc = documentRepository.findById(documentId)
                .orElseThrow(() -> new BusinessException("文档不存在"));

        // 删除向量数据
        embeddingRepository.deleteByDocumentId(documentId);
        // 删除分块（级联删除向量）
        chunkRepository.deleteByDocumentId(documentId);
        // 删除物理文件
        try {
            Files.deleteIfExists(Path.of(doc.getFilePath()));
        } catch (IOException e) {
            log.warn("删除文件失败: {}", doc.getFilePath());
        }
        // 删除文档记录
        documentRepository.deleteById(documentId);
        log.info("文档已删除: id={}", documentId);
    }

    /**
     * 知识库统计
     */
    public Map<String, Object> getStats() {
        return getStats(null);
    }

    /** 知识库维度统计 */
    public Map<String, Object> getStats(Long knowledgeBaseId) {
        if (knowledgeBaseId != null) {
            return Map.of(
                    "documentCount", documentRepository.countByKnowledgeBaseId(knowledgeBaseId),
                    "chunkCount", documentRepository.sumChunkCountByKnowledgeBaseId(knowledgeBaseId),
                    "embeddingCount", embeddingRepository.count());
        }
        return Map.of(
                "documentCount", documentRepository.countAll(),
                "chunkCount", documentRepository.sumChunkCount(),
                "embeddingCount", embeddingRepository.count());
    }

    /**
     * 更新文档信息（标题、标签、描述）
     */
    @Transactional
    public Document updateDocument(Long id, String title, String tags, String description) {
        Document doc = documentRepository.findById(id)
                .orElseThrow(() -> new BusinessException("文档不存在"));
        if (title != null && !title.isBlank()) doc.setTitle(title);
        if (tags != null) doc.setTags(tags);
        if (description != null) doc.setDescription(description);
        return documentRepository.save(doc);
    }

    /**
     * 搜索文档（按标题或标签关键词）
     */
    public Page<Document> searchDocuments(String keyword, Pageable pageable) {
        return searchDocuments(keyword, pageable, null);
    }

    public Page<Document> searchDocuments(String keyword, Pageable pageable, Long knowledgeBaseId) {
        if (keyword == null || keyword.isBlank()) {
            return getDocuments(pageable, null, knowledgeBaseId);
        }
        if (knowledgeBaseId != null) {
            return documentRepository.searchByKeywordAndKnowledgeBaseId(keyword, knowledgeBaseId, pageable);
        }
        return documentRepository.findByTitleContainingIgnoreCaseOrTagsContainingIgnoreCase(
                keyword, keyword, pageable);
    }

    /**
     * 批量删除文档
     */
    @Transactional
    public Map<String, Object> batchDelete(List<Long> ids) {
        int deleted = 0;
        for (Long id : ids) {
            try {
                deleteDocument(id);
                deleted++;
            } catch (Exception e) {
                log.warn("删除文档失败: id={}", id, e);
            }
        }
        return Map.of("total", ids.size(), "deleted", deleted);
    }

    /**
     * 清空所有文档
     */
    @Transactional
    public Map<String, Object> clearAll() {
        List<Document> allDocs = documentRepository.findAll();
        int count = allDocs.size();
        for (Document doc : allDocs) {
            try {
                embeddingRepository.deleteByDocumentId(doc.getId());
                chunkRepository.deleteByDocumentId(doc.getId());
            } catch (Exception e) {
                log.warn("清理文档数据失败: id={}", doc.getId());
            }
        }
        documentRepository.deleteAll();
        return Map.of("deleted", count);
    }

    /**
     * 更新分块内容
     */
    @Transactional
    public Chunk updateChunk(Long chunkId, String content) {
        Chunk chunk = chunkRepository.findById(chunkId)
                .orElseThrow(() -> new BusinessException("分块不存在"));
        chunk.setContent(content);
        chunk.setTokenCount(textSplitter.estimateTokenCount(content));
        return chunkRepository.save(chunk);
    }

    /**
     * 删除单个分块
     */
    @Transactional
    public void deleteChunk(Long chunkId) {
        Chunk chunk = chunkRepository.findById(chunkId)
                .orElseThrow(() -> new BusinessException("分块不存在"));
        // 更新文档分块计数
        Document doc = documentRepository.findById(chunk.getDocumentId()).orElse(null);
        if (doc != null) {
            doc.setChunkCount(Math.max(0, (doc.getChunkCount() != null ? doc.getChunkCount() : 0) - 1));
            documentRepository.save(doc);
        }
        chunkRepository.delete(chunk);
    }

    /**
     * 导出文档内容（拼接所有分块）
     */
    public String exportDocumentContent(Long documentId) {
        List<Chunk> chunks = chunkRepository.findByDocumentIdOrderByChunkIndexAsc(documentId);
        StringBuilder sb = new StringBuilder();
        for (Chunk c : chunks) {
            sb.append(c.getContent()).append("\n\n");
        }
        return sb.toString();
    }

    /**
     * 导出知识库数据
     */
    public List<Map<String, Object>> exportAll() {
        List<Document> docs = documentRepository.findAll();
        List<Map<String, Object>> result = new ArrayList<>();
        for (Document doc : docs) {
            List<Chunk> chunks = chunkRepository.findByDocumentIdOrderByChunkIndexAsc(doc.getId());
            List<Map<String, Object>> chunkList = chunks.stream().map(c -> Map.<String, Object>of(
                    "chunkIndex", c.getChunkIndex(),
                    "content", c.getContent(),
                    "tokenCount", c.getTokenCount()
            )).toList();

            result.add(Map.of(
                    "id", doc.getId(),
                    "title", doc.getTitle(),
                    "fileType", doc.getFileType(),
                    "tags", doc.getTags() != null ? doc.getTags() : "",
                    "status", doc.getStatus().name(),
                    "chunkCount", doc.getChunkCount(),
                    "createdAt", doc.getCreatedAt().toString(),
                    "chunks", chunkList
            ));
        }
        return result;
    }

    /**
     * 上传文档（支持自定义切分参数）
     */
    @Transactional
    public Document uploadDocument(MultipartFile file, Long userId, Integer customChunkSize, Integer customOverlap, Long knowledgeBaseId) {
        Document doc = uploadDocument(file, userId, knowledgeBaseId);
        // 暂存自定义参数，异步处理时使用
        if (customChunkSize != null) {
            chunkSize = customChunkSize;
        }
        if (customOverlap != null) {
            chunkOverlap = customOverlap;
        }
        return doc;
    }

    /**
     * 获取文档分块（含向量状态）
     */
    public List<Map<String, Object>> getDocumentChunksWithStatus(Long documentId) {
        List<Chunk> chunks = chunkRepository.findByDocumentIdOrderByChunkIndexAsc(documentId);
        List<Map<String, Object>> result = new ArrayList<>();
        for (Chunk c : chunks) {
            boolean hasEmbedding = false;
            try {
                String table = "offline".equals(aiProvider.getMode())
                        ? "chunk_embeddings" : "chunk_embeddings_online";
                Long count = jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM " + table + " WHERE chunk_id = ?",
                        Long.class, c.getId());
                hasEmbedding = count != null && count > 0;
            } catch (Exception ignored) {}

            result.add(Map.of(
                    "id", c.getId(),
                    "chunkIndex", c.getChunkIndex(),
                    "content", c.getContent(),
                    "tokenCount", c.getTokenCount(),
                    "hasEmbedding", hasEmbedding
            ));
        }
        return result;
    }

    /**
     * 获取上传目录的绝对路径
     * 如果配置的是相对路径（如 ./data/documents），
     * 则解析为用户主目录下的绝对路径，避免 Tomcat 临时目录干扰
     */
    private Path getUploadPath() {
        Path path = Paths.get(uploadDir);
        if (!path.isAbsolute()) {
            // 相对路径 → 放到用户主目录下，确保有写入权限且不受 Tomcat 工作目录影响
            String relativePath = uploadDir.startsWith("./")
                    ? uploadDir.substring(2)
                    : uploadDir;
            path = Paths.get(System.getProperty("user.home"), relativePath);
        }
        return path;
    }

    private String saveFile(MultipartFile file) {
        try {
            Path uploadPath = getUploadPath();
            Files.createDirectories(uploadPath);

            String originalFilename = file.getOriginalFilename();
            String extension = "";
            if (originalFilename != null && originalFilename.contains(".")) {
                extension = originalFilename.substring(originalFilename.lastIndexOf("."));
            }
            String savedName = UUID.randomUUID().toString() + extension;
            Path filePath = uploadPath.resolve(savedName);
            file.transferTo(filePath.toFile());

            log.info("文件已保存: {}", filePath.toAbsolutePath());
            return filePath.toAbsolutePath().toString();
        } catch (IOException e) {
            throw new BusinessException("文件保存失败: " + e.getMessage());
        }
    }

    private String getFileType(String filename) {
        if (filename == null) return "unknown";
        int dotIndex = filename.lastIndexOf('.');
        if (dotIndex == -1) return "unknown";
        return filename.substring(dotIndex + 1).toLowerCase();
    }

    private boolean isSupportedType(String fileType) {
        return List.of("pdf", "txt", "md", "docx", "xlsx", "doc", "xls", "csv", "html", "xml").contains(fileType);
    }

    /**
     * URL 导入：抓取网页内容，保存为文档
     * @param url 网页地址
     * @param mode "text" 抓取纯文本 | "pdf" 抓取内容另存为文档
     */
    @Transactional
    public Document importFromUrl(String url, String mode, Long userId, Long knowledgeBaseId) {
        try {
            // 使用 Jsoup 抓取网页
            org.jsoup.Connection conn = org.jsoup.Jsoup.connect(url)
                    .userAgent("Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36")
                    .timeout(15000);
            org.jsoup.nodes.Document htmlDoc = conn.get();

            String title = htmlDoc.title();
            if (title == null || title.isBlank()) {
                title = url.replaceAll("https?://", "").replaceAll("[^a-zA-Z0-9\\u4e00-\\u9fa5]", "-");
                if (title.length() > 100) title = title.substring(0, 100);
            }

            String content;
            if ("text".equals(mode)) {
                // 纯文本模式：提取正文
                content = htmlDoc.body().text();
            } else {
                // PDF/文档模式：保留 HTML 结构
                content = htmlDoc.body().html();
            }

            if (content == null || content.isBlank()) {
                throw new BusinessException("网页内容为空，无法导入");
            }

            // 保存为文件
            String safeTitle = title.replaceAll("[\\\\/:*?\"<>|]", "_");
            Path uploadPath = getUploadPath();
            Files.createDirectories(uploadPath);
            String extension = "text".equals(mode) ? ".txt" : ".html";
            String savedName = UUID.randomUUID().toString() + "_" + safeTitle + extension;
            Path filePath = uploadPath.resolve(savedName);
            Files.writeString(filePath, content);

            // 创建文档记录
            String fileType = "text".equals(mode) ? "txt" : "html";
            Document.DocumentBuilder builder = Document.builder()
                    .title(title)
                    .fileName(safeTitle + extension)
                    .fileType(fileType)
                    .filePath(filePath.toAbsolutePath().toString())
                    .fileSize((long) content.getBytes(java.nio.charset.StandardCharsets.UTF_8).length)
                    .status(DocumentStatus.PENDING)
                    .chunkCount(0)
                    .uploadedBy(userId);
            if (knowledgeBaseId != null) {
                knowledgeBaseRepository.findById(knowledgeBaseId).ifPresent(kb -> {
                    builder.knowledgeBaseId(knowledgeBaseId);
                    builder.category(categoryNameOf(kb));
                });
            }
            Document doc = builder.build();

            doc = documentRepository.save(doc);
            log.info("URL 导入成功: {} → {}", url, doc.getTitle());

            // 异步处理
            processDocumentAsync(doc.getId());

            return doc;

        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("URL 导入失败: {}", url, e);
            throw new BusinessException("URL 导入失败: " + e.getMessage());
        }
    }
}
