package com.example.agentruntime.conversation;

import com.example.agentruntime.i18n.MessageService;
import com.example.agentruntime.persistence.entity.AgentConversationEntity;
import com.example.agentruntime.persistence.entity.UserAccountEntity;
import com.example.agentruntime.persistence.repository.AgentConversationRepository;
import com.example.agentruntime.persistence.repository.AgentMessageRepository;
import com.example.agentruntime.persistence.repository.UserAccountRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.support.StaticMessageSource;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConversationHistoryServiceTest {

    @Mock
    private AgentConversationRepository conversationRepository;

    @Mock
    private AgentMessageRepository messageRepository;

    @Mock
    private UserAccountRepository userAccountRepository;

    @Test
    void shouldCreateConversationWithRememberedModelProfile() {
        UserAccountEntity user = new UserAccountEntity();
        setId(user, 1L);
        when(userAccountRepository.findById(1L)).thenReturn(Optional.of(user));
        when(conversationRepository.save(any(AgentConversationEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ConversationHistoryService service = new ConversationHistoryService(
                conversationRepository,
                messageRepository,
                userAccountRepository,
                messageService()
        );

        AgentConversationEntity conversation = service.ensureConversation(1L, null, "测试会话", 88L);

        assertEquals(88L, conversation.getModelProfileId());
        assertEquals("测试会话", conversation.getTitle());
    }

    @Test
    void shouldUpdateExistingConversationModelWhenExplicitOverrideProvided() {
        UserAccountEntity user = new UserAccountEntity();
        setId(user, 1L);
        AgentConversationEntity conversation = new AgentConversationEntity();
        conversation.setUser(user);
        conversation.setConversationId("c-1");
        conversation.setTitle("已有会话");
        conversation.setModelProfileId(10L);

        when(userAccountRepository.findById(1L)).thenReturn(Optional.of(user));
        when(conversationRepository.findByConversationId("c-1")).thenReturn(Optional.of(conversation));
        when(conversationRepository.save(any(AgentConversationEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ConversationHistoryService service = new ConversationHistoryService(
                conversationRepository,
                messageRepository,
                userAccountRepository,
                messageService()
        );

        AgentConversationEntity updated = service.ensureConversation(1L, "c-1", "忽略", 99L);

        assertSame(conversation, updated);
        assertEquals(99L, updated.getModelProfileId());
    }

    @Test
    void shouldExposeRememberedModelProfileInConversationSummary() {
        UserAccountEntity user = new UserAccountEntity();
        setId(user, 2L);
        AgentConversationEntity rememberedConversation = new AgentConversationEntity();
        rememberedConversation.setUser(user);
        rememberedConversation.setConversationId("c-2");
        rememberedConversation.setTitle("带模型会话");
        rememberedConversation.setModelProfileId(66L);
        setUpdatedAt(rememberedConversation, Instant.parse("2026-03-31T03:00:00Z"));

        AgentConversationEntity plainConversation = new AgentConversationEntity();
        plainConversation.setUser(user);
        plainConversation.setConversationId("c-3");
        plainConversation.setTitle("普通会话");
        plainConversation.setModelProfileId(null);
        setUpdatedAt(plainConversation, Instant.parse("2026-03-31T04:00:00Z"));

        when(userAccountRepository.findById(2L)).thenReturn(Optional.of(user));
        when(conversationRepository.findByUserOrderByIdDesc(user)).thenReturn(List.of(rememberedConversation, plainConversation));

        ConversationHistoryService service = new ConversationHistoryService(
                conversationRepository,
                messageRepository,
                userAccountRepository,
                messageService()
        );

        List<ConversationSummary> summaries = service.listConversations(2L);

        assertEquals(2, summaries.size());
        assertEquals(66L, summaries.get(0).modelProfileId());
        assertNull(summaries.get(1).modelProfileId());
    }

    private void setId(UserAccountEntity entity, Long id) {
        try {
            var field = UserAccountEntity.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(entity, id);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private void setUpdatedAt(AgentConversationEntity entity, Instant value) {
        try {
            var field = AgentConversationEntity.class.getDeclaredField("updatedAt");
            field.setAccessible(true);
            field.set(entity, value);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private MessageService messageService() {
        StaticMessageSource source = new StaticMessageSource();
        source.addMessage("auth.error.userNotFound", Locale.ENGLISH, "user not found");
        source.addMessage("conversation.error.notFound", Locale.ENGLISH, "conversation not found");
        source.addMessage("conversation.title.default", Locale.ENGLISH, "New Conversation");
        return new MessageService(source);
    }
}
