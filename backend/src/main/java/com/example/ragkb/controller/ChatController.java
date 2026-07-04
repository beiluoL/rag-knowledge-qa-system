package com.example.ragkb.controller;

import com.example.ragkb.model.dto.*;
import com.example.ragkb.model.entity.Conversation;
import com.example.ragkb.service.ConversationService;
import com.example.ragkb.service.EmbeddingService;
import com.example.ragkb.service.RAGService;
import com.example.ragkb.service.RAGService.RAGContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

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
    private final ObjectMapper objectMapper;

    public ChatController(RAGService ragService,
                           ConversationService conversationService,
                           EmbeddingService embeddingService,
                           ObjectMapper objectMapper) {
        this.ragService = ragService;
        this.conversationService = conversationService;
        this.embeddingService = embeddingService;
        this.objectMapper = objectMapper;
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

            // ── Step 1: 向量化问题 ──
            long t1 = System.currentTimeMillis();
            sendStep(writer, "embedding", "running", "正在向量化问题...",
                    "模型: " + embeddingService.getMode() + " (" + embeddingService.getDimension() + "维)");
            writer.flush();

            String questionVector = embeddingService.embed(question);
            long t1Done = System.currentTimeMillis();

            sendStep(writer, "embedding", "done", "问题向量化完成",
                    "耗时 " + (t1Done - t1) + "ms, " + embeddingService.getDimension() + " 维向量");
            writer.flush();

            // ── Step 2: 语义搜索 ──
            sendStep(writer, "searching", "running", "正在搜索知识库...",
                    "余弦相似度搜索 Top-" + ragService.getTopK());
            writer.flush();

            RAGContext ragContext = ragService.preparePrompt(conversationId, question);
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
                    "模型: " + ragService.getCurrentMode());
            writer.flush();

            // 7. 生成回答（流式）
            String answer = ragService.generateAnswer(
                    ragContext.systemPrompt(), ragContext.userMessage());
            if (answer != null && !answer.isBlank()) {
                for (int i = 0; i < answer.length(); i++) {
                    String ch = answer.substring(i, i + 1);
                    sendSSE(writer, "content", ch);
                    writer.flush();
                }
            }

            long t3Done = System.currentTimeMillis();
            sendStep(writer, "generating", "done", "回答生成完成",
                    "耗时 " + (t3Done - t3) + "ms, 回答长度 " + (answer != null ? answer.length() : 0) + " 字");
            writer.flush();

            // 8. 保存 AI 回答
            conversationService.saveAssistantMessage(
                    conversationId, answer, ragContext.references());

            // 9. 完成（总耗时）
            long totalTime = t3Done - t1;
            sendSSE(writer, "done",
                    objectMapper.writeValueAsString(Map.of(
                            "conversationId", conversationId,
                            "references", ragContext.references(),
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
    public ResponseEntity<List<MessageDTO>> getConversationMessages(@PathVariable Long id) {
        return ResponseEntity.ok(conversationService.getConversationMessages(id));
    }

    @PutMapping("/conversations/{id}")
    public ResponseEntity<Map<String, String>> renameConversation(
            @PathVariable Long id, @RequestBody Map<String, String> body) {
        conversationService.renameConversation(id, body.get("title"));
        return ResponseEntity.ok(Map.of("message", "重命名成功"));
    }

    @DeleteMapping("/conversations/{id}")
    public ResponseEntity<Map<String, String>> deleteConversation(@PathVariable Long id) {
        conversationService.deleteConversation(id);
        return ResponseEntity.ok(Map.of("message", "会话已删除"));
    }
}
