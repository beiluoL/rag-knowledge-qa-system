package com.example.ragkb.service;

import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.model.ollama.OllamaEmbeddingModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * LangChain4j 实现的 AI 提供者。
 * 与 DynamicAiProvider（Spring AI）平行：按当前模式选择 Ollama（离线）或
 * DashScope（在线，OpenAI 兼容接口）的 LangChain4j 模型。
 * 向量维度与 DynamicAiProvider 保持一致（离线 768 / 在线 1536），
 * 因此两种框架下写入的向量可共用同一张 pgvector 表。
 */
@Slf4j
@Component
public class LangChain4jDynamicProvider implements AiProvider {

    private final DynamicAiProvider modeProvider; // 模式持有者（offline/online）
    private final OllamaChatModel ollamaChatModel;
    private final OllamaEmbeddingModel ollamaEmbeddingModel;
    private final OpenAiChatModel dashScopeChatModel;
    private final OpenAiEmbeddingModel dashScopeEmbeddingModel;

    public LangChain4jDynamicProvider(DynamicAiProvider modeProvider,
                                      OllamaChatModel ollamaChatModel,
                                      OllamaEmbeddingModel ollamaEmbeddingModel,
                                      OpenAiChatModel dashScopeChatModel,
                                      OpenAiEmbeddingModel dashScopeEmbeddingModel) {
        this.modeProvider = modeProvider;
        this.ollamaChatModel = ollamaChatModel;
        this.ollamaEmbeddingModel = ollamaEmbeddingModel;
        this.dashScopeChatModel = dashScopeChatModel;
        this.dashScopeEmbeddingModel = dashScopeEmbeddingModel;
    }

    @Override
    public String getMode() {
        return modeProvider.getMode();
    }

    @Override
    public String chat(String systemPrompt, String userMessage) {
        boolean online = "online".equals(modeProvider.getMode());
        var model = online ? dashScopeChatModel : ollamaChatModel;
        Response<dev.langchain4j.data.message.AiMessage> resp =
                model.generate(SystemMessage.from(systemPrompt), UserMessage.from(userMessage));
        String content = resp.content().text();
        log.info("LangChain4j 回答生成成功（{}），长度: {}", modeProvider.getMode(), content.length());
        return content;
    }

    @Override
    public float[] embed(String text) {
        return currentEmbeddingModel().embed(text).content().vector();
    }

    @Override
    public List<float[]> embedBatch(List<String> texts) {
        EmbeddingModel model = currentEmbeddingModel();
        List<float[]> result = new ArrayList<>();
        for (String t : texts) {
            result.add(model.embed(t).content().vector());
        }
        return result;
    }

    @Override
    public int getEmbeddingDimension() {
        return "online".equals(modeProvider.getMode()) ? 1536 : 768;
    }

    private EmbeddingModel currentEmbeddingModel() {
        return "online".equals(modeProvider.getMode()) ? dashScopeEmbeddingModel : ollamaEmbeddingModel;
    }
}
