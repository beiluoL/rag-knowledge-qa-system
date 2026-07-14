package com.example.ragkb.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Embedding 向量化服务
 * 根据 ai-mode 选择离线 Ollama 或在线 DashScope，根据 ai-framework 选择调用库
 */
@Service
@Slf4j
public class EmbeddingService {

    private final AiFrameworkRouter aiProvider;

    public EmbeddingService(AiFrameworkRouter aiProvider) {
        this.aiProvider = aiProvider;
    }

    public String getMode() {
        return aiProvider.getMode();
    }

    public String getFramework() {
        return aiProvider.getFramework();
    }

    public int getDimension() {
        return aiProvider.getEmbeddingDimension();
    }

    /**
     * 单条文本向量化
     */
    public String embed(String text) {
        float[] vector = aiProvider.embed(text);
        return vectorToString(vector);
    }

    /**
     * 批量文本向量化
     */
    public List<String> embedBatch(List<String> texts) {
        List<float[]> vectors = aiProvider.embedBatch(texts);
        List<String> results = new ArrayList<>();
        for (float[] vector : vectors) {
            results.add(vectorToString(vector));
        }
        return results;
    }

    /**
     * 批量文本向量化（按批次处理）
     */
    public List<String> embedBatchInChunks(List<String> texts, int batchSize) {
        List<String> allVectors = new ArrayList<>();
        for (int i = 0; i < texts.size(); i += batchSize) {
            int end = Math.min(i + batchSize, texts.size());
            List<String> batch = texts.subList(i, end);
            List<String> batchVectors = embedBatch(batch);
            allVectors.addAll(batchVectors);
            log.info("向量化进度 [{},{}]: {}/{}", aiProvider.getMode(), aiProvider.getEmbeddingDimension(), end, texts.size());
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
