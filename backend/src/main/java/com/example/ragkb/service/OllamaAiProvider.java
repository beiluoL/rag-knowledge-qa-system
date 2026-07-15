package com.example.ragkb.service;

import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.OllamaEmbeddingModel;
import org.springframework.stereotype.Component;

import java.util.List;
import reactor.core.publisher.Flux;

/**
 * 离线模式：使用本地 Ollama 模型
 */
@Component
public class OllamaAiProvider implements AiProvider {

    private final OllamaChatModel chatModel;
    private final OllamaEmbeddingModel embeddingModel;

    public OllamaAiProvider(OllamaChatModel chatModel, OllamaEmbeddingModel embeddingModel) {
        this.chatModel = chatModel;
        this.embeddingModel = embeddingModel;
    }

    @Override
    public String getMode() {
        return "offline";
    }

    @Override
    public String chat(String systemPrompt, String userMessage) {
        Prompt prompt = new Prompt(List.of(
                new SystemMessage(systemPrompt),
                new UserMessage(userMessage)
        ));
        return chatModel.call(prompt).getResult().getOutput().getText();
    }

    @Override
    public Flux<String> chatStream(String systemPrompt, String userMessage) {
        Prompt prompt = new Prompt(List.of(
                new SystemMessage(systemPrompt),
                new UserMessage(userMessage)
        ));
        return chatModel.stream(prompt)
                .map(ChatResponse::getResult)
                .map(generation -> generation.getOutput().getText())
                .filter(text -> text != null && !text.isEmpty());
    }

    @Override
    public float[] embed(String text) {
        return embeddingModel.embed(text);
    }

    @Override
    public List<float[]> embedBatch(List<String> texts) {
        return embeddingModel.embed(texts);
    }

    @Override
    public int getEmbeddingDimension() {
        return 768; // nomic-embed-text
    }
}
