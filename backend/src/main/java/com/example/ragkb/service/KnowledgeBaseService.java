package com.example.ragkb.service;

import com.example.ragkb.exception.BusinessException;
import com.example.ragkb.model.entity.Document;
import com.example.ragkb.model.entity.KbCategory;
import com.example.ragkb.model.entity.KnowledgeBase;
import com.example.ragkb.repository.DocumentRepository;
import com.example.ragkb.repository.KbCategoryRepository;
import com.example.ragkb.repository.KnowledgeBaseRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 知识库服务：泛化多领域知识库，支持树状结构（parent_id）与动态分类（kb_categories）。
 * RAG 检索按 knowledge_base_id 隔离，选父库时自动包含其全部子孙库（子树检索）。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class KnowledgeBaseService {

    private final KnowledgeBaseRepository knowledgeBaseRepository;
    private final DocumentRepository documentRepository;
    private final KbCategoryRepository kbCategoryRepository;

    /** 树节点（含子节点），用于前端树形选择器 / 管理页 */
    public record KbTreeItem(Long id, String name, String description, Long parentId,
                             Long categoryId, String categoryName, boolean isSystem,
                             int sortOrder, List<KbTreeItem> children) {}

    public List<KnowledgeBase> listAll() {
        return knowledgeBaseRepository.findAllByOrderBySortOrderAscCreatedAtDesc();
    }

    /** 返回树形结构（顶级为 parentId = null 的节点，递归挂载子节点） */
    public List<KbTreeItem> getTree() {
        List<KnowledgeBase> all = listAll();
        Map<Long, String> catNames = kbCategoryRepository.findAll().stream()
                .collect(Collectors.toMap(KbCategory::getId, KbCategory::getName, (a, b) -> a));
        Map<Long, List<KnowledgeBase>> childrenMap = all.stream()
                .filter(kb -> kb.getParentId() != null)
                .collect(Collectors.groupingBy(KnowledgeBase::getParentId));
        return all.stream()
                .filter(kb -> kb.getParentId() == null)
                .sorted(Comparator.comparingInt(kb -> kb.getSortOrder() == null ? 0 : kb.getSortOrder()))
                .map(kb -> toNode(kb, childrenMap, catNames))
                .collect(Collectors.toList());
    }

    private KbTreeItem toNode(KnowledgeBase kb, Map<Long, List<KnowledgeBase>> childrenMap,
                              Map<Long, String> catNames) {
        List<KbTreeItem> children = Optional.ofNullable(childrenMap.get(kb.getId()))
                .orElse(List.of()).stream()
                .sorted(Comparator.comparingInt(k -> k.getSortOrder() == null ? 0 : k.getSortOrder()))
                .map(c -> toNode(c, childrenMap, catNames))
                .collect(Collectors.toList());
        String catName = kb.getCategoryId() != null ? catNames.get(kb.getCategoryId()) : null;
        return new KbTreeItem(kb.getId(), kb.getName(), kb.getDescription(), kb.getParentId(),
                kb.getCategoryId(), catName, Boolean.TRUE.equals(kb.getIsSystem()),
                kb.getSortOrder() == null ? 0 : kb.getSortOrder(), children);
    }

    public KnowledgeBase getById(Long id) {
        return knowledgeBaseRepository.findById(id)
                .orElseThrow(() -> new BusinessException("知识库不存在"));
    }

    @Transactional
    public KnowledgeBase create(String name, String description, Long categoryId, Long parentId,
                                String tags, Long ownerId, boolean isSystem) {
        if (name == null || name.isBlank()) throw new BusinessException("知识库名称不能为空");
        Long resolvedCategory = categoryId;
        if (parentId != null) {
            KnowledgeBase parent = getById(parentId);
            if (resolvedCategory == null) resolvedCategory = parent.getCategoryId();
        }
        KnowledgeBase kb = KnowledgeBase.builder()
                .name(name).description(description).categoryId(resolvedCategory)
                .parentId(parentId).tags(tags).ownerId(ownerId).isSystem(isSystem)
                .sortOrder(0).build();
        return knowledgeBaseRepository.save(kb);
    }

    @Transactional
    public KnowledgeBase update(Long id, String name, String description, Long categoryId,
                                Long parentId, String tags) {
        KnowledgeBase kb = getById(id);
        if (name != null && !name.isBlank()) kb.setName(name);
        if (description != null) kb.setDescription(description);
        if (categoryId != null) kb.setCategoryId(categoryId);
        if (parentId != null) {
            if (parentId.equals(id)) throw new BusinessException("不能将知识库设为自身的父级");
            kb.setParentId(parentId);
        } else {
            kb.setParentId(null);
        }
        if (tags != null) kb.setTags(tags);
        return knowledgeBaseRepository.save(kb);
    }

    @Transactional
    public void delete(Long id) {
        KnowledgeBase kb = getById(id);
        if (Boolean.TRUE.equals(kb.getIsSystem())) {
            throw new BusinessException("系统预置知识库不可删除");
        }
        // 清理自身 + 全部后代文档归属，再删除（外键 ON DELETE CASCADE 会同时移除子节点行）
        for (Long kid : getDescendantIds(id)) documentRepository.clearKnowledgeBase(kid);
        knowledgeBaseRepository.deleteById(id);
    }

    /** 返回某节点自身 + 所有后代节点 ID（用于 RAG 子树检索与删除清理） */
    public List<Long> getDescendantIds(Long rootId) {
        List<Long> result = new ArrayList<>();
        Deque<Long> queue = new ArrayDeque<>();
        queue.add(rootId);
        Map<Long, List<Long>> childIndex = listAll().stream()
                .filter(kb -> kb.getParentId() != null)
                .collect(Collectors.groupingBy(KnowledgeBase::getParentId,
                        Collectors.mapping(KnowledgeBase::getId, Collectors.toList())));
        while (!queue.isEmpty()) {
            Long cur = queue.poll();
            result.add(cur);
            for (Long child : childIndex.getOrDefault(cur, List.of())) queue.add(child);
        }
        return result;
    }

    public Map<String, Object> stats(Long id) {
        long docCount = documentRepository.countByKnowledgeBaseId(id);
        return Map.of("documentCount", docCount);
    }
}
