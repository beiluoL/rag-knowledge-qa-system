package com.example.ragkb.controller;

import com.example.ragkb.model.dto.*;
import com.example.ragkb.model.entity.Conversation;
import com.example.ragkb.service.ConversationService;
import com.example.ragkb.service.ConversationConfigService;
import com.example.ragkb.service.EmbeddingService;
import com.example.ragkb.service.MemoryService;
import com.example.ragkb.service.RAGService;
import com.example.ragkb.service.RAGService.RAGContext;
import com.example.ragkb.service.QueryRewriter;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/chat")
@Slf4j
public class ChatController {

    private final RAGService ragService;
    private final ConversationService conversationService;
    private final EmbeddingService embeddingService;
    private final QueryRewriter queryRewriter;
    private final ObjectMapper objectMapper;
    private final ConversationConfigService configService;
    private final MemoryService memoryService;

    public ChatController(RAGService ragService,
                           ConversationService conversationService,
                           EmbeddingService embeddingService,
                           QueryRewriter queryRewriter,
                           ObjectMapper objectMapper,
                           ConversationConfigService configService,
                           MemoryService memoryService) {
        this.ragService = ragService;
        this.conversationService = conversationService;
        this.embeddingService = embeddingService;
        this.queryRewriter = queryRewriter;
        this.objectMapper = objectMapper;
        this.configService = configService;
        this.memoryService = memoryService;
    }

