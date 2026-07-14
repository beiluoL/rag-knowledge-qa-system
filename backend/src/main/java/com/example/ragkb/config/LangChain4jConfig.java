package com.example.ragkb.config;

import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.model.ollama.OllamaEmbeddingModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * LangChain4j 模型配置
 * 复用 application.yml 中已有的 Ollama / DashScope 配置，
 * 让 LangChain4j 框架与 Spring AI 框架指向同一套底层模型，
 * 保证两种框架下生成的向量维度一致（离线 768 / 在线 1536）。
 */
@Configuration
public class LangChain4jConfig {

    private static final String DASHSCOPE_BASE_URL = "https://dashscope.aliyuncs.com/compatible-mode/v1";

    @Value("${spring.ai.ollama.base-url:http://localhost:11434}")
    private String ollamaBaseUrl;

    @Value("${spring.ai.ollama.chat.model:qwen2.5-coder:7b}")
    private String ollamaChatModel;

    @Value("${spring.ai.ollama.embedding.model:nomic-embed-text:latest}")
    private String ollamaEmbeddingModel;

    @Value("${app.dashscope.api-key:}")
    private String dashscopeApiKey;

    @Value("${app.dashscope.chat-model:qwen-plus}")
    private String dashscopeChatModel;

    @Value("${app.dashscope.embedding-model:text-embedding-v2}")
    private String dashscopeEmbeddingModel;

    @Bean
    public OllamaChatModel langchainOllamaChatModel() {
        return OllamaChatModel.builder()
                .baseUrl(ollamaBaseUrl)
                .modelName(ollamaChatModel)
                .temperature(0.3)
                .build();
    }

    @Bean
    public OllamaEmbeddingModel langchainOllamaEmbeddingModel() {
        return OllamaEmbeddingModel.builder()
                .baseUrl(ollamaBaseUrl)
                .modelName(ollamaEmbeddingModel)
                .build();
    }

    @Bean
    public OpenAiChatModel langchainDashScopeChatModel() {
        return OpenAiChatModel.builder()
                .baseUrl(DASHSCOPE_BASE_URL)
                .apiKey(dashscopeApiKey)
                .modelName(dashscopeChatModel)
                .temperature(0.3)
                .build();
    }

    @Bean
    public OpenAiEmbeddingModel langchainDashScopeEmbeddingModel() {
        return OpenAiEmbeddingModel.builder()
                .baseUrl(DASHSCOPE_BASE_URL)
                .apiKey(dashscopeApiKey)
                .modelName(dashscopeEmbeddingModel)
                .build();
    }
}
