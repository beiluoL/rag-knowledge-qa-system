package com.example.ragkb.service;

import com.example.ragkb.model.dto.ReferenceDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 智能写作服务：基于指定知识库的检索内容，调用大模型撰写结构化的 Markdown 文章
 * （学习总结 / 报告 / 文章）。所有内容均锚定知识库资料，附引用来源，避免编造。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WritingService {

    private final RAGService ragService;
    private final AiFrameworkRouter aiProvider;

    /**
     * 撰写文章
     *
     * @param topic          文章主题
     * @param knowledgeBaseId 知识库 ID（null 表示全库）
     * @param outline        可选大纲要求
     * @param style          可选风格（如：正式/通俗/教学）
     * @param length         建议篇幅（字数，默认 800）
     * @return { article, references }
     */
    public Map<String, Object> compose(String topic, Long knowledgeBaseId,
                                       String outline, String style, int length) {
        if (topic == null || topic.isBlank()) {
            throw new com.example.ragkb.exception.BusinessException("文章主题不能为空");
        }
        int wordCount = length <= 0 ? 800 : Math.min(length, 3000);

        // 1. 检索相关资料
        List<ReferenceDTO> refs = ragService.search(topic, knowledgeBaseId);

        // 2. 组装参考资料上下文
        StringBuilder ctx = new StringBuilder();
        if (refs.isEmpty()) {
            ctx.append("（未检索到相关文档，请基于通用知识撰写，并明确说明）\n");
        } else {
            for (int i = 0; i < refs.size(); i++) {
                ctx.append("[").append(i + 1).append("] ").append(refs.get(i).getDocumentTitle()).append("\n")
                    .append(refs.get(i).getContentSnippet()).append("\n\n");
            }
        }

        // 3. 提示词
        String system = "你是一名优秀的写作助手，善于基于给定资料撰写结构清晰、严谨专业的中文文章。"
                + "请使用 Markdown 格式输出，包含一级/二级标题、要点列表或表格；文末以「## 参考资料」列出引用来源编号；"
                + "不要编造资料中不存在的具体数据或结论。";

        StringBuilder user = new StringBuilder();
        user.append("主题：").append(topic).append("\n");
        if (style != null && !style.isBlank()) user.append("风格：").append(style).append("\n");
        user.append("篇幅：约 ").append(wordCount).append(" 字\n");
        if (outline != null && !outline.isBlank()) user.append("大纲要求：").append(outline).append("\n");
        user.append("\n请基于以下【参考资料】撰写文章：\n").append(ctx);

        // 4. 调用大模型
        String article = aiProvider.chat(system, user.toString());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("article", article);
        result.put("references", refs);
        result.put("topic", topic);
        return result;
    }
}
