package com.example.ragkb.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import reactor.core.publisher.Flux;

/**
 * AI 框架路由器：在运行时按 activeFramework 在
 * Spring AI（DynamicAiProvider）与 LangChain4j（LangChain4jDynamicProvider）之间切换。
 * <p>
 * 框架轴与模式轴（offline/online）正交：
 * - 模式决定用哪套模型 + 哪张向量表（768/1536），由 DynamicAiProvider 持有；
 * - 框架决定用哪个库去调用模型。
 * getMode() 始终委托给 DynamicAiProvider，保证向量表选择与框架无关。
 */
@Slf4j
@Component
public class AiFrameworkRouter implements AiProvider {

    private final DynamicAiProvider springAiProvider;       // 同时是模式持有者
    private final LangChain4jDynamicProvider langchain4jProvider;
    private volatile String activeFramework;

    public AiFrameworkRouter(DynamicAiProvider springAiProvider,
                             LangChain4jDynamicProvider langchain4jProvider,
                             @Value("${app.ai-framework:spring-ai}") String defaultFramework) {
        this.springAiProvider = springAiProvider;
        this.langchain4jProvider = langchain4jProvider;
        this.activeFramework = "langchain4j".equalsIgnoreCase(defaultFramework) ? "langchain4j" : "spring-ai";
    }

    public String getFramework() {
        return activeFramework;
    }

    /**
     * 切换 AI 框架
     */
    public String switchFramework(String framework) {
        if ("langchain4j".equalsIgnoreCase(framework)) {
            activeFramework = "langchain4j";
        } else {
            activeFramework = "spring-ai";
        }
        log.info("AI 框架已切换为: {} (模式: {})", activeFramework, getMode());
        return activeFramework;
    }

    @Override
    public String getMode() {
        return springAiProvider.getMode();
    }

    @Override
    public int getEmbeddingDimension() {
        return active().getEmbeddingDimension();
    }

    @Override
    public String chat(String systemPrompt, String userMessage) {
        return active().chat(systemPrompt, userMessage);
    }

    @Override
    public Flux<String> chatStream(String systemPrompt, String userMessage) {
        return active().chatStream(systemPrompt, userMessage);
    }

    @Override
    public float[] embed(String text) {
        return active().embed(text);
    }

    @Override
    public List<float[]> embedBatch(List<String> texts) {
        return active().embedBatch(texts);
    }

    private AiProvider active() {
        return "langchain4j".equals(activeFramework) ? langchain4jProvider : springAiProvider;
    }
}
