package com.example.ragkb.service;

import com.example.ragkb.model.dto.ReferenceDTO;
import com.example.ragkb.model.entity.Message;
import com.example.ragkb.repository.ChunkEmbeddingRepository;
import com.example.ragkb.repository.MessageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * RAG 核心编排服务
 * 负责：问题向量化 → 语义搜索 → Prompt 拼接 → LLM 生成
 * 根据 ai-mode 自动切换离线/在线模型
 */
@Service
@Slf4j
public class RAGService {

    private final EmbeddingService embeddingService;
    private final ChunkEmbeddingRepository embeddingRepository;
    private final DynamicAiProvider aiProvider;
    private final MessageRepository messageRepository;

    public RAGService(EmbeddingService embeddingService,
                       ChunkEmbeddingRepository embeddingRepository,
                       DynamicAiProvider aiProvider,
                       MessageRepository messageRepository) {
        this.embeddingService = embeddingService;
        this.embeddingRepository = embeddingRepository;
        this.aiProvider = aiProvider;
        this.messageRepository = messageRepository;
    }

    @Value("${app.rag.top-k}")
    private int topK;

    @Value("${app.rag.similarity-threshold}")
    private double similarityThreshold;

    @Value("${app.rag.max-history-rounds}")
    private int maxHistoryRounds;

    public int getTopK() { return topK; }
    public int getMaxHistoryRounds() { return maxHistoryRounds; }
    public String getCurrentMode() { return aiProvider.getMode(); }

    private static final String SYSTEM_PROMPT = """
            你是电商知识库助手，专门回答关于平台上商品的问题。

            回答规则：
            1. 请严格基于下方【参考资料】中的内容来回答问题，不要编造信息
            2. 回答中引用资料时，使用 [编号] 标注来源，例如 [1]、[2]
            3. 回答要准确、简洁、专业
            4. 如果参考资料不足以回答用户问题，请明确告知"参考资料中未找到相关信息"，并建议用户查阅商品详情页或联系客服
            5. 如果用户问的是与商品、购物无关的问题，请礼貌引导其回到商品咨询
            """;

    /**
     * 构建完整的 RAG Prompt
     *
     * @param conversationId 会话 ID（用于获取历史对话）
     * @param question       用户问题
     * @return Prompt 对象和检索到的引用
     */
    public RAGContext preparePrompt(Long conversationId, String question) {
        // 1. 问题向量化
        String questionVector = embeddingService.embed(question);

        // 2. 语义搜索
        List<ReferenceDTO> references = embeddingRepository.semanticSearch(
                questionVector, topK, similarityThreshold);
        log.info("语义搜索返回 {} 条结果，阈值: {}", references.size(), similarityThreshold);

        // 3. 构建上下文
        StringBuilder contextBuilder = new StringBuilder();
        if (!references.isEmpty()) {
            contextBuilder.append("【参考资料】\n");
            for (int i = 0; i < references.size(); i++) {
                ReferenceDTO ref = references.get(i);
                contextBuilder.append("[").append(i + 1).append("] ")
                        .append("来源: ").append(ref.getDocumentTitle()).append("\n")
                        .append(ref.getContentSnippet()).append("\n\n");
            }
        } else {
            contextBuilder.append("【参考资料】\n（暂无相关商品信息）\n");
        }

        // 4. 获取历史对话
        String history = buildHistory(conversationId);

        // 5. 组装消息
        String userContent = contextBuilder.toString() + "\n" + history + "\n" + "【用户问题】\n" + question;

        return new RAGContext(SYSTEM_PROMPT, userContent, references);
    }

    /**
     * 调用 AI 生成回答（离线走 Ollama，在线走 DashScope）
     */
    public String generateAnswer(String systemPrompt, String userMessage) {
        return aiProvider.chat(systemPrompt, userMessage);
    }

    /**
     * 构建历史对话上下文
     */
    private String buildHistory(Long conversationId) {
        if (conversationId == null) return "";

        List<Message> recentMessages = messageRepository
                .findByConversationIdOrderByCreatedAtDesc(conversationId);

        // 取最近 N 轮对话
        int maxMessages = maxHistoryRounds * 2; // 每轮一问一答
        List<Message> historyMessages = new ArrayList<>(
                recentMessages.stream().limit(maxMessages).toList());
        Collections.reverse(historyMessages);

        if (historyMessages.isEmpty()) return "";

        StringBuilder sb = new StringBuilder();
        sb.append("【历史对话】\n");
        for (Message msg : historyMessages) {
            if ("USER".equals(msg.getRole())) {
                sb.append("用户: ").append(msg.getContent()).append("\n");
            } else {
                // 只取回答的前 200 字作为历史
                String brief = msg.getContent().length() > 200
                        ? msg.getContent().substring(0, 200) + "..."
                        : msg.getContent();
                sb.append("助手: ").append(brief).append("\n");
            }
        }
        sb.append("\n");
        return sb.toString();
    }

    /**
     * RAG 上下文（系统提示 + 用户消息 + 引用）
     */
    public record RAGContext(String systemPrompt, String userMessage,
                              List<ReferenceDTO> references) {}
}
