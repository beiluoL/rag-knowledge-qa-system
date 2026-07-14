package com.example.ragkb.service;

import com.example.ragkb.model.dto.ReferenceDTO;
import com.example.ragkb.model.entity.KnowledgeBase;
import com.example.ragkb.model.entity.Message;
import com.example.ragkb.repository.ChunkEmbeddingRepository;
import com.example.ragkb.repository.KnowledgeBaseRepository;
import com.example.ragkb.repository.MessageRepository;
import com.example.ragkb.service.KnowledgeBaseService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * RAG 核心编排服务
 * 负责：问题向量化 → 检索（混合：向量语义 + 关键词全文，RRF 融合）→ Prompt 拼接 → LLM 生成
 * 根据 ai-mode 自动切换离线/在线模型；根据 ai-framework 选择调用库（Spring AI / LangChain4j）。
 */
@Service
@Slf4j
public class RAGService {

    private final EmbeddingService embeddingService;
    private final ChunkEmbeddingRepository embeddingRepository;
    private final AiFrameworkRouter aiProvider;
    private final MessageRepository messageRepository;
    private final KnowledgeBaseRepository knowledgeBaseRepository;
    private final KnowledgeBaseService knowledgeBaseService;

    public RAGService(EmbeddingService embeddingService,
                       ChunkEmbeddingRepository embeddingRepository,
                       AiFrameworkRouter aiProvider,
                       MessageRepository messageRepository,
                       KnowledgeBaseRepository knowledgeBaseRepository,
                       KnowledgeBaseService knowledgeBaseService) {
        this.embeddingService = embeddingService;
        this.embeddingRepository = embeddingRepository;
        this.aiProvider = aiProvider;
        this.messageRepository = messageRepository;
        this.knowledgeBaseRepository = knowledgeBaseRepository;
        this.knowledgeBaseService = knowledgeBaseService;
    }

    @Value("${app.rag.top-k}")
    private int topK;

    @Value("${app.rag.similarity-threshold}")
    private double similarityThreshold;

    @Value("${app.rag.max-history-rounds}")
    private int maxHistoryRounds;

    @Value("${app.rag.hybrid-enabled:true}")
    private boolean hybridEnabled;

    @Value("${app.rag.rrf-k:60}")
    private int rrfK;

    public int getTopK() { return topK; }
    public int getMaxHistoryRounds() { return maxHistoryRounds; }
    public String getCurrentMode() { return aiProvider.getMode(); }
    public String getCurrentFramework() { return aiProvider.getFramework(); }

    /**
     * 公开检索入口：供智能出题 / 智能写作 / 复习计划等进阶功能复用。
     * 按知识库（含整棵子树）做混合检索，返回 Top-K 引用。
     *
     * @param question         检索问题（会被向量化）
     * @param knowledgeBaseId  知识库 ID（null 表示全部知识库）
     * @return 检索到的引用列表（已按 RRF 融合排序）
     */
    public List<ReferenceDTO> search(String question, Long knowledgeBaseId) {
        String questionVector = embeddingService.embed(question);
        List<Long> kbIds = (knowledgeBaseId != null)
                ? knowledgeBaseService.getDescendantIds(knowledgeBaseId) : null;
        return retrieve(question, questionVector, kbIds);
    }

    /**
     * 动态构建系统提示词：按知识库领域注入，去掉硬编码的"电商"限定。
     */
    private String buildSystemPrompt(Long knowledgeBaseId) {
        String domain = "通用知识库";
        String extra = "";
        if (knowledgeBaseId != null) {
            KnowledgeBase kb = knowledgeBaseRepository.findById(knowledgeBaseId).orElse(null);
            if (kb != null) {
                domain = kb.getName();
                if (kb.getDescription() != null && !kb.getDescription().isBlank()) {
                    extra = "\n知识库简介：" + kb.getDescription();
                }
            }
        }
        return """
                你是%s的知识库智能助手，专门基于下方【参考资料】回答用户的问题。

                回答规则：
                1. 请严格基于【参考资料】中的内容来回答，不要编造信息
                2. 引用资料时使用 [编号] 标注来源，例如 [1]、[2]
                3. 回答要准确、简洁、专业、易懂
                4. 如果参考资料不足以回答，请明确告知"参考资料中未找到相关信息"，并建议用户补充相关资料或联系管理员
                5. 如果用户的问题与资料无关，请礼貌引导其回到知识库咨询
                """.formatted(domain) + extra;
    }

    /**
     * 构建完整的 RAG Prompt（自动向量化问题）
     *
     * @param conversationId 会话 ID（用于获取历史对话）
     * @param question       用户问题
     * @return Prompt 对象和检索到的引用
     */
    public RAGContext preparePrompt(Long conversationId, String question) {
        return preparePrompt(conversationId, question, null);
    }

    public RAGContext preparePrompt(Long conversationId, String question, Long knowledgeBaseId) {
        String questionVector = embeddingService.embed(question);
        return preparePrompt(conversationId, question, knowledgeBaseId, questionVector);
    }

