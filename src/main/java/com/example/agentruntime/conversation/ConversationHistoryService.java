package com.example.agentruntime.conversation;

import com.example.agentruntime.i18n.MessageService;
import com.example.agentruntime.persistence.entity.AgentConversationEntity;
import com.example.agentruntime.persistence.entity.AgentMessageEntity;
import com.example.agentruntime.persistence.entity.UserAccountEntity;
import com.example.agentruntime.persistence.repository.AgentConversationRepository;
import com.example.agentruntime.persistence.repository.AgentMessageRepository;
import com.example.agentruntime.persistence.repository.UserAccountRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * 会话与消息持久化服务。
 */
@Service
public class ConversationHistoryService {

    private final AgentConversationRepository conversationRepository;
    private final AgentMessageRepository messageRepository;
    private final UserAccountRepository userAccountRepository;
    private final MessageService messageService;

    public ConversationHistoryService(AgentConversationRepository conversationRepository,
                                      AgentMessageRepository messageRepository,
                                      UserAccountRepository userAccountRepository,
                                      MessageService messageService) {
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
        this.userAccountRepository = userAccountRepository;
        this.messageService = messageService;
    }

    @Transactional
    public AgentConversationEntity ensureConversation(Long userId,
                                                      String conversationId,
                                                      String seedMessage,
                                                      Long modelProfileId) {
        UserAccountEntity user = userAccountRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException(messageService.get("auth.error.userNotFound")));
        if (conversationId != null && !conversationId.isBlank()) {
            AgentConversationEntity conversation = conversationRepository.findByConversationId(conversationId)
                    .filter(existing -> existing.getUser().getId().equals(userId))
                    .orElseThrow(() -> new IllegalArgumentException(messageService.get("conversation.error.notFound")));
            if (modelProfileId != null && !modelProfileId.equals(conversation.getModelProfileId())) {
                conversation.setModelProfileId(modelProfileId);
                conversation = conversationRepository.save(conversation);
            }
            return conversation;
        }
        AgentConversationEntity conversation = new AgentConversationEntity();
        conversation.setConversationId(UUID.randomUUID().toString());
        conversation.setUser(user);
        conversation.setTitle(buildTitle(seedMessage));
        conversation.setModelProfileId(modelProfileId);
        return conversationRepository.save(conversation);
    }

    /**
     * 更新会话记忆的模型档案。
     * 主要用于首次试跑成功后开始对话，或会话中的模型临时覆盖切换。
     */
    @Transactional
    public AgentConversationEntity rememberModelProfile(AgentConversationEntity conversation, Long modelProfileId) {
        conversation.setModelProfileId(modelProfileId);
        return conversationRepository.save(conversation);
    }

    @Transactional
    public void appendMessage(AgentConversationEntity conversation, String role, String content, String payloadJson) {
        AgentMessageEntity message = new AgentMessageEntity();
        message.setConversation(conversation);
        message.setRole(role);
        message.setContent(content == null || content.isBlank() ? "-" : content);
        message.setPayloadJson(payloadJson);
        messageRepository.save(message);
        conversationRepository.save(conversation);
    }

    @Transactional(readOnly = true)
    public List<ConversationSummary> listConversations(Long userId) {
        UserAccountEntity user = userAccountRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException(messageService.get("auth.error.userNotFound")));
        return conversationRepository.findByUserOrderByIdDesc(user).stream()
                .map(conversation -> new ConversationSummary(
                        conversation.getConversationId(),
                        conversation.getTitle(),
                        conversation.getUpdatedAt(),
                        conversation.getModelProfileId()
                ))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ConversationMessageView> listMessages(Long userId, String conversationId) {
        AgentConversationEntity conversation = conversationRepository.findByConversationId(conversationId)
                .filter(existing -> existing.getUser().getId().equals(userId))
                .orElseThrow(() -> new IllegalArgumentException(messageService.get("conversation.error.notFound")));
        return messageRepository.findByConversationOrderByIdAsc(conversation).stream()
                .map(message -> new ConversationMessageView(
                        message.getId(),
                        message.getRole(),
                        message.getContent(),
                        message.getPayloadJson(),
                        message.getCreatedAt()
                ))
                .toList();
    }

    private String buildTitle(String seedMessage) {
        if (seedMessage == null || seedMessage.isBlank()) {
            return messageService.get("conversation.title.default");
        }
        String normalized = seedMessage.trim();
        return normalized.length() <= 48 ? normalized : normalized.substring(0, 48) + "...";
    }
}
