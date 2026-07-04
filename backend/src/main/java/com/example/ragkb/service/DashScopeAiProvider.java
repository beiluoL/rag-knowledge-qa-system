package com.example.ragkb.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 在线模式：调用阿里云百炼 DashScope API（OpenAI 兼容接口）
 */
@Slf4j
public class DashScopeAiProvider implements AiProvider {

    private final RestClient restClient;
    private final String chatModel;
    private final String embeddingModel;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final String BASE_URL = "https://dashscope.aliyuncs.com/compatible-mode/v1";

    public DashScopeAiProvider(String apiKey, String chatModel, String embeddingModel) {
        this.chatModel = chatModel;
        this.embeddingModel = embeddingModel;
        this.restClient = RestClient.builder()
                .baseUrl(BASE_URL)
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .defaultHeader("Content-Type", "application/json")
                .build();
    }

    @Override
    public String getMode() {
        return "online";
    }

    @Override
    public String chat(String systemPrompt, String userMessage) {
        try {
            Map<String, Object> requestBody = Map.of(
                    "model", chatModel,
                    "messages", List.of(
                            Map.of("role", "system", "content", systemPrompt),
                            Map.of("role", "user", "content", userMessage)
                    ),
                    "temperature", 0.3,
                    "max_tokens", 1024
            );

            String response = restClient.post()
                    .uri("/chat/completions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestBody)
                    .retrieve()
                    .body(String.class);

            JsonNode root = objectMapper.readTree(response);
            String content = root.path("choices").get(0)
                    .path("message").path("content").asText();
            log.info("DashScope 回答生成成功，长度: {}", content.length());
            return content;

        } catch (Exception e) {
            log.error("DashScope API 调用失败", e);
            throw new RuntimeException("在线 AI 服务调用失败: " + e.getMessage(), e);
        }
    }

    @Override
    public float[] embed(String text) {
        List<float[]> results = embedBatch(List.of(text));
        return results.isEmpty() ? new float[0] : results.get(0);
    }

    @Override
    public List<float[]> embedBatch(List<String> texts) {
        try {
            Map<String, Object> requestBody = Map.of(
                    "model", embeddingModel,
                    "input", texts
            );

            String response = restClient.post()
                    .uri("/embeddings")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestBody)
                    .retrieve()
                    .body(String.class);

            JsonNode root = objectMapper.readTree(response);
            List<float[]> embeddings = new ArrayList<>();

            JsonNode dataArray = root.path("data");
            for (JsonNode item : dataArray) {
                JsonNode embeddingNode = item.path("embedding");
                float[] vector = new float[embeddingNode.size()];
                for (int i = 0; i < embeddingNode.size(); i++) {
                    vector[i] = embeddingNode.get(i).floatValue();
                }
                embeddings.add(vector);
            }

            log.info("DashScope 向量化完成: {} 条", embeddings.size());
            return embeddings;

        } catch (Exception e) {
            log.error("DashScope Embedding 调用失败", e);
            throw new RuntimeException("在线向量化失败: " + e.getMessage(), e);
        }
    }

    @Override
    public int getEmbeddingDimension() {
        return 1536; // text-embedding-v2
    }
}
