package com.example.ragkb.controller;

import com.example.ragkb.model.entity.OperationLog;
import com.example.ragkb.model.entity.User;
import com.example.ragkb.repository.ConversationRepository;
import com.example.ragkb.repository.DocumentRepository;
import com.example.ragkb.repository.MessageRepository;
import com.example.ragkb.service.OperationLogService;
import com.example.ragkb.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 管理员用户管理控制器
 * 所有端点需要 ADMIN 角色权限（由 SecurityConfig 控制）
 */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final UserService userService;
    private final DocumentRepository documentRepository;
    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final OperationLogService operationLogService;

    /**
     * 获取所有用户列表（管理员专用）
     * 返回用户基本信息，按创建时间倒序
     */
    @GetMapping("/users")
    public ResponseEntity<List<Map<String, Object>>> getAllUsers() {
        List<User> users = userService.getAllUsers();
        List<Map<String, Object>> result = users.stream()
                .map(this::toUserMap)
                .toList();
        return ResponseEntity.ok(result);
    }

    /**
     * 切换用户启用/禁用状态（管理员专用）
     * 管理员不能禁用自己
     *
     * @param id             目标用户 ID
     * @param authentication 当前管理员的认证信息
     */
    @PutMapping("/users/{id}/toggle")
    public ResponseEntity<Map<String, String>> toggleUser(
            @PathVariable Long id, Authentication authentication) {
        Long operatorId = Long.parseLong(authentication.getPrincipal().toString());
        userService.toggleUserEnabled(id, operatorId);
        return ResponseEntity.ok(Map.of("message", "操作成功"));
    }

    /**
     * 修改用户角色（管理员专用）
     * 管理员不能修改自己的角色
     *
     * @param id             目标用户 ID
     * @param body           包含 "role" 字段（ADMIN 或 USER）
     * @param authentication 当前管理员的认证信息
     */
    @PutMapping("/users/{id}/role")
    public ResponseEntity<Map<String, String>> updateRole(
            @PathVariable Long id,
            @RequestBody Map<String, String> body,
            Authentication authentication) {
        Long operatorId = Long.parseLong(authentication.getPrincipal().toString());
        userService.updateUserRole(id, operatorId, body.get("role"));
        return ResponseEntity.ok(Map.of("message", "角色修改成功"));
    }

    /**
     * 将用户实体转换为前端展示用的 Map（隐藏敏感字段）
     */
    private Map<String, Object> toUserMap(User user) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", user.getId());
        map.put("username", user.getUsername());
        map.put("email", user.getEmail() != null ? user.getEmail() : "");
        map.put("nickname", user.getNickname() != null ? user.getNickname() : "");
        map.put("role", user.getRole().name());
        map.put("enabled", user.getEnabled());
        map.put("createdAt", user.getCreatedAt().toString());
        return map;
    }

    // ═══════════════ 系统统计 ═══════════════

    /**
     * 获取系统全局统计数据（管理员专用）
     * 返回用户数、文档数、会话数、消息数等汇总信息
     */
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getSystemStats() {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("totalUsers", userService.getAllUsers().size());
        stats.put("totalDocuments", documentRepository.count());
        stats.put("totalChunks", documentRepository.sumChunkCount());
        stats.put("totalConversations", conversationRepository.count());
        stats.put("totalMessages", messageRepository.count());
        return ResponseEntity.ok(stats);
    }

    // ═══════════════ 操作日志 ═══════════════

    /**
     * 获取操作日志（管理员专用）
     *
     * @param page 页码（从 0 开始）
     * @param size 每页数量
     */
    @GetMapping("/logs")
    public ResponseEntity<Page<OperationLog>> getOperationLogs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(operationLogService.getLogs(PageRequest.of(page, size)));
    }
}
