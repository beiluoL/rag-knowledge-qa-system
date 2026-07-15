package com.example.ragkb.service;

import com.example.ragkb.exception.BusinessException;
import com.example.ragkb.model.dto.MessageDTO;
import com.example.ragkb.model.dto.ReferenceDTO;
import com.example.ragkb.model.entity.Conversation;
import com.example.ragkb.model.entity.Message;
import com.example.ragkb.repository.ConversationRepository;
import com.example.ragkb.repository.MessageRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ConversationService {

    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final ObjectMapper objectMapper;

    /**
     * 创建会话
     */
    public Conversation createConversation(Long userId, String title) {
        Conversation conversation = Conversation.builder()
                .userId(userId)
                .title(title != null ? title : "新对话")
                .build();
        return conversationRepository.save(conversation);
    }

    /**
     * 获取用户的会话列表（置顶优先，按更新时间倒序）
     */
    public List<Conversation> getUserConversations(Long userId) {
        return conversationRepository.findByUserIdOrderByPinnedDescUpdatedAtDesc(userId);
    }

    /**
     * 获取会话详情（含消息）
     */
    public Conversation getConversation(Long conversationId) {
        return conversationRepository.findById(conversationId)
                .orElseThrow(() -> new BusinessException("会话不存在"));
    }

    /** 校验会话归属当前用户，否则抛出拒绝访问异常（会话越权防护） */
    public void assertOwnership(Long conversationId, Long userId) {
        Conversation conv = getConversation(conversationId);
        if (!conv.getUserId().equals(userId)) {
            throw new org.springframework.security.access.AccessDeniedException("无权访问该会话");
        }
    }

    /** 校验消息归属当前用户（通过消息找到所属会话再比对 owner） */
    public void assertMessageOwner(Long messageId, Long userId) {
        Message msg = messageRepository.findById(messageId)
                .orElseThrow(() -> new BusinessException("消息不存在"));
        Conversation conv = conversationRepository.findById(msg.getConversationId())
                .orElseThrow(() -> new BusinessException("会话不存在"));
        if (!conv.getUserId().equals(userId)) {
            throw new org.springframework.security.access.AccessDeniedException("无权操作该消息");
        }
    }

    /**
     * 获取会话中的所有消息
     */
    public List<MessageDTO> getConversationMessages(Long conversationId) {
        List<Message> messages = messageRepository
                .findByConversationIdOrderByCreatedAtAsc(conversationId);
        return messages.stream()
                .map(this::convertToDTO)
                .toList();
    }

    /**
     * 保存用户消息
     */
    public Message saveUserMessage(Long conversationId, String content) {
        Message message = Message.builder()
                .conversationId(conversationId)
                .role("USER")
                .content(content)
                .build();
        Message saved = messageRepository.save(message);

        // 更新会话时间
        conversationRepository.findById(conversationId).ifPresent(conv -> {
            conv.setUpdatedAt(java.time.LocalDateTime.now());
            conversationRepository.save(conv);
        });

        return saved;
    }

    /**
     * 保存 AI 回答
     */
    public Message saveAssistantMessage(Long conversationId, String content,
                                         List<ReferenceDTO> references) {
        String refJson = null;
        if (references != null && !references.isEmpty()) {
            try {
                refJson = objectMapper.writeValueAsString(references);
            } catch (JsonProcessingException e) {
                // ignore
            }
        }

        Message message = Message.builder()
                .conversationId(conversationId)
                .role("ASSISTANT")
                .content(content)
                .referencesData(refJson)
                .build();

        return messageRepository.save(message);
    }

    /**
     * 更新会话标题（自动使用用户第一个问题）
     */
    public void updateConversationTitle(Long conversationId, String firstQuestion) {
        if (firstQuestion == null || firstQuestion.isBlank()) return;
        String title = firstQuestion.length() > 30
                ? firstQuestion.substring(0, 30) + "..."
                : firstQuestion;
        conversationRepository.findById(conversationId).ifPresent(conv -> {
            // 仅当标题是默认值时更新
            if ("新对话".equals(conv.getTitle())) {
                conv.setTitle(title);
                conversationRepository.save(conv);
            }
        });
    }

    /**
     * 重命名会话
     */
    public void renameConversation(Long conversationId, String newTitle) {
        Conversation conv = getConversation(conversationId);
        conv.setTitle(newTitle);
        conversationRepository.save(conv);
    }

    /**
     * 删除会话及消息
     */
    @Transactional
    public void deleteConversation(Long conversationId) {
        messageRepository.deleteByConversationId(conversationId);
        conversationRepository.deleteById(conversationId);
    }

    /**
     * 消息反馈（点赞/踩）
     * 仅允许对 ASSISTANT 角色的消息进行反馈
     *
     * @param messageId 消息 ID
     * @param feedback  反馈值：like、dislike 或 null（取消反馈）
     */
    public void feedbackMessage(Long messageId, String feedback) {
        Message message = messageRepository.findById(messageId)
                .orElseThrow(() -> new BusinessException("消息不存在"));
        if (!"ASSISTANT".equals(message.getRole())) {
            throw new BusinessException("只能对 AI 回答进行反馈");
        }
        // 点击同一个反馈值则取消
        if (feedback != null && feedback.equals(message.getFeedback())) {
            message.setFeedback(null);
        } else {
            message.setFeedback(feedback);
        }
        messageRepository.save(message);
    }

    /**
     * 搜索用户的会话（按消息内容全文搜索）
     *
     * @param userId  用户 ID
     * @param keyword 搜索关键词
     * @return 匹配的会话列表
     */
    public List<Conversation> searchConversations(Long userId, String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return conversationRepository.findByUserIdOrderByPinnedDescUpdatedAtDesc(userId);
        }
        return conversationRepository.searchByKeyword(userId, keyword);
    }

    /**
     * 切换会话置顶状态
     * 点击已置顶的会话则取消置顶，点击未置顶的则置顶
     *
     * @param conversationId 会话 ID
     * @return 更新后的会话
     */
    public Conversation togglePin(Long conversationId) {
        Conversation conv = getConversation(conversationId);
        conv.setPinned(!Boolean.TRUE.equals(conv.getPinned()));
        return conversationRepository.save(conv);
    }

    /**
     * 导出会话内容为 Markdown 格式
     *
     * @param conversationId 会话 ID
     * @return Markdown 格式的会话文本
     */
    public String exportConversation(Long conversationId) {
        Conversation conv = getConversation(conversationId);
        List<Message> messages = messageRepository
                .findByConversationIdOrderByCreatedAtAsc(conversationId);

        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
        StringBuilder sb = new StringBuilder();
        sb.append("# ").append(conv.getTitle()).append("\n\n");
        sb.append("导出时间: ").append(java.time.LocalDateTime.now().format(fmt)).append("\n\n");
        sb.append("---\n\n");

        for (Message msg : messages) {
            String role = "USER".equals(msg.getRole()) ? "**用户**" : "**AI 助手**";
            String time = msg.getCreatedAt() != null ? msg.getCreatedAt().format(fmt) : "";
            sb.append("### ").append(role).append(" ").append(time).append("\n\n");
            sb.append(msg.getContent()).append("\n\n");
        }

        return sb.toString();
    }

    private MessageDTO convertToDTO(Message message) {
        List<ReferenceDTO> refs = new ArrayList<>();
        if (message.getReferencesData() != null) {
            try {
                refs = objectMapper.readValue(message.getReferencesData(),
                        new TypeReference<List<ReferenceDTO>>() {});
            } catch (JsonProcessingException e) {
                // ignore
            }
        }

        return MessageDTO.builder()
                .id(message.getId())
                .role(message.getRole())
                .content(message.getContent())
                .references(refs.isEmpty() ? null : refs)
                .feedback(message.getFeedback())
                .createdAt(message.getCreatedAt())
                .build();
    }
}
