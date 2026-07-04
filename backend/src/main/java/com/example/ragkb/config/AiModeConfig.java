package com.example.ragkb.config;

import com.example.ragkb.service.DashScopeAiProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 创建 DashScopeAiProvider Bean
 * OllamaAiProvider 和 DynamicAiProvider 由 @Component 自动创建
 */
@Configuration
public class AiModeConfig {

    @Value("${app.dashscope.api-key:}")
    private String dashscopeApiKey;

    @Value("${app.dashscope.chat-model:qwen-plus}")
    private String dashscopeChatModel;

    @Value("${app.dashscope.embedding-model:text-embedding-v2}")
    private String dashscopeEmbeddingModel;

    @Bean
    public DashScopeAiProvider dashScopeAiProvider() {
        return new DashScopeAiProvider(dashscopeApiKey,
                dashscopeChatModel, dashscopeEmbeddingModel);
    }
}
