package com.example.ragkb.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.ollama.OllamaEmbeddingModel;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Embedding 向量化服务
 * 使用 Ollama bge-m3 模型将文本转换为向量
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EmbeddingService {

    private final OllamaEmbeddingModel embeddingModel;

    /**
     * 单条文本向量化
     * 返回向量字符串，格式: [0.1,0.2,...]
     */
    public String embed(String text) {
        float[] vector = embeddingModel.embed(text);
        return vectorToString(vector);
    }

    /**
     * 批量文本向量化
     * 返回向量字符串列表
     */
    public List<String> embedBatch(List<String> texts) {
        List<float[]> vectors = embeddingModel.embed(texts);
        List<String> results = new ArrayList<>();
        for (float[] vector : vectors) {
            results.add(vectorToString(vector));
        }
        return results;
    }

    /**
     * 批量文本向量化（按批次处理，避免单次请求过大）
     */
    public List<String> embedBatchInChunks(List<String> texts, int batchSize) {
        List<String> allVectors = new ArrayList<>();
        for (int i = 0; i < texts.size(); i += batchSize) {
            int end = Math.min(i + batchSize, texts.size());
            List<String> batch = texts.subList(i, end);
            List<String> batchVectors = embedBatch(batch);
            allVectors.addAll(batchVectors);
            log.info("向量化进度: {}/{}", end, texts.size());
        }
        return allVectors;
    }

    private String vectorToString(float[] vector) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) sb.append(",");
            sb.append(vector[i]);
        }
        sb.append("]");
        return sb.toString();
    }
}
