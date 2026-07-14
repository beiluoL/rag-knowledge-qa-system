package com.example.ragkb.repository;

import com.example.ragkb.model.entity.Conversation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface ConversationRepository extends JpaRepository<Conversation, Long> {

    List<Conversation> findByUserIdOrderByUpdatedAtDesc(Long userId);

    /** 查询用户会话，置顶优先，再按更新时间倒序 */
    @Query("SELECT c FROM Conversation c WHERE c.userId = :userId ORDER BY c.pinned DESC, c.updatedAt DESC")
    List<Conversation> findByUserIdOrderByPinnedDescUpdatedAtDesc(@Param("userId") Long userId);

    /**
     * 按消息内容关键词搜索用户的会话
     * 使用 PostgreSQL 全文搜索（to_tsvector）匹配消息内容
     *
     * @param userId  用户 ID
     * @param keyword 搜索关键词
     * @return 匹配的会话列表，按更新时间倒序
     */
    @Query(value = """
            SELECT DISTINCT c.* FROM conversations c
            JOIN messages m ON m.conversation_id = c.id
            WHERE c.user_id = :userId
            AND to_tsvector('simple', m.content) @@ plainto_tsquery('simple', :keyword)
            ORDER BY c.updated_at DESC
            """, nativeQuery = true)
    List<Conversation> searchByKeyword(@Param("userId") Long userId,
                                        @Param("keyword") String keyword);
}
