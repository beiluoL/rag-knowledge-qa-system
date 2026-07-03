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
     * 获取用户的会话列表
     */
    public List<Conversation> getUserConversations(Long userId) {
        return conversationRepository.findByUserIdOrderByUpdatedAtDesc(userId);
    }

    /**
     * 获取会话详情（含消息）
     */
    public Conversation getConversation(Long conversationId) {
        return conversationRepository.findById(conversationId)
                .orElseThrow(() -> new BusinessException("会话不存在"));
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
                .createdAt(message.getCreatedAt())
                .build();
    }
}
