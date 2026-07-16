package com.example.ragkb.repository;

import com.example.ragkb.model.entity.Message;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface MessageRepository extends JpaRepository<Message, Long> {

    List<Message> findByConversationIdOrderByCreatedAtAsc(Long conversationId);

    List<Message> findByConversationIdOrderByCreatedAtDesc(Long conversationId);

    void deleteByConversationId(Long conversationId);

    // ── 评估体系用 ──
    long countByRole(String role);

    long countByRoleAndFeedback(String role, String feedback);

    List<Message> findByRoleOrderByCreatedAtDesc(String role);

    /** 取某助手消息之前、同一会话中最近的一条用户发言（用于还原"检索不到的问题"） */
    @Query("SELECT m FROM Message m WHERE m.role = 'USER' AND m.conversationId = :cid "
            + "AND m.createdAt < :t ORDER BY m.createdAt DESC")
    List<Message> findLastUserBefore(@Param("cid") Long conversationId,
                                     @Param("t") LocalDateTime createdAt);
}
