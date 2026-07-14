package com.example.ragkb.service;

import com.example.ragkb.exception.BusinessException;
import com.example.ragkb.model.entity.Chunk;
import com.example.ragkb.model.entity.Document;
import com.example.ragkb.model.enums.DocumentStatus;
import com.example.ragkb.repository.ChunkEmbeddingRepository;
import com.example.ragkb.repository.ChunkRepository;
import com.example.ragkb.repository.DocumentRepository;
import com.example.ragkb.util.TextSplitter;
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
    private final org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    @Value("${app.storage.upload-dir}")
    private String uploadDir;

    @Value("${app.rag.chunk-size}")
    private int chunkSize;

    @Value("${app.rag.chunk-overlap}")
    private int chunkOverlap;

    /**
     * 上传文档
     */
    @Transactional
    public Document uploadDocument(MultipartFile file, Long userId) {
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
        Document doc = Document.builder()
                .title(originalFilename)
                .fileName(originalFilename)
                .fileType(fileType)
                .filePath(savedPath)
                .fileSize(file.getSize())
                .status(DocumentStatus.PENDING)
                .chunkCount(0)
                .uploadedBy(userId)
                .build();

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

            // 2. 文本切分
            List<String> chunks = textSplitter.split(text, chunkSize, chunkOverlap);
            log.info("文档 id={} 切分为 {} 个分块", documentId, chunks.size());

            // 3. 保存分块
            List<Chunk> chunkEntities = new ArrayList<>();
            for (int i = 0; i < chunks.size(); i++) {
                chunkEntities.add(Chunk.builder()
                        .documentId(documentId)
                        .chunkIndex(i)
                        .content(chunks.get(i))
                        .tokenCount(textSplitter.estimateTokenCount(chunks.get(i)))
                        .build());
            }
            chunkEntities = chunkRepository.saveAll(chunkEntities);

            // 4. 向量化并存储
            List<String> chunkTexts = chunks;
            List<String> vectors = embeddingService.embedBatchInChunks(chunkTexts, 16);

            for (int i = 0; i < chunkEntities.size() && i < vectors.size(); i++) {
                embeddingRepository.saveEmbedding(chunkEntities.get(i).getId(), vectors.get(i));
            }

            // 5. 更新文档状态
            doc.setChunkCount(chunks.size());
            doc.setStatus(DocumentStatus.COMPLETED);
            documentRepository.save(doc);
            log.info("文档处理完成: id={}, chunks={}", documentId, chunks.size());

        } catch (Exception e) {
            log.error("文档处理失败: id={}", documentId, e);
            doc.setStatus(DocumentStatus.FAILED);
            documentRepository.save(doc);
        }
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
        return Map.of(
                "documentCount", documentRepository.countAll(),
                "chunkCount", documentRepository.sumChunkCount(),
                "embeddingCount", embeddingRepository.count()
        );
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
        if (keyword == null || keyword.isBlank()) {
            return getDocuments(pageable, null);
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
    public Document uploadDocument(MultipartFile file, Long userId, Integer customChunkSize, Integer customOverlap) {
        Document doc = uploadDocument(file, userId);
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
    public Document importFromUrl(String url, String mode, Long userId) {
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
            Document doc = Document.builder()
                    .title(title)
                    .fileName(safeTitle + extension)
                    .fileType(fileType)
                    .filePath(filePath.toAbsolutePath().toString())
                    .fileSize((long) content.getBytes(java.nio.charset.StandardCharsets.UTF_8).length)
                    .status(DocumentStatus.PENDING)
                    .chunkCount(0)
                    .uploadedBy(userId)
                    .build();

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
