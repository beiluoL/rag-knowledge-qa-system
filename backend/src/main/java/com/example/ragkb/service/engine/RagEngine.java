package com.example.ragkb.service.engine;

import com.example.ragkb.model.dto.ReferenceDTO;

import java.util.List;

/**
 * RAG 引擎抽象接口
 * 统一 Spring AI 和 LangChain4j 两种 RAG 管道的调用协议
 * 前端可通过 engine 参数选择使用哪种引擎
 */
public interface RagEngine {

    /**
     * 引擎名称标识
     */
    String getName();

    /**
     * 执行 RAG 检索 + Prompt 构建
     *
     * @param conversationId 会话 ID（用于获取历史对话）
     * @param question       用户原始问题
     * @param questionVector 已计算好的问题向量（避免重复 Embedding）
     * @return RAG 结果（系统提示 + 用户消息 + 引用列表）
     */
    RagResult prepare(Long conversationId, String question, String questionVector);

    /**
     * RAG 执行结果
     *
     * @param systemPrompt  系统提示词
     * @param userMessage   组装后的用户消息（含参考资料 + 历史 + 问题）
     * @param references    检索到的引用来源
     * @param rewrittenQuery 改写后的查询（可为 null，表示未改写）
     * @param searchDetails  搜索详情描述（供前端 RAG 可视化展示）
     */
    record RagResult(
            String systemPrompt,
            String userMessage,
            List<ReferenceDTO> references,
            String rewrittenQuery,
            String searchDetails
    ) {}
}
