package com.example.ragkb.service;

import com.example.ragkb.exception.BusinessException;
import com.example.ragkb.model.entity.*;
import com.example.ragkb.model.enums.DocumentStatus;
import com.example.ragkb.repository.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 学习路径服务：
 * - 创建路径 / 从知识库一键生成有序节点（每篇文档一个 DOCUMENT 节点）；
 * - 列表与详情（节点 + 当前用户的节点进度）；
 * - 节点级进度更新（NOT_STARTED / IN_PROGRESS / COMPLETED），并聚合路径完成度。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class LearningPathService {

    private final LearningPathRepository pathRepository;
    private final LearningPathNodeRepository nodeRepository;
    private final LearningPathNodeProgressRepository progressRepository;
    private final KnowledgeBaseRepository knowledgeBaseRepository;
    private final DocumentRepository documentRepository;
    private final KnowledgeBaseService knowledgeBaseService;
    private final ObjectMapper objectMapper;

    // ═══════════════ 创建 ═══════════════

    @Transactional
    public LearningPath createPath(Long userId, String title, String description, Long knowledgeBaseId) {
        if (title == null || title.isBlank()) {
            throw new BusinessException("学习路径标题不能为空");
        }
        LearningPath path = LearningPath.builder()
                .userId(userId)
                .title(title.trim())
                .description(description)
                .knowledgeBaseId(knowledgeBaseId)
                .status("ACTIVE")
                .build();
        return pathRepository.save(path);
    }

    /**
     * 从知识库（含整棵子树）一键生成有序学习路线：每篇已入库文档生成一个 DOCUMENT 节点。
     */
    @Transactional
    public LearningPath generateFromKnowledgeBase(Long userId, Long knowledgeBaseId, String title) {
        if (knowledgeBaseId == null) {
            throw new BusinessException("生成学习路径需要指定知识库");
        }
        KnowledgeBase kb = knowledgeBaseRepository.findById(knowledgeBaseId)
                .orElseThrow(() -> new BusinessException("知识库不存在"));
        String pathTitle = (title != null && !title.isBlank())
                ? title.trim() : "学习路线 · " + kb.getName();
        LearningPath path = createPath(userId, pathTitle,
                "根据知识库《" + kb.getName() + "》自动生成的有序学习路线", knowledgeBaseId);

        List<Long> kbIds = knowledgeBaseService.getDescendantIds(knowledgeBaseId);
        List<Document> docs = documentRepository.findByKnowledgeBaseIdIn(kbIds);
        docs.sort(Comparator.comparing(Document::getId));

        int idx = 0;
        for (Document d : docs) {
            if (d.getStatus() != DocumentStatus.COMPLETED) continue; // 仅纳入已成功解析的文档
            Map<String, Object> ref = new LinkedHashMap<>();
            ref.put("documentId", d.getId());
            ref.put("title", d.getTitle());
            nodeRepository.save(LearningPathNode.builder()
                    .pathId(path.getId())
                    .title(d.getTitle())
                    .description("阅读并掌握文档《" + d.getTitle() + "》的核心内容")
                    .orderIndex(idx++)
                    .nodeType("DOCUMENT")
                    .refJson(writeJson(ref))
                    .build());
        }
        log.info("已从知识库 {} 生成学习路径 {}，共 {} 个节点", knowledgeBaseId, path.getId(), idx);
        return path;
    }

    // ═══════════════ 查询 ═══════════════

    public List<LearningPath> listPaths(Long userId) {
        return pathRepository.findByUserIdOrderByUpdatedAtDesc(userId);
    }

    public Map<String, Object> getPathDetail(Long pathId, Long userId) {
        LearningPath path = pathRepository.findById(pathId)
                .orElseThrow(() -> new BusinessException("学习路径不存在"));
        if (!path.getUserId().equals(userId)) {
            throw new BusinessException("无权访问该学习路径");
        }
        List<LearningPathNode> nodes = nodeRepository.findByPathIdOrderByOrderIndexAsc(pathId);
        List<Long> nodeIds = nodes.stream().map(LearningPathNode::getId).toList();
        Map<Long, LearningPathNodeProgress> progMap = nodeIds.isEmpty() ? Map.of()
                : progressRepository.findByNodeIdInAndUserId(nodeIds, userId).stream()
                .collect(Collectors.toMap(LearningPathNodeProgress::getNodeId, p -> p, (a, b) -> a));

        List<Map<String, Object>> nodeViews = nodes.stream().map(n -> {
            LearningPathNodeProgress pr = progMap.get(n.getId());
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", n.getId());
            m.put("title", n.getTitle());
            m.put("description", n.getDescription());
            m.put("orderIndex", n.getOrderIndex());
            m.put("nodeType", n.getNodeType());
            m.put("ref", parseJson(n.getRefJson()));
            m.put("status", pr != null ? pr.getStatus() : "NOT_STARTED");
            m.put("score", pr != null ? pr.getScore() : null);
            m.put("completedAt", pr != null ? pr.getCompletedAt() : null);
            return m;
        }).toList();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", path.getId());
        result.put("title", path.getTitle());
        result.put("description", path.getDescription());
        result.put("knowledgeBaseId", path.getKnowledgeBaseId());
        result.put("status", path.getStatus());
        result.put("createdAt", path.getCreatedAt());
        result.put("updatedAt", path.getUpdatedAt());
        result.put("nodes", nodeViews);
        result.put("progress", computeProgress(nodes, progMap));
        return result;
    }

    // ═══════════════ 进度 ═══════════════

    @Transactional
    public Map<String, Object> updateNodeProgress(Long userId, Long nodeId, String status, Integer score) {
        LearningPathNode node = nodeRepository.findById(nodeId)
                .orElseThrow(() -> new BusinessException("学习路径节点不存在"));
        LearningPath path = pathRepository.findById(node.getPathId())
                .orElseThrow(() -> new BusinessException("学习路径不存在"));
        if (!path.getUserId().equals(userId)) {
            throw new BusinessException("无权操作该学习路径");
        }
        List<String> valid = List.of("NOT_STARTED", "IN_PROGRESS", "COMPLETED");
        if (status == null || !valid.contains(status)) {
            throw new BusinessException("无效的节点状态：" + status);
        }
        LearningPathNodeProgress prog = progressRepository.findByNodeIdAndUserId(nodeId, userId)
                .orElseGet(() -> LearningPathNodeProgress.builder().nodeId(nodeId).userId(userId).build());
        prog.setStatus(status);
        prog.setScore(score);
        prog.setCompletedAt("COMPLETED".equals(status) ? LocalDateTime.now() : null);
        progressRepository.save(prog);
        return getPathDetail(path.getId(), userId);
    }

    // ═══════════════ 删除 ═══════════════

    @Transactional
    public void deletePath(Long userId, Long pathId) {
        LearningPath path = pathRepository.findById(pathId)
                .orElseThrow(() -> new BusinessException("学习路径不存在"));
        if (!path.getUserId().equals(userId)) {
            throw new BusinessException("无权删除该学习路径");
        }
        pathRepository.delete(path); // 节点与进度随 ON DELETE CASCADE 清理
        log.info("已删除学习路径 {}", pathId);
    }

    // ═══════════════ 内部 ═══════════════

    private Map<String, Object> computeProgress(List<LearningPathNode> nodes,
                                                 Map<Long, LearningPathNodeProgress> progMap) {
        int total = nodes.size();
        int completed = 0;
        int inProgress = 0;
        for (LearningPathNode n : nodes) {
            LearningPathNodeProgress p = progMap.get(n.getId());
            if (p == null) continue;
            if ("COMPLETED".equals(p.getStatus())) completed++;
            else if ("IN_PROGRESS".equals(p.getStatus())) inProgress++;
        }
        double percent = total == 0 ? 0.0 : (double) completed / total;
        String status = total == 0 ? "EMPTY"
                : (completed == total ? "COMPLETED" : (completed + inProgress > 0 ? "IN_PROGRESS" : "NOT_STARTED"));
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("total", total);
        m.put("completed", completed);
        m.put("inProgress", inProgress);
        m.put("percent", percent);
        m.put("status", status);
        return m;
    }

    private String writeJson(Object o) {
        try {
            return objectMapper.writeValueAsString(o);
        } catch (Exception e) {
            return "{}";
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseJson(String s) {
        try {
            if (s == null || s.isBlank()) return Map.of();
            return objectMapper.readValue(s, Map.class);
        } catch (Exception e) {
            return Map.of();
        }
    }
}
