package com.example.ragkb.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * 查询改写器：在检索前用 LLM 把用户的自然语言问题改写成
 * 更适合向量数据库语义检索的简洁查询，提升召回质量。
 * <p>
 * - 通过 app.rag.query-rewrite-enabled 开关控制（默认开启）；
 * - 任何异常均降级为返回原始问题，保证问答链路不中断。
 */
@Slf4j
@Service
public class QueryRewriter {

    private final AiFrameworkRouter aiProvider;

    @Value("${app.rag.query-rewrite-enabled:true}")
    private boolean enabled;

    public QueryRewriter(AiFrameworkRouter aiProvider) {
        this.aiProvider = aiProvider;
    }

    private static final String REWRITE_SYSTEM = """
            你是一个电商知识库检索查询优化器。
            请将用户的自然语言问题改写成更适合向量数据库语义检索的简洁查询语句，
            保留关键的商品名、型号、规格参数词，去除口语化表达和问候语。
            只输出改写后的查询本身，不要任何解释、前缀或多余标点。
            """;

    /**
     * 将用户问题改写为优化后的检索查询。
     * 若未启用或改写失败，返回原始问题（降级保证可用）。
     */
    public String rewrite(String question) {
        if (!enabled) {
            return question;
        }
        try {
            String rewritten = aiProvider.chat(REWRITE_SYSTEM, question).trim();
            // 防御：模型若输出了多余解释，仅取首个非空行
            if (rewritten.contains("\n")) {
                rewritten = rewritten.lines()
                        .filter(l -> !l.isBlank())
                        .findFirst()
                        .orElse(rewritten)
                        .trim();
            }
            log.info("查询改写: [{}] -> [{}]", question, rewritten);
            return rewritten.isBlank() ? question : rewritten;
        } catch (Exception e) {
            log.warn("查询改写失败，使用原问题: {}", e.getMessage());
            return question;
        }
    }
}
