package com.example.ragkb.controller;

import com.example.ragkb.model.entity.StudyTask;
import com.example.ragkb.service.LearningService;
import com.example.ragkb.service.ReviewPlanService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 学习系统接口（需登录）。
 * 提供仪表盘、任务、成就、学习卡片生成、个性化复习计划与完成学习回调（涨经验/升级/解锁成就）。
 */
@RestController
@RequestMapping("/api/learning")
@RequiredArgsConstructor
public class LearningController {

    private final LearningService learningService;
    private final ReviewPlanService reviewPlanService;

    private Long uid(Authentication auth) {
        return Long.parseLong(auth.getPrincipal().toString());
    }

    /** 学习仪表盘：经验 / 等级 / 进度 / 连续天数 / 今日任务 / 近期成就 */
    @GetMapping("/dashboard")
    public ResponseEntity<Map<String, Object>> dashboard(Authentication authentication) {
        return ResponseEntity.ok(learningService.getDashboard(uid(authentication)));
    }

    /** 任务列表（按周期筛选：daily/weekly/monthly） */
    @GetMapping("/tasks")
    public ResponseEntity<List<StudyTask>> tasks(@RequestParam(required = false) String cycle,
                                                Authentication authentication) {
        return ResponseEntity.ok(learningService.getTasks(uid(authentication), cycle));
    }

    /** 生成周期任务（从知识库派生） */
    @PostMapping("/tasks/generate")
    public ResponseEntity<List<StudyTask>> generate(@RequestParam(defaultValue = "daily") String cycle,
                                                    Authentication authentication) {
        return ResponseEntity.ok(learningService.generateCycleTasks(uid(authentication), cycle));
    }

    /** 手动创建任务 */
    @PostMapping("/tasks")
    public ResponseEntity<StudyTask> createTask(@RequestBody Map<String, Object> body,
                                                Authentication authentication) {
        Long kbId = body.get("knowledgeBaseId") != null
                ? Long.valueOf(body.get("knowledgeBaseId").toString()) : null;
        int target = body.get("targetCount") != null
                ? Integer.parseInt(body.get("targetCount").toString()) : 10;
        StudyTask task = learningService.createTask(
                uid(authentication),
                (String) body.get("title"),
                (String) body.get("mode"),
                (String) body.get("cycle"),
                kbId, target);
        return ResponseEntity.ok(task);
    }

    /** 完成学习：回调涨经验、刷新连续天数、解锁成就 */
    @PostMapping("/study/complete")
    public ResponseEntity<Map<String, Object>> complete(@RequestBody Map<String, Object> body,
                                                        Authentication authentication) {
        int cards = body.get("cards") != null ? Integer.parseInt(body.get("cards").toString()) : 1;
        Long kbId = body.get("knowledgeBaseId") != null
                ? Long.valueOf(body.get("knowledgeBaseId").toString()) : null;
        String mode = (String) body.get("mode");
        return ResponseEntity.ok(learningService.completeStudy(uid(authentication), cards, kbId, mode));
    }

    /** 成就墙（含解锁状态） */
    @GetMapping("/achievements")
    public ResponseEntity<List<Map<String, Object>>> achievements(Authentication authentication) {
        return ResponseEntity.ok(learningService.getAchievements(uid(authentication)));
    }

    /** 学习卡片：从指定知识库的文档 chunks 动态生成 */
    @GetMapping("/cards")
    public ResponseEntity<List<Map<String, Object>>> cards(@RequestParam Long knowledgeBaseId,
                                                          @RequestParam(defaultValue = "flashcard") String mode) {
        return ResponseEntity.ok(learningService.studyCards(knowledgeBaseId, mode));
    }

    /** 个性化复习计划：基于知识库内容与学习目标，由大模型生成按天安排 */
    @PostMapping("/review-plan")
    public ResponseEntity<Map<String, Object>> reviewPlan(@RequestBody Map<String, Object> body,
                                                         Authentication authentication) {
        Long kbId = body.get("knowledgeBaseId") != null
                ? Long.valueOf(body.get("knowledgeBaseId").toString()) : null;
        String goal = body.get("goal") != null ? body.get("goal").toString() : null;
        int days = body.get("days") != null ? Integer.parseInt(body.get("days").toString()) : 7;
        int dailyMinutes = body.get("dailyMinutes") != null
                ? Integer.parseInt(body.get("dailyMinutes").toString()) : 30;
        return ResponseEntity.ok(reviewPlanService.generate(kbId, goal, days, dailyMinutes));
    }
}