    /**
     * 构建完整的 RAG Prompt（使用已计算好的向量，避免重复向量化）
     * <p>检索阶段采用混合检索：向量语义搜索 Top-K*3 + 关键词全文搜索 Top-K*3，
     * 再用 RRF（Reciprocal Rank Fusion）融合排序，取最终 Top-K。</p>
     *
     * @param conversationId 会话 ID（用于获取历史对话）
     * @param question       用户问题（原始文本，用于关键词检索 + 拼入 Prompt）
     * @param questionVector 已计算好的问题向量字符串（来自查询改写后的检索查询）
     * @return Prompt 对象和检索到的引用
     */
    public RAGContext preparePrompt(Long conversationId, String question,
                                     Long knowledgeBaseId, String questionVector) {
        // 选中的知识库若是父节点，检索范围扩展到其全部子孙库（子树检索）
        List<Long> kbIds = (knowledgeBaseId != null)
                ? knowledgeBaseService.getDescendantIds(knowledgeBaseId) : null;
        // 1. 混合检索（使用传入的向量，避免重复调用 embeddingService）
        List<ReferenceDTO> references = retrieve(question, questionVector, kbIds);
        log.info("混合检索返回 {} 条结果（混合检索: {}，知识库: {}），阈值: {}",
                references.size(), hybridEnabled, knowledgeBaseId, similarityThreshold);

        // 2. 构建参考资料上下文
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
            contextBuilder.append("【参考资料】\n（暂无相关文档信息）\n");
        }

        // 3. 获取历史对话
        String history = buildHistory(conversationId);

        // 4. 组装完整用户消息：参考资料 + 历史对话 + 用户问题
        String userContent = contextBuilder.toString() + "\n" + history + "\n" + "【用户问题】\n" + question;

        return new RAGContext(buildSystemPrompt(knowledgeBaseId), userContent, references);
    }

    /**
     * 检索：向量语义搜索 + 关键词全文搜索，RRF 融合
     */
    private List<ReferenceDTO> retrieve(String keyword, String questionVector, List<Long> kbIds) {
        if (!hybridEnabled) {
            return embeddingRepository.semanticSearch(questionVector, topK, similarityThreshold, kbIds);
        }
        int candidate = topK * 3; // 召回候选，融合后取 Top-K
        List<ReferenceDTO> semantic = embeddingRepository.semanticSearch(questionVector, candidate, similarityThreshold, kbIds);
        List<ReferenceDTO> keywordResults = embeddingRepository.keywordSearch(keyword, candidate, kbIds);
        return rrfFuse(semantic, keywordResults, topK);
    }

    /**
     * RRF（Reciprocal Rank Fusion）融合两种召回结果。
     * 融合分 = Σ 1/(k + rank)，与具体相似度数值无关，避免跨渠道分数不可比。
     */
    private List<ReferenceDTO> rrfFuse(List<ReferenceDTO> semantic,
                                       List<ReferenceDTO> keyword, int topK) {
        Map<Long, Double> fused = new HashMap<>();
        Map<Long, ReferenceDTO> best = new LinkedHashMap<>(); // 保留首个出现的引用（语义优先）
        Map<Long, Double> semanticScore = new HashMap<>();

        accumulate(semantic, fused, best, semanticScore);
        accumulate(keyword, fused, best, semanticScore);

        if (fused.isEmpty()) return List.of();

        double maxFused = fused.values().stream().mapToDouble(Double::doubleValue).max().orElse(1.0);

        List<Map.Entry<Long, Double>> sorted = new ArrayList<>(fused.entrySet());
        sorted.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));

        List<ReferenceDTO> result = new ArrayList<>();
        for (Map.Entry<Long, Double> e : sorted) {
            if (result.size() >= topK) break;
            ReferenceDTO src = best.get(e.getKey());
            // 展示分：优先用语义余弦相似度（0~1），否则用归一化融合分
            double display = semanticScore.containsKey(e.getKey())
                    ? semanticScore.get(e.getKey())
                    : (e.getValue() / maxFused);
            result.add(ReferenceDTO.builder()
                    .chunkId(src.getChunkId())
                    .documentId(src.getDocumentId())
                    .documentTitle(src.getDocumentTitle())
                    .contentSnippet(src.getContentSnippet())
                    .score(display)
                    .build());
        }
        return result;
    }

    private void accumulate(List<ReferenceDTO> list, Map<Long, Double> fused,
                            Map<Long, ReferenceDTO> best, Map<Long, Double> semanticScore) {
        for (int i = 0; i < list.size(); i++) {
            ReferenceDTO r = list.get(i);
            double s = 1.0 / (rrfK + i + 1);
            fused.merge(r.getChunkId(), s, Double::sum);
            best.putIfAbsent(r.getChunkId(), r); // 语义列表先入，作为最佳引用来源
            if (!semanticScore.containsKey(r.getChunkId())) {
                semanticScore.put(r.getChunkId(), r.getScore());
            }
        }
    }

    /**
     * 调用 AI 生成回答（按当前框架 + 模式）
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
