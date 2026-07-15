package com.example.ragkb.service;

import com.example.ragkb.exception.BusinessException;
import com.example.ragkb.model.dto.KnowledgeCardRequest;
import com.example.ragkb.model.entity.Chunk;
import com.example.ragkb.model.entity.Document;
import com.example.ragkb.model.entity.KnowledgeCard;
import com.example.ragkb.repository.ChunkRepository;
import com.example.ragkb.repository.DocumentRepository;
import com.example.ragkb.repository.KnowledgeCardRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class KnowledgeCardService {

    private final KnowledgeCardRepository cardRepository;
    private final DynamicAiProvider aiProvider;
    private final KnowledgeBaseService knowledgeBaseService;
    private final DocumentRepository documentRepository;
    private final ChunkRepository chunkRepository;
    private final ObjectMapper objectMapper;

    /**
     * 列表查询：支持按分类筛选 + 关键词检索 + 知识库筛选
     */
    public List<KnowledgeCard> listCards(Long userId, String category, String keyword, Long knowledgeBaseId) {
        List<KnowledgeCard> base;
        if (knowledgeBaseId != null) {
            List<Long> kbIds = knowledgeBaseService.getDescendantIds(knowledgeBaseId);
            base = cardRepository.findByKnowledgeBaseIdIn(kbIds);
        } else if (StringUtils.hasText(keyword)) {
            base = cardRepository.searchByKeyword(userId, keyword.trim());
        } else if (StringUtils.hasText(category)) {
            base = cardRepository.findByUserIdAndCategoryOrderByUpdatedAtDesc(userId, category);
        } else {
            base = cardRepository.findByUserIdOrderByUpdatedAtDesc(userId);
        }
        // 在 KB 筛选基础上叠加关键词/分类
        if (knowledgeBaseId != null && StringUtils.hasText(keyword)) {
            String kw = keyword.trim().toLowerCase();
            base = base.stream().filter(c ->
                (c.getTitle() != null && c.getTitle().toLowerCase().contains(kw)) ||
                (c.getFront() != null && c.getFront().toLowerCase().contains(kw)) ||
                c.getBack().toLowerCase().contains(kw) ||
                (c.getTags() != null && c.getTags().toLowerCase().contains(kw))
            ).collect(Collectors.toList());
        }
        if (knowledgeBaseId != null && StringUtils.hasText(category)) {
            base = base.stream().filter(c -> category.equals(c.getCategory())).collect(Collectors.toList());
        }
        return base;
    }

    public List<String> listCategories(Long userId) {
        return cardRepository.findByUserIdOrderByUpdatedAtDesc(userId).stream()
                .map(KnowledgeCard::getCategory)
                .filter(c -> c != null && !c.isBlank())
                .distinct()
                .toList();
    }

    public KnowledgeCard getCard(Long userId, Long cardId) {
        return cardRepository.findById(cardId)
                .filter(c -> c.getUserId().equals(userId))
                .orElseThrow(() -> new BusinessException("卡片不存在或无权访问"));
    }

    public KnowledgeCard createCard(Long userId, KnowledgeCardRequest req) {
        KnowledgeCard card = KnowledgeCard.builder()
                .userId(userId)
                .title(req.getTitle().trim())
                .front(blankToNull(req.getFront()))
                .back(req.getBack().trim())
                .category(blankToNull(req.getCategory()))
                .tags(normalizeTags(req.getTags()))
                .source("MANUAL")
                .build();
        return cardRepository.save(card);
    }

    public KnowledgeCard updateCard(Long userId, Long cardId, KnowledgeCardRequest req) {
        KnowledgeCard card = getCard(userId, cardId);
        card.setTitle(req.getTitle().trim());
        card.setFront(blankToNull(req.getFront()));
        card.setBack(req.getBack().trim());
        card.setCategory(blankToNull(req.getCategory()));
        card.setTags(normalizeTags(req.getTags()));
        return cardRepository.save(card);
    }

    public void deleteCard(Long userId, Long cardId) {
        KnowledgeCard card = getCard(userId, cardId);
        cardRepository.delete(card);
    }

    @Transactional
    public void batchDelete(Long userId, List<Long> cardIds) {
        List<KnowledgeCard> cards = cardRepository.findAllById(cardIds);
        for (KnowledgeCard c : cards) {
            if (!c.getUserId().equals(userId)) throw new BusinessException("无权操作卡片 " + c.getId());
        }
        cardRepository.deleteAll(cards);
    }

    @Transactional
    public void batchMove(Long userId, List<Long> cardIds, Long knowledgeBaseId) {
        List<KnowledgeCard> cards = cardRepository.findAllById(cardIds);
        for (KnowledgeCard c : cards) {
            if (!c.getUserId().equals(userId)) throw new BusinessException("无权操作卡片 " + c.getId());
            c.setKnowledgeBaseId(knowledgeBaseId);
        }
        cardRepository.saveAll(cards);
    }

    /**
     * AI 一键生成：根据主题生成 N 张卡片并落库（source=AI）
     */
    public List<KnowledgeCard> generateFromTopic(Long userId, String topic, Integer count, String category) {
        if (!StringUtils.hasText(topic)) {
            throw new BusinessException("请填写生成主题");
        }
        int n = (count == null || count < 1) ? 5 : Math.min(count, 20);

        String systemPrompt = """
                你是一个专业知识卡片生成助手。根据用户给出的主题，生成若干张高质量知识卡片。
                每张卡片包含四个字段：
                - title：卡片标题（简洁概括）
                - front：正面内容，可以是问题、术语或核心概念（若不适合可填空字符串）
                - back：背面内容，答案或详细解析（务必充实、准确）
                - tags：标签数组，3 个以内的关键词
                只输出一个 JSON 数组，不要包含任何额外说明文字、不要使用 Markdown 代码块标记。
                示例格式：
                [{"title":"...","front":"...","back":"...","tags":["...","..."]}]
                """;

        String userMessage = String.format(
                "主题：%s。请生成 %d 张卡片，覆盖该主题的核心知识点，由浅入深。",
                topic.trim(), n);

        String raw = aiProvider.chat(systemPrompt, userMessage);
        List<Map<String, Object>> parsed = parseCardsJson(raw);

        List<KnowledgeCard> saved = new ArrayList<>();
        String cat = blankToNull(category);
        for (Map<String, Object> item : parsed) {
            String title = asText(item.get("title"));
            String back = asText(item.get("back"));
            if (title.isBlank() || back.isBlank()) continue; // 跳过残缺项
            String front = asText(item.get("front"));
            String tags = parseTags(item.get("tags"));
            KnowledgeCard card = KnowledgeCard.builder()
                    .userId(userId)
                    .title(title.length() > 300 ? title.substring(0, 300) : title)
                    .front(blankToNull(front))
                    .back(back)
                    .category(cat)
                    .tags(tags)
                    .source("AI")
                    .build();
            saved.add(cardRepository.save(card));
        }
        if (saved.isEmpty()) {
            throw new BusinessException("AI 未返回有效卡片，请换个主题重试");
        }
        log.info("AI 生成知识卡片 {} 张，用户 {}", saved.size(), userId);
        return saved;
    }

    /**
     * AI 从知识库文档抽取卡片：读取 KB 子树文档的 chunks 拼接成文本，调 AI 生成卡片并落库
     */
    public List<KnowledgeCard> extractFromKnowledgeBase(Long userId, Long knowledgeBaseId, Integer count, String category) {
        List<Long> kbIds = knowledgeBaseService.getDescendantIds(knowledgeBaseId);
        List<Document> docs = documentRepository.findByKnowledgeBaseIdIn(kbIds);
        if (docs.isEmpty()) throw new BusinessException("该知识库下没有文档，请先上传文档");

        // 拼接文档内容（截取前 15000 字符避免 token 溢出）
        StringBuilder sb = new StringBuilder();
        int maxChars = 15000;
        for (Document d : docs) {
            if (sb.length() >= maxChars) break;
            sb.append("## ").append(d.getTitle()).append("\n");
            List<Chunk> chunks = chunkRepository.findByDocumentIdOrderByChunkIndexAsc(d.getId());
            for (Chunk c : chunks) {
                if (sb.length() >= maxChars) break;
                String content = c.getContent();
                if (content != null && !content.isBlank()) {
                    sb.append(content).append("\n");
                }
            }
        }
        String context = sb.toString();
        if (context.isBlank()) throw new BusinessException("知识库文档内容为空");

        int n = (count == null || count < 1) ? 10 : Math.min(count, 50);

        String systemPrompt = """
                你是一个知识提炼专家。根据提供的知识库文档内容，抽取其中最重要的知识点，生成知识卡片。
                每张卡片包含四个字段：
                - title：简短标题
                - front：正面（问题或核心概念，若不适合可填空字符串）
                - back：背面（详细解析，务必基于原文，准确充实）
                - tags：标签数组，2-4 个关键词
                只输出一个 JSON 数组，不要包含额外说明文字或 Markdown 标记。
                示例：[{"title":"...","front":"...","back":"...","tags":["..."]}]
                """;

        String userMessage = String.format(
                "从以下文档内容中抽取 %d 张知识卡片，覆盖最重要的知识点：\n\n%s", n, context);

        String raw = aiProvider.chat(systemPrompt, userMessage);
        List<Map<String, Object>> parsed = parseCardsJson(raw);

        List<KnowledgeCard> saved = new ArrayList<>();
        String cat = blankToNull(category);
        for (Map<String, Object> item : parsed) {
            String title = asText(item.get("title"));
            String back = asText(item.get("back"));
            if (title.isBlank() || back.isBlank()) continue;
            String front = asText(item.get("front"));
            String tags = parseTags(item.get("tags"));
            KnowledgeCard card = KnowledgeCard.builder()
                    .userId(userId)
                    .knowledgeBaseId(knowledgeBaseId)
                    .title(title.length() > 300 ? title.substring(0, 300) : title)
                    .front(blankToNull(front))
                    .back(back)
                    .category(cat)
                    .tags(tags)
                    .source("AI")
                    .build();
            saved.add(cardRepository.save(card));
        }
        if (saved.isEmpty()) {
            throw new BusinessException("AI 未从知识库文档中抽取出有效卡片，请确认文档内容充实后重试");
        }
        log.info("AI 从知识库 {} 文档中抽取知识卡片 {} 张，用户 {}", knowledgeBaseId, saved.size(), userId);
        return saved;
    }

    // ───────────────────── 工具方法 ─────────────────────

    private List<Map<String, Object>> parseCardsJson(String raw) {
        if (raw == null) throw new BusinessException("AI 生成失败：返回为空");
        String json = raw.trim();
        // 去除 Markdown 代码块标记
        if (json.startsWith("```")) {
            int firstBracket = json.indexOf('[');
            int lastBracket = json.lastIndexOf(']');
            if (firstBracket >= 0 && lastBracket > firstBracket) {
                json = json.substring(firstBracket, lastBracket + 1);
            }
        } else {
            int firstBracket = json.indexOf('[');
            int lastBracket = json.lastIndexOf(']');
            if (firstBracket >= 0 && lastBracket > firstBracket) {
                json = json.substring(firstBracket, lastBracket + 1);
            }
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<Map<String, Object>>>() {});
        } catch (Exception e) {
            log.warn("AI 卡片 JSON 解析失败: {}", raw);
            throw new BusinessException("AI 返回内容无法解析为卡片，请重试");
        }
    }

    private String parseTags(Object tagsObj) {
        if (tagsObj instanceof List<?> list) {
            return list.stream()
                    .map(String::valueOf)
                    .filter(s -> !s.isBlank())
                    .limit(10)
                    .reduce((a, b) -> a + "," + b)
                    .orElse(null);
        }
        return normalizeTags(tagsObj == null ? null : String.valueOf(tagsObj));
    }

    private String normalizeTags(String tags) {
        if (!StringUtils.hasText(tags)) return null;
        return java.util.Arrays.stream(tags.split("[,，;；]"))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .limit(10)
                .distinct()
                .reduce((a, b) -> a + "," + b)
                .orElse(null);
    }

    private String asText(Object o) {
        return o == null ? "" : String.valueOf(o).trim();
    }

    private String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }
}
