package com.example.ragkb.service;

import com.example.ragkb.exception.BusinessException;
import com.example.ragkb.model.dto.ReferenceDTO;
import com.example.ragkb.model.entity.Document;
import com.example.ragkb.model.entity.KnowledgeBase;
import com.example.ragkb.repository.DocumentRepository;
import com.example.ragkb.repository.KnowledgeBaseRepository;
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
 * 个性化复习计划服务：基于知识库的文档清单与用户的学习目标，
 * 调用大模型生成按天递进的复习计划（每天聚焦主题 + 具体任务 + 关联资料）。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ReviewPlanService {

    private final RAGService ragService;
    private final KnowledgeBaseRepository knowledgeBaseRepository;
    private final DocumentRepository documentRepository;
    private final KnowledgeBaseService knowledgeBaseService;
    private final ObjectMapper objectMapper;
    private final AiFrameworkRouter aiProvider;

    /**
     * 生成个性化复习计划
     *
     * @param knowledgeBaseId 知识库 ID（决定计划范围）
     * @param goal            学习目标（如「两周内掌握 Spring Boot 核心」）
     * @param days            总天数
     * @param dailyMinutes    每天建议学习时长（分钟）
     * @return { knowledgeBase, goal, days, dailyMinutes, plan: List<dayMap> }
     */
    public Map<String, Object> generate(Long knowledgeBaseId, String goal, int days, int dailyMinutes) {
        if (knowledgeBaseId == null) throw new BusinessException("请选择知识库");
        KnowledgeBase kb = knowledgeBaseRepository.findById(knowledgeBaseId)
                .orElseThrow(() -> new BusinessException("知识库不存在"));
        if (days <= 0) days = 7;
        if (dailyMinutes <= 0) dailyMinutes = 30;
        if (goal == null || goal.isBlank()) goal = "系统掌握《" + kb.getName() + "》的核心内容";

        // 收集子树下的文档清单作为计划素材
        List<Long> kbIds = knowledgeBaseService.getDescendantIds(knowledgeBaseId);
        List<Document> docs = documentRepository.findByKnowledgeBaseIdIn(kbIds);
        StringBuilder docInfo = new StringBuilder();
        for (Document d : docs) {
            docInfo.append("- ").append(d.getTitle());
            if (d.getDescription() != null && !d.getDescription().isBlank()) {
                docInfo.append("（").append(d.getDescription()).append("）");
            }
            docInfo.append("\n");
        }

        // 与目标最相关的资料片段
        List<ReferenceDTO> refs = ragService.search(goal, knowledgeBaseId);

        String system = "你是一名专业的学习规划师。只输出一个 JSON 数组（不要包含 Markdown 代码块标记），"
                + "数组每个元素表示一天的安排，字段："
                + "{\"day\":整数,\"focus\":\"当日主题\",\"tasks\":[\"任务1\",\"任务2\"],"
                + "\"minutes\":建议时长(整数),\"topics\":[\"关联资料/文档标题\"]}。"
                + "计划需循序渐进、覆盖知识库主要内容，并与学习目标对齐。";

        StringBuilder user = new StringBuilder();
        user.append("知识库：").append(kb.getName()).append("\n");
        if (kb.getDescription() != null && !kb.getDescription().isBlank()) {
            user.append("简介：").append(kb.getDescription()).append("\n");
        }
        user.append("学习目标：").append(goal).append("\n");
        user.append("总天数：").append(days).append("，每天约 ").append(dailyMinutes).append(" 分钟\n");
        user.append("\n知识库包含文档：\n").append(docInfo);
        if (!refs.isEmpty()) {
            user.append("\n与学习目标最相关的资料片段：\n");
            for (int i = 0; i < refs.size(); i++) {
                user.append("[").append(i + 1).append("] ").append(refs.get(i).getDocumentTitle())
                    .append(": ").append(refs.get(i).getContentSnippet()).append("\n");
            }
        }
        user.append("\n请生成 ").append(days).append(" 天的个性化复习计划。");

        String raw = aiProvider.chat(system, user.toString());
        List<Map<String, Object>> plan = parsePlan(raw, days, dailyMinutes);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("knowledgeBase", kb.getName());
        result.put("goal", goal);
        result.put("days", days);
        result.put("dailyMinutes", dailyMinutes);
        result.put("docCount", docs.size());
        result.put("plan", plan);
        return result;
    }

    private List<Map<String, Object>> parsePlan(String raw, int days, int dailyMinutes) {
        try {
            String json = raw;
            int s = json.indexOf('[');
            int e = json.lastIndexOf(']');
            if (s < 0 || e <= s) throw new BusinessException("未找到 JSON 数组");
            json = json.substring(s, e + 1);

            List<Map<String, Object>> list = objectMapper.readValue(json,
                    new TypeReference<List<Map<String, Object>>>() {});

            List<Map<String, Object>> result = new ArrayList<>();
            int idx = 0;
            for (Map<String, Object> item : list) {
                Map<String, Object> day = new LinkedHashMap<>(item);
                day.putIfAbsent("day", ++idx);
                day.putIfAbsent("focus", "复习当日内容");
                if (!(day.get("tasks") instanceof List)) day.put("tasks", List.of("阅读并整理相关资料"));
                if (day.get("minutes") == null) day.put("minutes", dailyMinutes);
                if (!(day.get("topics") instanceof List)) day.put("topics", List.of());
                result.add(day);
            }
            return result;
        } catch (BusinessException be) {
            throw be;
        } catch (Exception ex) {
            log.error("解析复习计划失败", ex);
            throw new BusinessException("AI 返回格式异常，请重试");
        }
    }
}
