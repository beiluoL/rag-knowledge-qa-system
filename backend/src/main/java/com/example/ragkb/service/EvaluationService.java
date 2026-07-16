package com.example.ragkb.service;

import com.example.ragkb.model.entity.Message;
import com.example.ragkb.repository.MessageRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * RAG 效果评估体系
 * 基于 message 表（feedback 点赞踩 + references_data 检索结果）做量化聚合：
 *  - 答案质量：满意度（点赞率）
 *  - 检索质量：命中率、平均引用文档数
 *  - 报表：被引用最多的文档、检索不到的问题
 */
@Service
@Slf4j
public class EvaluationService {

    private final MessageRepository messageRepository;
    private final ObjectMapper objectMapper;

    public EvaluationService(MessageRepository messageRepository, ObjectMapper objectMapper) {
        this.messageRepository = messageRepository;
        this.objectMapper = objectMapper;
    }

    /** 综合评估概览 */
    public Map<String, Object> getOverview(int topN, int noHitN) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("satisfaction", getSatisfaction());
        out.put("retrieval", getRetrievalMetrics());
        out.put("topDocuments", getTopDocuments(topN));
        out.put("noHitQuestions", getNoHitQuestions(noHitN));
        return out;
    }

    /** 答案质量：满意度统计 */
    public Map<String, Object> getSatisfaction() {
        long likes = messageRepository.countByRoleAndFeedback("ASSISTANT", "like");
        long dislikes = messageRepository.countByRoleAndFeedback("ASSISTANT", "dislike");
        long totalAnswered = messageRepository.countByRole("ASSISTANT");
        long rated = likes + dislikes;
        double rate = rated == 0 ? 0 : Math.round(likes * 1000.0 / rated) / 10.0;
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("likes", likes);
        m.put("dislikes", dislikes);
        m.put("totalAnswered", totalAnswered);
        m.put("rated", rated);
        m.put("satisfactionRate", rate); // 点赞率 %
        return m;
    }

    /** 检索质量：命中率、平均引用文档数 */
    public Map<String, Object> getRetrievalMetrics() {
        List<Message> msgs = messageRepository.findByRoleOrderByCreatedAtDesc("ASSISTANT");
        long total = msgs.size();
        long hit = 0;
        long sumRefs = 0;
        for (Message msg : msgs) {
            List<Map<String, Object>> refs = parseRefs(msg.getReferencesData());
            if (!refs.isEmpty()) {
                hit++;
                sumRefs += refs.size();
            }
        }
        double hitRate = total == 0 ? 0 : Math.round(hit * 1000.0 / total) / 10.0;
        double avgRefs = hit == 0 ? 0 : Math.round(sumRefs * 100.0 / hit) / 100.0;
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("totalAnswered", total);
        m.put("hitCount", hit);
        m.put("noHitCount", total - hit);
        m.put("hitRate", hitRate);          // 命中率 %
        m.put("avgReferencedDocs", avgRefs); // 平均引用文档数
        return m;
    }

    /** 被引用最多的文档（基于 references_data 聚合） */
    public List<Map<String, Object>> getTopDocuments(int topN) {
        List<Message> msgs = messageRepository.findByRoleOrderByCreatedAtDesc("ASSISTANT");
        Map<Long, Map<String, Object>> agg = new LinkedHashMap<>();
        for (Message msg : msgs) {
            List<Map<String, Object>> refs = parseRefs(msg.getReferencesData());
            for (Map<String, Object> r : refs) {
                Object idObj = r.get("documentId");
                if (idObj == null) continue;
                Long docId = ((Number) idObj).longValue();
                Map<String, Object> e = agg.computeIfAbsent(docId, k -> {
                    Map<String, Object> x = new LinkedHashMap<>();
                    x.put("documentId", docId);
                    x.put("documentTitle", r.get("documentTitle"));
                    x.put("count", 0L);
                    return x;
                });
                e.put("count", ((Long) e.get("count")) + 1);
            }
        }
        return agg.values().stream()
                .sorted((a, b) -> Long.compare((Long) b.get("count"), (Long) a.get("count")))
                .limit(topN)
                .collect(Collectors.toList());
    }

    /** 检索不到的问题（references_data 为空），还原用户原始提问 */
    public List<Map<String, Object>> getNoHitQuestions(int limit) {
        List<Message> msgs = messageRepository.findByRoleOrderByCreatedAtDesc("ASSISTANT");
        List<Map<String, Object>> result = new ArrayList<>();
        for (Message msg : msgs) {
            List<Map<String, Object>> refs = parseRefs(msg.getReferencesData());
            if (!refs.isEmpty()) continue; // 仅统计未检索到任何片段的回答
            Optional<String> question = messageRepository
                    .findLastUserBefore(msg.getConversationId(), msg.getCreatedAt())
                    .stream().findFirst()
                    .map(Message::getContent);
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("conversationId", msg.getConversationId());
            item.put("question", question.orElse("(未知提问)"));
            item.put("answer", truncate(msg.getContent(), 120));
            item.put("time", msg.getCreatedAt());
            result.add(item);
            if (result.size() >= limit) break;
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> parseRefs(String referencesData) {
        if (referencesData == null || referencesData.isBlank()) return List.of();
        try {
            JsonNode node = objectMapper.readTree(referencesData);
            if (node.isArray() && node.size() > 0) {
                return objectMapper.convertValue(node, new TypeReference<List<Map<String, Object>>>() {});
            }
        } catch (Exception e) {
            // 忽略解析失败
        }
        return List.of();
    }

    private String truncate(String s, int n) {
        if (s == null) return "";
        return s.length() <= n ? s : s.substring(0, n) + "…";
    }
}
