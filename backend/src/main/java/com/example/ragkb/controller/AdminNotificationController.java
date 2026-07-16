package com.example.ragkb.controller;

import com.example.ragkb.service.NotificationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/admin/notifications")
public class AdminNotificationController {

    private final NotificationService notificationService;

    public AdminNotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    /** 管理员发布系统公告：userId 省略=全员公告；也可指定 userId 私信给某人 */
    @PostMapping
    public ResponseEntity<?> announce(@RequestBody Map<String, Object> body) {
        Long target = body.get("userId") != null ? Long.valueOf(body.get("userId").toString()) : null;
        notificationService.create(target, "system_announcement",
                (String) body.get("title"), (String) body.get("content"), "announcement", null);
        return ResponseEntity.ok(Map.of("message", "公告已发布", "scope", target == null ? "all" : "user:" + target));
    }
}
