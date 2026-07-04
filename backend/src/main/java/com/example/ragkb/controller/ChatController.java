package com.example.ragkb.controller;

import com.example.ragkb.model.dto.*;
import com.example.ragkb.model.entity.Conversation;
import com.example.ragkb.service.ConversationService;
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
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/chat")
@Slf4j
public class ChatController {

    private final RAGService ragService;
    private final ConversationService conversationService;
    private final ObjectMapper objectMapper;

    public ChatController(RAGService ragService,
                           ConversationService conversationService,
                           ObjectMapper objectMapper) {
        this.ragService = ragService;
        this.conversationService = conversationService;
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

            // 4. 状态
            sendSSE(writer, "status", "正在搜索知识库...");

            // 5. RAG 检索
            RAGContext ragContext = ragService.preparePrompt(conversationId, question);

            // 6. 发送引用
            if (!ragContext.references().isEmpty()) {
                sendSSE(writer, "references",
                        objectMapper.writeValueAsString(ragContext.references()));
            }

            // 7. 生成回答
            String answer = ragService.generateAnswer(
                    ragContext.systemPrompt(), ragContext.userMessage());
            if (answer != null && !answer.isBlank()) {
                for (int i = 0; i < answer.length(); i++) {
                    String ch = answer.substring(i, i + 1);
                    sendSSE(writer, "content", ch);
                    writer.flush();
                }
            }

            // 8. 保存 AI 回答
            conversationService.saveAssistantMessage(
                    conversationId, answer, ragContext.references());

            // 9. 完成
            sendSSE(writer, "done",
                    objectMapper.writeValueAsString(Map.of(
                            "conversationId", conversationId,
                            "references", ragContext.references()
                    )));
            writer.flush();

        } catch (Exception e) {
            log.error("问答处理失败", e);
            try {
                PrintWriter writer = response.getWriter();
                sendSSE(writer, "error", "处理失败: " + e.getMessage());
                writer.flush();
            } catch (Exception ex) {
                log.error("写入失败", ex);
            }
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
