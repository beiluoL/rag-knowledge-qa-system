package com.example.ragkb.repository;

import com.example.ragkb.model.entity.KnowledgeCard;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface KnowledgeCardRepository extends JpaRepository<KnowledgeCard, Long> {

    List<KnowledgeCard> findByUserIdOrderByUpdatedAtDesc(Long userId);

    List<KnowledgeCard> findByUserIdAndCategoryOrderByUpdatedAtDesc(Long userId, String category);

    /** 按知识库子树批量查询（学习模式用） */
    List<KnowledgeCard> findByKnowledgeBaseIdIn(List<Long> knowledgeBaseIds);

    /** 单个知识库的卡片 */
    List<KnowledgeCard> findByKnowledgeBaseId(Long knowledgeBaseId);

    /**
     * 关键词检索：匹配 标题 / 正面 / 背面 / 标签（不区分大小写）
     */
    @Query("""
           SELECT c FROM KnowledgeCard c
           WHERE c.userId = :userId
             AND (
               LOWER(c.title) LIKE LOWER(CONCAT('%', :kw, '%'))
            OR LOWER(c.front) LIKE LOWER(CONCAT('%', :kw, '%'))
            OR LOWER(c.back)  LIKE LOWER(CONCAT('%', :kw, '%'))
            OR LOWER(c.tags)  LIKE LOWER(CONCAT('%', :kw, '%'))
             )
           ORDER BY c.updatedAt DESC
           """)
    List<KnowledgeCard> searchByKeyword(@Param("userId") Long userId, @Param("kw") String keyword);
}
