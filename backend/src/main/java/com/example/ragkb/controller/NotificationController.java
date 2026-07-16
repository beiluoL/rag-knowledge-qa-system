package com.example.ragkb.controller;

import com.example.ragkb.service.NotificationService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    /** 我的通知列表（含全员公告） */
    @GetMapping
    public ResponseEntity<?> list(Authentication authentication) {
        Long userId = Long.parseLong(authentication.getPrincipal().toString());
        return ResponseEntity.ok(notificationService.listForUser(userId));
    }

    /** 未读数量 */
    @GetMapping("/unread-count")
    public ResponseEntity<?> unreadCount(Authentication authentication) {
        Long userId = Long.parseLong(authentication.getPrincipal().toString());
        return ResponseEntity.ok(Map.of("count", notificationService.unreadCount(userId)));
    }

    /** SSE 实时推送连接 */
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(Authentication authentication) {
        Long userId = Long.parseLong(authentication.getPrincipal().toString());
        return notificationService.subscribe(userId);
    }

    /** 标记单条已读 */
    @PostMapping("/mark-read")
    public ResponseEntity<?> markRead(@RequestBody Map<String, Long> body, Authentication authentication) {
        Long userId = Long.parseLong(authentication.getPrincipal().toString());
        notificationService.markRead(body.get("id"), userId);
        return ResponseEntity.ok(Map.of("message", "ok"));
    }

    /** 全部已读 */
    @PostMapping("/mark-all-read")
    public ResponseEntity<?> markAllRead(Authentication authentication) {
        Long userId = Long.parseLong(authentication.getPrincipal().toString());
        notificationService.markAllRead(userId);
        return ResponseEntity.ok(Map.of("message", "ok"));
    }
}
