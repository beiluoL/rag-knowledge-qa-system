package com.example.ragkb.controller;

import com.example.ragkb.service.DynamicAiProvider;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/ai-mode")
public class AiModeController {

    private final DynamicAiProvider aiProvider;

    public AiModeController(DynamicAiProvider aiProvider) {
        this.aiProvider = aiProvider;
    }

    /**
     * 获取当前 AI 模式
     */
    @GetMapping
    public ResponseEntity<Map<String, String>> getMode() {
        return ResponseEntity.ok(Map.of(
                "mode", aiProvider.getMode(),
                "dimension", String.valueOf(aiProvider.getEmbeddingDimension())
        ));
    }

    /**
     * 切换 AI 模式
     */
    @PostMapping("/switch")
    public ResponseEntity<Map<String, String>> switchMode(@RequestBody Map<String, String> body) {
        String targetMode = body.getOrDefault("mode", "offline");
        String newMode = aiProvider.switchMode(targetMode);
        return ResponseEntity.ok(Map.of(
                "mode", newMode,
                "dimension", String.valueOf(aiProvider.getEmbeddingDimension()),
                "message", "已切换为 " + ("online".equals(newMode) ? "在线模式（阿里云百炼）" : "离线模式（Ollama 本地）")
        ));
    }
}
