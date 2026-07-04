package com.example.ragkb.service;

import java.util.List;

/**
 * AI 提供者接口：统一封装离线 Ollama 和在线 DashScope 两种模式
 */
public interface AiProvider {

    /**
     * 当前模式名称
     */
    String getMode();

    /**
     * 生成对话回答（非流式）
     */
    String chat(String systemPrompt, String userMessage);

    /**
     * 将文本转为向量
     */
    float[] embed(String text);

    /**
     * 批量文本向量化
     */
    List<float[]> embedBatch(List<String> texts);

    /**
     * 获取向量维度
     */
    int getEmbeddingDimension();
}
