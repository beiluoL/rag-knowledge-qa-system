package com.example.ragkb.service;

import com.example.ragkb.model.entity.Message;
import com.example.ragkb.model.entity.UserMemory;
import com.example.ragkb.repository.MessageRepository;
import com.example.ragkb.repository.UserMemoryRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 对话记忆增强服务
 * 1) 长期记忆：跨会话记住用户偏好/关键事实，注入 RAG System Prompt
 * 2) 记忆抽取：每轮对话后用 LLM 抽取明确陈述的偏好（异步、去重）
 * 3) 记忆摘要：超长对话自动压缩为 summary 记忆，避免上下文无限膨胀
 */
@Service
@Slf4j
public class MemoryService {

    private final UserMemoryRepository memoryRepository;
    private final MessageRepository messageRepository;
    private final AiFrameworkRouter aiProvider;
    private final ObjectMapper objectMapper;

    private static final int MAX_INJECTED = 8;
    private static final int SUMMARY_THRESHOLD = 24;
    private static final int SUMMARY_TAKE = 12;

    public MemoryService(UserMemoryRepository memoryRepository,
                         MessageRepository messageRepository,
                         AiFrameworkRouter aiProvider,
                         ObjectMapper objectMapper) {
        this.memoryRepository = memoryRepository;
        this.messageRepository = messageRepository;
        this.aiProvider = aiProvider;
        this.objectMapper = objectMapper;
    }

    /** 注入到 System Prompt 的长期记忆块（同步，供 RAG 使用）。无记忆返回空串。 */
    public String getMemoryContextPrompt(Long userId) {
        if (userId == null) return "";
        List<UserMemory> mems = memoryRepository.findByUserIdOrderByImportanceDescCreatedAtDesc(userId);
        if (mems.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        sb.append("\n【用户长期记忆（跨会话记住的偏好与关键事实，在不与参考资料冲突的前提下适当参考）】\n");
        int n = 0;
        for (UserMemory m : mems) {
            if (n++ >= MAX_INJECTED) break;
            if ("summary".equals(m.getMemoryType())) {
                sb.append("- 历史摘要：").append(m.getContent()).append("\n");
            } else {
                sb.append("- 偏好/事实：").append(m.getContent()).append("\n");
            }
        }
        return sb.toString();
    }

    /** 异步抽取本轮对话中的长期偏好（在独立线程执行，不阻塞 SSE 响应） */
    @Async("documentTaskExecutor")
    public void extractMemoriesAsync(Long userId, Long conversationId, String userText, String aiText) {
        try {
            if (userText == null || userText.isBlank()) return;
            String system = "你是用户记忆提取助手。请从【用户最新发言】中抽取用户明确表达的、跨会话有用的长期偏好或事实"
                    + "（如关注的领域、偏好的回答风格、身份角色等）。只抽取用户明确陈述的内容，不要猜测。"
                    + "若没有可抽取内容，返回空数组 []。";
            String user = "【用户最新发言】\n" + userText
                    + "\n\n请仅输出 JSON 数组，元素形如 {\"content\":\"偏好描述\",\"importance\":3}"
                    + "（importance 取值 1~5，越重要越大）。不要输出任何其他文字。";
            String resp = aiProvider.chat(system, user);
            List<Extracted> list = parseExtracted(resp);
            if (list == null || list.isEmpty()) return;
            List<UserMemory> existing = memoryRepository.findByUserIdAndMemoryTypeOrderByImportanceDescCreatedAtDesc(userId, "preference");
            for (Extracted e : list) {
                String c = (e.content == null ? "" : e.content.trim());
                if (c.length() < 2) continue;
                boolean dup = existing.stream().anyMatch(m ->
                        m.getContent().equalsIgnoreCase(c)
                                || containsIgnoreCase(m.getContent(), c)
                                || containsIgnoreCase(c, m.getContent()));
                if (dup) continue;
                int imp = (e.importance == null) ? 3 : Math.max(1, Math.min(5, e.importance));
                memoryRepository.save(UserMemory.builder()
                        .userId(userId).memoryType("preference").content(c)
                        .sourceConversationId(conversationId).importance(imp).build());
                log.info("已记住用户 {} 的偏好: {}", userId, c);
            }
        } catch (Exception e) {
            log.warn("记忆提取失败(已忽略): {}", e.getMessage());
        }
    }

    /** 异步：超长对话摘要压缩（保留关键信息，避免上下文无限膨胀） */
    @Async("documentTaskExecutor")
    public void maybeSummarizeAsync(Long userId, Long conversationId) {
        try {
            List<Message> msgs = messageRepository.findByConversationIdOrderByCreatedAtAsc(conversationId);
            if (msgs.size() < SUMMARY_THRESHOLD) return;
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < Math.min(SUMMARY_TAKE, msgs.size()); i++) {
                Message m = msgs.get(i);
                sb.append(m.getRole()).append(": ").append(m.getContent()).append("\n");
            }
            String system = "你是对话摘要助手。请将下面的早期对话压缩成不超过 150 字的关键信息摘要"
                    + "（保留用户目标、重要结论与待办），只输出摘要正文。";
            String summary = aiProvider.chat(system, sb.toString()).trim();
            if (summary.isEmpty()) return;
            memoryRepository.save(UserMemory.builder()
                    .userId(userId).memoryType("summary").content(summary)
                    .sourceConversationId(conversationId).importance(2).build());
            log.info("已为会话 {} 生成摘要记忆", conversationId);
        } catch (Exception e) {
            log.warn("对话摘要失败(已忽略): {}", e.getMessage());
        }
    }

    private boolean containsIgnoreCase(String a, String b) {
        return a.toLowerCase().contains(b.toLowerCase());
    }

    private List<Extracted> parseExtracted(String resp) {
        if (resp == null) return List.of();
        String s = resp.trim();
        int start = s.indexOf('[');
        int end = s.lastIndexOf(']');
        if (start < 0 || end <= start) return List.of();
        s = s.substring(start, end + 1);
        try {
            return objectMapper.readValue(s, new TypeReference<List<Extracted>>() {});
        } catch (Exception e) {
            return List.of();
        }
    }

    /** LLM 返回的抽取项 */
    public static class Extracted {
        public String content;
        public Integer importance;
    }
}