    @PostMapping("/send")
    public void sendMessage(@Valid @RequestBody ChatRequest request,
                            Authentication authentication,
                            HttpServletResponse response) {

        Long userId = Long.parseLong(authentication.getPrincipal().toString());
        final String question = request.getQuestion();
        final Long requestConvId = request.getConversationId();

        // 设置 SSE 响应头
        response.setContentType("text/event-stream");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Cache-Control", "no-cache");
        response.setHeader("X-Accel-Buffering", "no");

        try {
            PrintWriter writer = response.getWriter();

            // 1. 获取或创建会话
            Long cid = requestConvId;
            if (cid == null) {
                cid = conversationService.createConversation(userId, "新对话").getId();
            }
            final Long conversationId = cid;

            // 2. 保存用户消息
            conversationService.saveUserMessage(conversationId, question);
            conversationService.updateConversationTitle(conversationId, question);

            // 3. 发送会话 ID
            sendSSE(writer, "conversation",
                    objectMapper.writeValueAsString(Map.of("conversationId", conversationId)));

            // ═══════════════════════════════════════════
            // RAG 过程逐步推送（可视化用）
            // ═══════════════════════════════════════════

            // ── Step 0: 查询改写（优化检索查询）──
            long t0 = System.currentTimeMillis();
            sendStep(writer, "rewriting", "running", "正在优化检索查询...",
                    "框架: " + ragService.getCurrentFramework());
            writer.flush();

            // 用改写后的查询做语义检索，原始问题仍用于关键词检索与最终 Prompt
            String retrievalQuery = queryRewriter.rewrite(question);
            long t0Done = System.currentTimeMillis();
            boolean rewritten = !retrievalQuery.equals(question);
            sendStep(writer, "rewriting", "done", rewritten ? "查询已优化" : "无需改写",
                    "耗时 " + (t0Done - t0) + "ms"
                            + (rewritten ? "，检索将使用优化后的查询" : ""));
            writer.flush();

            // ── Step 1: 向量化问题 ──
            long t1 = System.currentTimeMillis();
            sendStep(writer, "embedding", "running", "正在向量化问题...",
                    "框架: " + ragService.getCurrentFramework() + "，模型: " + embeddingService.getMode()
                            + " (" + embeddingService.getDimension() + "维)");
            writer.flush();

            // 向量化的问题可复用于语义搜索，避免 RAGService 内部重复调用
            String questionVector = embeddingService.embed(retrievalQuery);
            long t1Done = System.currentTimeMillis();

            sendStep(writer, "embedding", "done", "问题向量化完成",
                    "耗时 " + (t1Done - t1) + "ms, " + embeddingService.getDimension() + " 维向量");
            writer.flush();

            // ── Step 2: 语义搜索 ──
            sendStep(writer, "searching", "running", "正在搜索知识库...",
                    "混合检索 Top-" + ragService.getTopK() + "（向量+关键词 RRF 融合）");
            writer.flush();

            // 传入已计算的 questionVector 与知识库 ID，避免 RAGService 内部重复向量化
            // 注入用户长期记忆（跨会话偏好/摘要），让 AI 记住用户
            String memoryContext = memoryService.getMemoryContextPrompt(userId);
            RAGContext ragContext = ragService.preparePrompt(
                    conversationId, question, request.getKnowledgeBaseId(), questionVector, memoryContext);
            long t2Done = System.currentTimeMillis();

            List<ReferenceDTO> references = ragContext.references();
            String searchDetail;
            if (!references.isEmpty()) {
                StringBuilder sb = new StringBuilder();
                sb.append("找到 ").append(references.size()).append(" 条相关文档");
                sb.append(" (");
                for (int i = 0; i < references.size(); i++) {
                    if (i > 0) sb.append(", ");
                    sb.append(String.format("%.0f%%", references.get(i).getScore() * 100));
                }
                sb.append(")");
                searchDetail = sb.toString();
            } else {
                searchDetail = "未找到相关文档（相似度均低于阈值）";
            }
            sendStep(writer, "searching", "done", "语义搜索完成", searchDetail);
            writer.flush();

            // ── Step 3: 构建提示词 ──
            sendStep(writer, "prompt", "running", "正在构建提示词...",
                    "System Prompt + 参考资料 + 历史对话(" + ragService.getMaxHistoryRounds() + "轮)");
            writer.flush();

            // 短暂延迟让前端看到动画
            sendStep(writer, "prompt", "done", "提示词构建完成",
                    "参考资料 " + references.size() + " 条, 历史对话已加载");
            writer.flush();

            // 6. 发送引用
            if (!references.isEmpty()) {
                sendSSE(writer, "references",
                        objectMapper.writeValueAsString(references));
            }

            // ── Step 4: AI 生成 ──
            long t3 = System.currentTimeMillis();
            sendStep(writer, "generating", "running", "AI 正在生成回答...",
                    "框架: " + ragService.getCurrentFramework() + "，模型: " + ragService.getCurrentMode());
            writer.flush();

            // 7. 生成回答
            //    生成策略由「对话配置」中的 trueSseStreamingEnabled 决定（管理员可在后台实时切换）：
            //      true  -> 真 SSE 流式：模型边生成边按 chunk 推送 content 事件
            //      false -> 模拟逐字：先取完整答案，再分块推送 content 事件（前端消费方式不变）
            String answer;
            if (configService.isTrueSseStreamingEnabled()) {
                StringBuilder answerBuf = new StringBuilder();
                ragService.generateAnswerStream(ragContext.systemPrompt(), ragContext.userMessage())
                        .doOnNext(chunk -> {
                            if (chunk != null && !chunk.isEmpty()) {
                                answerBuf.append(chunk);
                                sendSSE(writer, "content", chunk);
                                writer.flush();
                            }
                        })
                        .blockLast();  // 阻塞至流结束（servlet 线程同步写出）
                answer = answerBuf.toString();
            } else {
                // 模拟逐字：先完整生成，再按小段推送，制造打字机效果
                String full = ragService.generateAnswer(ragContext.systemPrompt(), ragContext.userMessage());
                int len = full.length();
                int cursor = 0;
                int chunkSize = 2; // 每次推送 2 个字符，模拟逐字
                while (cursor < len) {
                    int end = Math.min(cursor + chunkSize, len);
                    sendSSE(writer, "content", full.substring(cursor, end));
                    writer.flush();
                    cursor = end;
                    try {
                        Thread.sleep(12); // 模拟打字延迟
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
                answer = full;
            }

            long t3Done = System.currentTimeMillis();
            sendStep(writer, "generating", "done", "回答生成完成",
                    "耗时 " + (t3Done - t3) + "ms, 回答长度 " + (answer != null ? answer.length() : 0) + " 字");
            writer.flush();

            // 8. 保存 AI 回答
            conversationService.saveAssistantMessage(
                    conversationId, answer, ragContext.references());

            // 9. 对话记忆增强：异步抽取用户偏好 + 超长对话摘要（不阻塞 SSE 响应）
            memoryService.extractMemoriesAsync(userId, conversationId, question, answer);
            memoryService.maybeSummarizeAsync(userId, conversationId);

            // 9. 完成（总耗时）
            long totalTime = t3Done - t1;
            // 拒答标志：检索阶段未命中任何相关片段时，回答大概率为"无法回答"
            boolean refused = references.isEmpty();
            sendSSE(writer, "done",
                    objectMapper.writeValueAsString(Map.of(
                            "conversationId", conversationId,
                            "references", ragContext.references(),
                            "refused", refused,
                            "totalTime", totalTime + "ms"
                    )));
            writer.flush();

        } catch (Exception e) {
            log.error("问答处理失败", e);
            try {
                PrintWriter writer = response.getWriter();
                sendStep(writer, "error", "done", "处理失败", e.getMessage());
                sendSSE(writer, "error", "处理失败: " + e.getMessage());
                writer.flush();
            } catch (Exception ex) {
                log.error("写入失败", ex);
            }
        }
    }

    /**
     * 发送 RAG 步骤事件（可视化用）
     */
    private void sendStep(PrintWriter writer, String step, String status,
                          String label, String detail) {
        try {
            Map<String, String> stepData = new LinkedHashMap<>();
            stepData.put("step", step);
            stepData.put("status", status);
            stepData.put("label", label);
            stepData.put("detail", detail);
            writer.write("event:step\n");
            writer.write("data:" + objectMapper.writeValueAsString(stepData) + "\n\n");
        } catch (Exception e) {
            log.warn("发送步骤事件失败: {}", e.getMessage());
        }
    }

    private void sendSSE(PrintWriter writer, String event, String data) {
        writer.write("event:" + event + "\n");
        writer.write("data:" + data + "\n\n");
    }

    @GetMapping("/conversations")
    public ResponseEntity<List<Conversation>> getConversations(Authentication authentication) {
        Long userId = Long.parseLong(authentication.getPrincipal().toString());
        return ResponseEntity.ok(conversationService.getUserConversations(userId));
    }

    @GetMapping("/conversations/{id}")
    public ResponseEntity<List<MessageDTO>> getConversationMessages(@PathVariable Long id, Authentication authentication) {
        Long userId = Long.parseLong(authentication.getPrincipal().toString());
        conversationService.assertOwnership(id, userId);
        return ResponseEntity.ok(conversationService.getConversationMessages(id));
    }

    @PutMapping("/conversations/{id}")
    public ResponseEntity<Map<String, String>> renameConversation(
            @PathVariable Long id, @RequestBody Map<String, String> body, Authentication authentication) {
        Long userId = Long.parseLong(authentication.getPrincipal().toString());
        conversationService.assertOwnership(id, userId);
        conversationService.renameConversation(id, body.get("title"));
        return ResponseEntity.ok(Map.of("message", "重命名成功"));
    }

    @DeleteMapping("/conversations/{id}")
    public ResponseEntity<Map<String, String>> deleteConversation(@PathVariable Long id, Authentication authentication) {
        Long userId = Long.parseLong(authentication.getPrincipal().toString());
        conversationService.assertOwnership(id, userId);
        conversationService.deleteConversation(id);
        return ResponseEntity.ok(Map.of("message", "会话已删除"));
    }

    /**
     * 消息反馈（点赞/踩）
     * 仅允许对 AI 回答消息进行反馈，点击相同反馈值可取消
     *
     * @param id   消息 ID
     * @param body 包含 "feedback" 字段（like/dislike）
     */
    @PutMapping("/messages/{id}/feedback")
    public ResponseEntity<Map<String, String>> feedbackMessage(
            @PathVariable Long id, @RequestBody Map<String, String> body, Authentication authentication) {
        Long userId = Long.parseLong(authentication.getPrincipal().toString());
        conversationService.assertMessageOwner(id, userId);
        conversationService.feedbackMessage(id, body.get("feedback"));
        return ResponseEntity.ok(Map.of("message", "反馈成功"));
    }

    /**
     * 搜索会话（按消息内容关键词）
     *
     * @param keyword        搜索关键词
     * @param authentication 当前用户认证信息
     * @return 匹配的会话列表
     */
    @GetMapping("/conversations/search")
    public ResponseEntity<List<Conversation>> searchConversations(
            @RequestParam(required = false) String keyword,
            Authentication authentication) {
        Long userId = Long.parseLong(authentication.getPrincipal().toString());
        return ResponseEntity.ok(conversationService.searchConversations(userId, keyword));
    }

    /**
     * 导出会话内容为 Markdown 格式
     * 返回纯文本，前端可直接下载保存为 .md 文件
     *
     * @param id 会话 ID
     * @return Markdown 格式的会话内容
     */
    @GetMapping("/conversations/{id}/export")
    public ResponseEntity<String> exportConversation(@PathVariable Long id, Authentication authentication) {
        Long userId = Long.parseLong(authentication.getPrincipal().toString());
        conversationService.assertOwnership(id, userId);
        String markdown = conversationService.exportConversation(id);
        return ResponseEntity.ok()
                .header("Content-Type", "text/markdown; charset=UTF-8")
                .header("Content-Disposition", "attachment; filename=conversation.md")
                .body(markdown);
    }

    /**
     * 切换会话置顶状态
     * 点击已置顶的会话取消置顶，点击未置顶的则置顶
     *
     * @param id 会话 ID
     */
    @PutMapping("/conversations/{id}/pin")
    public ResponseEntity<Map<String, Object>> togglePinConversation(@PathVariable Long id, Authentication authentication) {
        Long userId = Long.parseLong(authentication.getPrincipal().toString());
        conversationService.assertOwnership(id, userId);
        Conversation conv = conversationService.togglePin(id);
        return ResponseEntity.ok(Map.of(
                "id", conv.getId(),
                "pinned", Boolean.TRUE.equals(conv.getPinned()),
                "message", Boolean.TRUE.equals(conv.getPinned()) ? "已置顶" : "已取消置顶"
        ));
    }

    /**
     * 公开只读：RAG 过程可视化开关（全局配置，普通用户聊天页读取，决定是否展示推理步骤）。
     * 不暴露敏感信息，无需管理员权限。
     */
    @GetMapping("/rag-visualization")
    public ResponseEntity<Map<String, Boolean>> getRagVisualization() {
        return ResponseEntity.ok(Map.of("enabled", configService.isRagVisualizationEnabled()));
    }
}
