package com.example.ragkb.repository;

import com.example.ragkb.model.entity.Document;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface DocumentRepository extends JpaRepository<Document, Long> {

    Page<Document> findAllByOrderByCreatedAtDesc(Pageable pageable);

    List<Document> findByStatus(String status);

    @Query("SELECT COUNT(d) FROM Document d")
    long countAll();

    @Query("SELECT COALESCE(SUM(d.chunkCount), 0) FROM Document d")
    long sumChunkCount();

    // 搜索：按标题或标签关键词
    Page<Document> findByTitleContainingIgnoreCaseOrTagsContainingIgnoreCase(
            String titleKeyword, String tagsKeyword, Pageable pageable);

    /** 按状态筛选文档（分页） */
    Page<Document> findByStatusOrderByCreatedAtDesc(String status, Pageable pageable);

    /** 按知识库统计/查询文档 */
    List<Document> findByKnowledgeBaseId(Long knowledgeBaseId);

    /** 按多个知识库（含子树）批量查询文档 */
    List<Document> findByKnowledgeBaseIdIn(List<Long> knowledgeBaseIds);

    long countByKnowledgeBaseId(Long knowledgeBaseId);

    /** 删除知识库时，将归属文档置为未分类（不删除文档） */
    @Modifying
    @Query("UPDATE Document d SET d.knowledgeBaseId = null WHERE d.knowledgeBaseId = :kbId")
    void clearKnowledgeBase(@Param("kbId") Long kbId);
}
