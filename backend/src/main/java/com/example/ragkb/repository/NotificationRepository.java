package com.example.ragkb.repository;

import com.example.ragkb.model.entity.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {

    /** 取某用户的通知：专属通知 + 全员公告(NULL user_id)，按时间倒序 */
    @Query("SELECT n FROM Notification n WHERE n.userId = :userId OR n.userId IS NULL ORDER BY n.createdAt DESC")
    List<Notification> findByUserWithGlobal(@Param("userId") Long userId);

    /** 未读数量（专属 + 全员公告中未读的） */
    @Query("SELECT COUNT(n) FROM Notification n WHERE (n.userId = :userId OR n.userId IS NULL) AND n.isRead = false")
    long countUnread(@Param("userId") Long userId);

    @Query("SELECT n FROM Notification n WHERE (n.userId = :userId OR n.userId IS NULL) AND n.isRead = false ORDER BY n.createdAt DESC")
    List<Notification> findUnread(@Param("userId") Long userId);
}
