package com.example.ragkb.repository;

import com.example.ragkb.model.dto.ReferenceDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

/**
 * 向量存储与语义搜索 Repository
 * 使用 pgvector 的余弦相似度进行向量检索
 */
@Repository
@RequiredArgsConstructor
public class ChunkEmbeddingRepository {

    private final JdbcTemplate jdbcTemplate;

    /**
     * 插入向量数据
     */
    public void saveEmbedding(Long chunkId, String embeddingVector) {
        String sql = "INSERT INTO chunk_embeddings (chunk_id, embedding) VALUES (?, ?::vector)";
        jdbcTemplate.update(sql, chunkId, embeddingVector);
    }

    /**
     * 批量插入向量数据
     */
    public void batchSaveEmbeddings(List<Long> chunkIds, List<String> embeddingVectors) {
        String sql = "INSERT INTO chunk_embeddings (chunk_id, embedding) VALUES (?, ?::vector)";
        jdbcTemplate.batchUpdate(sql, chunkIds, chunkIds.size(),
                (ps, chunkId) -> {
                    int idx = chunkIds.indexOf(chunkId);
                    ps.setLong(1, chunkId);
                    ps.setString(2, embeddingVectors.get(idx));
                });
    }

    /**
     * 删除文档关联的所有向量
     */
    public void deleteByDocumentId(Long documentId) {
        String sql = """
            DELETE FROM chunk_embeddings
            WHERE chunk_id IN (SELECT id FROM chunks WHERE document_id = ?)
        """;
        jdbcTemplate.update(sql, documentId);
    }

    /**
     * 余弦相似度语义搜索
     * 返回 Top-K 最相关的分块及其来源文档信息
     */
    public List<ReferenceDTO> semanticSearch(String queryVector, int topK, double threshold) {
        String sql = """
            SELECT
                ce.chunk_id,
                c.content,
                c.document_id,
                d.title AS document_title,
                1 - (ce.embedding <=> ?::vector) AS similarity
            FROM chunk_embeddings ce
            JOIN chunks c ON c.id = ce.chunk_id
            JOIN documents d ON d.id = c.document_id
            WHERE 1 - (ce.embedding <=> ?::vector) >= ?
            ORDER BY ce.embedding <=> ?::vector
            LIMIT ?
        """;

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
     * 获取向量总数
     */
    public long count() {
        String sql = "SELECT COUNT(*) FROM chunk_embeddings";
        Long result = jdbcTemplate.queryForObject(sql, Long.class);
        return result != null ? result : 0;
    }
}
