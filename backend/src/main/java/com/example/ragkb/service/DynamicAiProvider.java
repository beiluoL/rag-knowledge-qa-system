package com.example.ragkb.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 动态 AI 提供者：运行时切换离线/在线模式
 */
@Component
@Slf4j
public class DynamicAiProvider implements AiProvider {

    private final AiProvider ollamaProvider;
    private final AiProvider dashScopeProvider;
    private volatile String activeMode = "offline";

    public DynamicAiProvider(OllamaAiProvider ollamaProvider,
                              DashScopeAiProvider dashScopeProvider) {
        this.ollamaProvider = ollamaProvider;
        this.dashScopeProvider = dashScopeProvider;
        this.activeMode = "offline";
    }

    @Override
    public String getMode() {
        return activeMode;
    }

    @Override
    public int getEmbeddingDimension() {
        return current().getEmbeddingDimension();
    }

    /**
     * 切换模式
     */
    public String switchMode(String mode) {
        if ("online".equalsIgnoreCase(mode)) {
            activeMode = "online";
        } else {
            activeMode = "offline";
        }
        log.info("AI 模式已切换为: {} (维度: {})",
                activeMode, getEmbeddingDimension());
        return activeMode;
    }

    @Override
    public String chat(String systemPrompt, String userMessage) {
        return current().chat(systemPrompt, userMessage);
    }

    @Override
    public float[] embed(String text) {
        return current().embed(text);
    }

    @Override
    public List<float[]> embedBatch(List<String> texts) {
        return current().embedBatch(texts);
    }

    private AiProvider current() {
        return "online".equals(activeMode) ? dashScopeProvider : ollamaProvider;
    }
}
