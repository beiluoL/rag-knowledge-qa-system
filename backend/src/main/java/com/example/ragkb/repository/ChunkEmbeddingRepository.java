package com.example.ragkb.repository;

import com.example.ragkb.model.dto.ReferenceDTO;
import com.example.ragkb.service.DynamicAiProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

@Repository
public class ChunkEmbeddingRepository {

    private final JdbcTemplate jdbcTemplate;
    private final DynamicAiProvider aiProvider;

    public ChunkEmbeddingRepository(JdbcTemplate jdbcTemplate, DynamicAiProvider aiProvider) {
        this.jdbcTemplate = jdbcTemplate;
        this.aiProvider = aiProvider;
    }

    /**
     * 获取当前模式使用的向量表名
     */
    private String tableName() {
        return "offline".equals(aiProvider.getMode())
                ? "chunk_embeddings"
                : "chunk_embeddings_online";
    }

    /**
     * 插入向量数据
     */
    public void saveEmbedding(Long chunkId, String embeddingVector) {
        String sql = "INSERT INTO " + tableName()
                + " (chunk_id, embedding) VALUES (?, ?::vector)";
        jdbcTemplate.update(sql, chunkId, embeddingVector);
    }

    /**
     * 删除文档关联的所有向量（清理两张表）
     */
    public void deleteByDocumentId(Long documentId) {
        for (String table : new String[]{"chunk_embeddings", "chunk_embeddings_online"}) {
            String sql = "DELETE FROM " + table
                    + " WHERE chunk_id IN (SELECT id FROM chunks WHERE document_id = ?)";
            jdbcTemplate.update(sql, documentId);
        }
    }

    /**
     * 余弦相似度语义搜索
     */
    public List<ReferenceDTO> semanticSearch(String queryVector, int topK, double threshold) {
        String sql = """
            SELECT
                ce.chunk_id,
                c.content,
                c.document_id,
                d.title AS document_title,
                1 - (ce.embedding <=> ?::vector) AS similarity
            FROM %s ce
            JOIN chunks c ON c.id = ce.chunk_id
            JOIN documents d ON d.id = c.document_id
            WHERE 1 - (ce.embedding <=> ?::vector) >= ?
            ORDER BY ce.embedding <=> ?::vector
            LIMIT ?
        """.formatted(tableName());

        return jdbcTemplate.query(sql,
                new Object[]{queryVector, queryVector, threshold, queryVector, topK},
                this::mapToReferenceDTO);
    }

    private ReferenceDTO mapToReferenceDTO(ResultSet rs, int rowNum) throws SQLException {
        return ReferenceDTO.builder()
                .chunkId(rs.getLong("chunk_id"))
                .documentId(rs.getLong("document_id"))
                .documentTitle(rs.getString("document_title"))
                .contentSnippet(truncateContent(rs.getString("content"), 200))
                .score(rs.getDouble("similarity"))
                .build();
    }

    private String truncateContent(String content, int maxLen) {
        if (content == null) return "";
        return content.length() > maxLen
                ? content.substring(0, maxLen) + "..."
                : content;
    }

    /**
     * 获取向量总数（两张表合计）
     */
    public long count() {
        Long c1 = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM chunk_embeddings", Long.class);
        Long c2 = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM chunk_embeddings_online", Long.class);
        return (c1 != null ? c1 : 0) + (c2 != null ? c2 : 0);
    }
}
