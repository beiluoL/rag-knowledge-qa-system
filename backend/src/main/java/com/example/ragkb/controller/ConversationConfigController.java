package com.example.ragkb.controller;

import com.example.ragkb.service.ConversationConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 对话配置管理（管理员专用）：AI 模式 / AI 框架 / RAG 可视化开关。
 * 路径位于 /api/admin/** ，由 SecurityConfig 全局要求 ADMIN 角色。
 */
@RestController
@RequestMapping("/api/admin/conversation-config")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class ConversationConfigController {

    private final ConversationConfigService configService;

    /**
     * 获取当前对话配置（含 embedding 维度）
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> getConfig() {
        return ResponseEntity.ok(configService.getConfig());
    }

    /**
     * 更新对话配置。body 中 aiMode / aiFramework / ragVisualizationEnabled 均可选（只更新提供的字段）。
     * 示例：
     * { "aiMode": "online" }
     * { "aiFramework": "langchain4j" }
     * { "ragVisualizationEnabled": false }
     */
    @PutMapping
    public ResponseEntity<Map<String, Object>> updateConfig(@RequestBody Map<String, Object> body) {
        String aiMode = body.get("aiMode") != null ? body.get("aiMode").toString() : null;
        String aiFramework = body.get("aiFramework") != null ? body.get("aiFramework").toString() : null;
        Boolean ragVisualizationEnabled = null;
        if (body.get("ragVisualizationEnabled") != null) {
            ragVisualizationEnabled = Boolean.valueOf(body.get("ragVisualizationEnabled").toString());
        }
        return ResponseEntity.ok(configService.updateConfig(aiMode, aiFramework, ragVisualizationEnabled));
    }
}
