package com.example.ragkb.service;

import com.example.ragkb.exception.BusinessException;
import com.example.ragkb.model.dto.ReferenceDTO;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 智能出题服务：基于指定知识库的检索内容，调用大模型生成练习题。
 * 支持两种题型：
 *  - mc：选择题（4 个选项 + 正确答案 + 解析 + 来源）
 *  - qa：问答题（问题 + 答案 + 解析 + 来源）
 * 生成的题目均严格基于知识库资料，避免幻觉。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class QuizService {

    private final RAGService ragService;
    private final ObjectMapper objectMapper;
    private final AiFrameworkRouter aiProvider;

    /**
     * 生成练习题
     *
     * @param knowledgeBaseId 知识库 ID（null 表示全库）
     * @param type            题型：mc / qa
     * @param count           题目数量（1~20）
     * @return 题目列表（每个元素为 Map）
     */
    public List<Map<String, Object>> generate(Long knowledgeBaseId, String type, int count) {
        if (count <= 0) count = 5;
        if (count > 20) count = 20;
        final boolean isMc = "mc".equalsIgnoreCase(type);

        // 1. 检索知识库相关内容作为出题素材
        List<ReferenceDTO> refs = ragService.search("请基于本知识库内容生成 " + count + " 道练习题", knowledgeBaseId);
        if (refs.isEmpty()) {
            throw new BusinessException("该知识库暂无可用内容，无法出题，请先上传文档");
        }

        StringBuilder ctx = new StringBuilder();
        for (int i = 0; i < refs.size(); i++) {
            ctx.append("[").append(i + 1).append("] 来源:").append(refs.get(i).getDocumentTitle()).append("\n")
                .append(refs.get(i).getContentSnippet()).append("\n\n");
        }

        // 2. 构造提示词，要求纯 JSON 数组输出
        String system = isMc
                ? "你是一名严谨的题库出题专家。只输出一个 JSON 数组，不要包含任何额外说明文字或 Markdown 代码块标记。"
                  + "数组每个元素为对象，字段："
                  + "{\"question\":\"题目\",\"options\":[\"A\",\"B\",\"C\",\"D\"],"
                  + "\"answer\":\"正确选项的完整文本，必须与 options 中某一项完全一致\","
                  + "\"explanation\":\"简短解析\",\"source\":\"来源文档标题\"}。"
                : "你是一名严谨的题库出题专家。只输出一个 JSON 数组，不要包含任何额外说明文字或 Markdown 代码块标记。"
                  + "数组每个元素为对象，字段："
                  + "{\"question\":\"问题\",\"answer\":\"答案\",\"explanation\":\"解析\",\"source\":\"来源文档标题\"}。";

        String user = "请基于以下【参考资料】生成 " + count + " 道"
                + (isMc ? "选择题" : "问答题") + "，覆盖不同知识点，答案必须严格来自资料，不要编造。\n\n"
                + "【参考资料】\n" + ctx;

        // 3. 调用大模型
        String raw = aiProvider.chat(system, user);

        // 4. 解析 JSON 数组（容错：截取首个 [ ... ]）
        List<Map<String, Object>> questions = parseArray(raw, isMc);
        if (questions.isEmpty()) {
            throw new BusinessException("AI 返回格式异常，请重试");
        }
        return questions;
    }

    private List<Map<String, Object>> parseArray(String raw, boolean isMc) {
        try {
            String json = raw;
            int s = json.indexOf('[');
            int e = json.lastIndexOf(']');
            if (s < 0 || e <= s) throw new BusinessException("未找到 JSON 数组");
            json = json.substring(s, e + 1);

            List<Map<String, Object>> list = objectMapper.readValue(json,
                    new TypeReference<List<Map<String, Object>>>() {});

            // 归一化：补充默认字段，确保前端安全渲染
            List<Map<String, Object>> result = new ArrayList<>();
            int idx = 0;
            for (Map<String, Object> item : list) {
                Map<String, Object> q = new LinkedHashMap<>(item);
                q.putIfAbsent("question", "（题目缺失）");
                if (isMc) {
                    Object options = q.get("options");
                    if (!(options instanceof List) || ((List<?>) options).isEmpty()) {
                        q.put("options", List.of("选项A", "选项B", "选项C", "选项D"));
                    }
                    q.putIfAbsent("answer", "");
                    q.putIfAbsent("explanation", "");
                } else {
                    q.putIfAbsent("answer", "");
                    q.putIfAbsent("explanation", "");
                }
                q.putIfAbsent("source", "");
                q.put("index", ++idx);
                result.add(q);
            }
            return result;
        } catch (BusinessException be) {
            throw be;
        } catch (Exception ex) {
            log.error("解析出题结果失败", ex);
            throw new BusinessException("AI 返回格式异常，请重试");
        }
    }
}
