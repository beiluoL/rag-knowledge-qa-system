package com.example.ragkb.service;

import com.example.ragkb.exception.BusinessException;
import com.example.ragkb.model.entity.*;
import com.example.ragkb.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 学习系统核心服务：
 * - 经验（XP）/ 等级（level）计算与升级；
 * - 每日/每周/每月任务驱动成长；
 * - 连续学习天数（streak）；
 * - 成就解锁判定；
 * - 从知识库 chunks 动态生成四种学习模式的卡片。
 * <p>
 * XP 规则：每学习 1 张卡片 +10 XP。等级公式 level = floor(sqrt(xp / 100)) + 1。
 * </p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class LearningService {

    private final UserProgressRepository userProgressRepository;
    private final StudyTaskRepository studyTaskRepository;
    private final AchievementRepository achievementRepository;
    private final UserAchievementRepository userAchievementRepository;
    private final KnowledgeBaseRepository knowledgeBaseRepository;
    private final DocumentRepository documentRepository;
    private final ChunkRepository chunkRepository;
    private final KnowledgeBaseService knowledgeBaseService;

    private static final int XP_PER_CARD = 10;

    // ═══════════════ 进度 / 等级 ═══════════════

    public UserProgress getOrInitProgress(Long userId) {
        return userProgressRepository.findByUserId(userId)
                .orElseGet(() -> userProgressRepository.save(UserProgress.builder()
                        .userId(userId).xp(0L).level(1)
                        .currentStreak(0).longestStreak(0).cardsStudied(0).build()));
    }

    /** 等级由累计 XP 推导 */
    public static int computeLevel(long xp) {
        return (int) Math.floor(Math.sqrt(xp / 100.0)) + 1;
    }

    /** 当前等级升到下一级所需的总 XP 阈值 */
    private long levelThreshold(int level) {
        return (long) level * level * 100;
    }

    public String titleOf(int level) {
        if (level >= 20) return "一代宗师";
        if (level >= 15) return "领域权威";
        if (level >= 10) return "资深专家";
        if (level >= 5) return "进阶学者";
        if (level >= 3) return "初级学员";
        return "萌新学员";
    }

    // ═══════════════ 仪表盘 ═══════════════

    public Map<String, Object> getDashboard(Long userId) {
        UserProgress p = getOrInitProgress(userId);
        int level = p.getLevel();
        long cur = levelThreshold(level - 1);
        long next = levelThreshold(level);
        double progress = next > cur ? (double) (p.getXp() - cur) / (next - cur) : 1.0;

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("xp", p.getXp());
        result.put("level", level);
        result.put("title", titleOf(level));
        result.put("levelProgress", Math.min(1.0, Math.max(0.0, progress)));
        result.put("xpToNextLevel", Math.max(0, next - p.getXp()));
        result.put("currentStreak", p.getCurrentStreak());
        result.put("longestStreak", p.getLongestStreak());
        result.put("cardsStudied", p.getCardsStudied());
        result.put("todayTasks", getTasks(userId, "daily"));
        result.put("recentAchievements", getAchievements(userId).stream()
                .filter(a -> Boolean.TRUE.equals(a.get("unlocked")))
                .limit(4).toList());
        return result;
    }

    // ═══════════════ 任务 ═══════════════

    public List<StudyTask> getTasks(Long userId, String cycle) {
        if (cycle == null || cycle.isBlank()) return studyTaskRepository.findByUserIdOrderByCreatedAtDesc(userId);
        return studyTaskRepository.findByUserIdAndCycle(userId, cycle);
    }

    @Transactional
    public StudyTask createTask(Long userId, String title, String mode, String cycle,
                                Long knowledgeBaseId, int targetCount) {
        if (title == null || title.isBlank()) throw new BusinessException("任务标题不能为空");
        StudyTask task = StudyTask.builder()
                .userId(userId).title(title).mode(mode).cycle(cycle)
                .knowledgeBaseId(knowledgeBaseId).targetCount(targetCount)
                .progressCount(0).status("ACTIVE").dueAt(cycleDueAt(cycle)).build();
        return studyTaskRepository.save(task);
    }

    /** 为当前用户生成周期任务（覆盖该周期已有任务），从知识库派生 */
    @Transactional
    public List<StudyTask> generateCycleTasks(Long userId, String cycle) {
        studyTaskRepository.findByUserIdAndCycle(userId, cycle)
                .forEach(t -> studyTaskRepository.delete(t));
        List<KnowledgeBase> kbs = knowledgeBaseRepository.findAllByOrderBySortOrderAscCreatedAtDesc();
        if (kbs.isEmpty()) return List.of();
        String[] modes = {"list", "flashcard", "swipe", "challenge"};
        List<StudyTask> created = new ArrayList<>();
        int n = Math.min(kbs.size(), 4);
        for (int i = 0; i < n; i++) {
            KnowledgeBase kb = kbs.get(i);
            created.add(studyTaskRepository.save(StudyTask.builder()
                    .userId(userId)
                    .knowledgeBaseId(kb.getId())
                    .title("学习《" + kb.getName() + "》")
                    .mode(modes[i % modes.length])
                    .cycle(cycle)
                    .targetCount(10)
                    .progressCount(0)
                    .status("ACTIVE")
                    .dueAt(cycleDueAt(cycle))
                    .build()));
        }
        return created;
    }

    // ═══════════════ 完成学习（涨经验 / 升级 / 成就）═══════════════

    @Transactional
    public Map<String, Object> completeStudy(Long userId, int cards, Long knowledgeBaseId, String mode) {
        if (cards <= 0) cards = 1;
        UserProgress p = getOrInitProgress(userId);
        int gained = cards * XP_PER_CARD;
        int oldLevel = p.getLevel();

        p.setXp(p.getXp() + gained);
        p.setCardsStudied(p.getCardsStudied() + cards);

        // 连续学习天数
        LocalDate today = LocalDate.now();
        if (p.getLastStudyDate() == null) {
            p.setCurrentStreak(1);
        } else if (p.getLastStudyDate().equals(today)) {
            // 同一天不改变
        } else if (p.getLastStudyDate().plusDays(1).equals(today)) {
            p.setCurrentStreak(p.getCurrentStreak() + 1);
        } else {
            p.setCurrentStreak(1);
        }
        p.setLastStudyDate(today);
        if (p.getCurrentStreak() > p.getLongestStreak()) p.setLongestStreak(p.getCurrentStreak());

        p.setLevel(computeLevel(p.getXp()));
        userProgressRepository.save(p);

        List<Achievement> unlocked = checkAchievements(userId, p);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("xpGained", gained);
        result.put("totalXp", p.getXp());
        result.put("level", p.getLevel());
        result.put("title", titleOf(p.getLevel()));
        result.put("leveledUp", p.getLevel() > oldLevel);
        result.put("streak", p.getCurrentStreak());
        result.put("unlocked", unlocked.stream()
                .map(a -> Map.of("code", a.getCode(), "name", a.getName(), "icon", a.getIcon()))
                .toList());
        return result;
    }

    /** 校验并解锁满足条件的成就，返回新解锁列表 */
    private List<Achievement> checkAchievements(Long userId, UserProgress p) {
        List<Achievement> defs = achievementRepository.findAll();
        List<Achievement> newly = new ArrayList<>();
        for (Achievement a : defs) {
            if (userAchievementRepository.existsByUserIdAndAchievementCode(userId, a.getCode())) continue;
            long val = switch (a.getMetric() == null ? "" : a.getMetric()) {
                case "xp" -> p.getXp();
                case "cards" -> p.getCardsStudied();
                case "streak" -> p.getCurrentStreak();
                case "level" -> p.getLevel();
                default -> 0;
            };
            if (val >= (a.getThreshold() == null ? 0 : a.getThreshold())) {
                userAchievementRepository.save(UserAchievement.builder()
                        .userId(userId).achievementCode(a.getCode()).build());
                newly.add(a);
            }
        }
        return newly;
    }

    // ═══════════════ 成就 ═══════════════

    public List<Map<String, Object>> getAchievements(Long userId) {
        List<String> unlockedCodes = userAchievementRepository.findByUserId(userId).stream()
                .map(UserAchievement::getAchievementCode).collect(Collectors.toList());
        return achievementRepository.findAllByOrderByThresholdAsc().stream().map(a -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("code", a.getCode());
            m.put("name", a.getName());
            m.put("description", a.getDescription());
            m.put("icon", a.getIcon());
            m.put("metric", a.getMetric());
            m.put("threshold", a.getThreshold());
            m.put("unlocked", unlockedCodes.contains(a.getCode()));
            return m;
        }).toList();
    }

    // ═══════════════ 学习卡片（从知识库 chunks 生成）═══════════════

    public List<Map<String, Object>> studyCards(Long knowledgeBaseId, String mode) {
        // 解析自身 + 全部后代知识库（选父库时也能学习其下所有子库）
        List<Long> kbIds = knowledgeBaseService.getDescendantIds(knowledgeBaseId);
        List<Document> docs = documentRepository.findByKnowledgeBaseIdIn(kbIds);
        List<Map<String, Object>> cards = new ArrayList<>();
        int idx = 0;
        for (Document d : docs) {
            List<Chunk> chunks = chunkRepository.findByDocumentIdOrderByChunkIndexAsc(d.getId());
            for (Chunk c : chunks) {
                String content = c.getContent();
                if (content == null || content.isBlank()) continue;
                idx++;
                cards.add(Map.of(
                        "id", idx,
                        "documentId", d.getId(),
                        "documentTitle", d.getTitle(),
                        "front", makeFront(content, mode),
                        "back", content));
            }
        }
        return cards;
    }

    private String makeFront(String content, String mode) {
        String firstLine = content.lines().findFirst().orElse(content).trim();
        if ("list".equals(mode)) {
            return firstLine.length() > 80 ? firstLine.substring(0, 80) + "…" : firstLine;
        }
        // 闪卡 / 刷卡 / 闯关：以片段作为"问题面"，完整内容作为"答案面"
        String prompt = firstLine.length() > 50 ? firstLine.substring(0, 50) + "…" : firstLine;
        return "请复述 / 理解：" + prompt;
    }

    private LocalDateTime cycleDueAt(String cycle) {
        LocalDateTime now = LocalDateTime.now();
        return switch (cycle == null ? "" : cycle) {
            case "weekly" -> now.plusDays(7);
            case "monthly" -> now.plusDays(30);
            default -> now.plusDays(1); // daily
        };
    }
}
