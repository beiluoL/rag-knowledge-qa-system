package com.example.ragkb.service;

import com.example.ragkb.model.entity.Notification;
import com.example.ragkb.repository.NotificationRepository;
import com.example.ragkb.repository.StudyTaskRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 通知与消息中心
 * - 文档处理完成、学习提醒（复习到期）、系统公告
 * - SSE 实时推送（用户建立长连接后，新通知即时下发）
 */
@Service
@Slf4j
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final StudyTaskRepository studyTaskRepository;

    /** 在线用户的 SSE 连接：userId -> emitter */
    private final Map<Long, SseEmitter> emitters = new ConcurrentHashMap<>();

    public NotificationService(NotificationRepository notificationRepository,
                              StudyTaskRepository studyTaskRepository) {
        this.notificationRepository = notificationRepository;
        this.studyTaskRepository = studyTaskRepository;
    }

    /** 创建通知并实时推送（userId 为 null 表示全员公告） */
    public Notification create(Long userId, String type, String title, String content,
                               String refType, Long refId) {
        Notification n = Notification.builder()
                .userId(userId).type(type).title(title).content(content)
                .refType(refType).refId(refId).isRead(false).build();
        n = notificationRepository.save(n);
        pushToUser(userId, n);
        return n;
    }

    /** 列出当前用户通知（含全员公告），并顺带生成到期复习提醒 */
    public List<Notification> listForUser(Long userId) {
        ensureDueReviewReminders(userId);
        return notificationRepository.findByUserWithGlobal(userId);
    }

    public long unreadCount(Long userId) {
        return notificationRepository.countUnread(userId);
    }

    public void markRead(Long id, Long userId) {
        notificationRepository.findById(id).ifPresent(n -> {
            if (n.getUserId() == null || n.getUserId().equals(userId)) {
                n.setIsRead(true);
                notificationRepository.save(n);
            }
        });
    }

    public void markAllRead(Long userId) {
        notificationRepository.findByUserWithGlobal(userId).forEach(n -> {
            if (!Boolean.TRUE.equals(n.getIsRead())) {
                n.setIsRead(true);
                notificationRepository.save(n);
            }
        });
    }

    /** 建立 SSE 长连接，返回 emitter */
    public SseEmitter subscribe(Long userId) {
        SseEmitter emitter = new SseEmitter(30 * 60 * 1000L); // 30 分钟超时
        emitters.put(userId, emitter);
        emitter.onCompletion(() -> emitters.remove(userId, emitter));
        emitter.onTimeout(() -> emitters.remove(userId, emitter));
        emitter.onError(e -> emitters.remove(userId, emitter));
        try {
            emitter.send(SseEmitter.event()
                    .name("init")
                    .data(Map.of("unread", unreadCount(userId)), MediaType.APPLICATION_JSON));
        } catch (IOException e) {
            emitters.remove(userId);
        }
        return emitter;
    }

    /** 向目标用户（或全员）推送通知 */
    private void pushToUser(Long userId, Notification n) {
        if (userId != null) {
            SseEmitter em = emitters.get(userId);
            if (em != null) {
                try {
                    em.send(SseEmitter.event().name("notification")
                            .data(n, MediaType.APPLICATION_JSON));
                } catch (IOException e) {
                    emitters.remove(userId);
                }
            }
        } else {
            // 全员公告：推送给所有在线用户
            emitters.forEach((u, em) -> {
                try {
                    em.send(SseEmitter.event().name("notification")
                            .data(n, MediaType.APPLICATION_JSON));
                } catch (IOException e) {
                    emitters.remove(u);
                }
            });
        }
    }

    /** 若用户有到期未完成复习任务，且近 1 天无未读复习提醒，则生成一条 */
    private void ensureDueReviewReminders(Long userId) {
        try {
            long due = studyTaskRepository.countByUserIdAndStatusAndDueAtLessThanEqual(
                    userId, "ACTIVE", LocalDateTime.now());
            if (due <= 0) return;
            boolean recent = notificationRepository.findByUserWithGlobal(userId).stream()
                    .anyMatch(n -> "study_reminder".equals(n.getType())
                            && Boolean.FALSE.equals(n.getIsRead())
                            && n.getCreatedAt().isAfter(LocalDateTime.now().minusDays(1)));
            if (recent) return;
            create(userId, "study_reminder", "复习提醒",
                    "你有 " + due + " 项复习任务已到期，回来复习一下吧～", "review", null);
        } catch (Exception e) {
            log.warn("复习提醒生成失败(已忽略): {}", e.getMessage());
        }
    }
}
