package com.example.agentruntime.audit;

import com.example.agentruntime.persistence.entity.CapabilityInvocationEntity;
import com.example.agentruntime.persistence.entity.UserAccountEntity;
import com.example.agentruntime.persistence.repository.CapabilityInvocationRepository;
import com.example.agentruntime.persistence.repository.UserAccountRepository;
import com.example.agentruntime.i18n.MessageService;
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
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CapabilityAuditQueryServiceTest {

    @Mock
    private CapabilityInvocationRepository invocationRepository;

    @Mock
    private UserAccountRepository userAccountRepository;

    @Test
    void shouldListAuditRecordsForConversation() {
        MessageService messageService = messageService();
        CapabilityAuditQueryService service = new CapabilityAuditQueryService(
                invocationRepository,
                userAccountRepository,
                messageService
        );

        UserAccountEntity user = new UserAccountEntity();
        CapabilityInvocationEntity entity = new CapabilityInvocationEntity();
        entity.setConversationId("conv-1");
        entity.setCapabilityId("skill:test");
        entity.setCapabilityName("Test Skill");
        entity.setCapabilityType("SKILL");
        entity.setProvider("catalog");
        entity.setStatus("SUCCESS");
        entity.setRequestJson("{\"message\":\"hi\"}");
        entity.setResultJson("{\"ok\":true}");
        entity.setTraceJson("{\"assistantMessageSource\":\"model\",\"model\":{\"modelId\":\"glm-4.6v\"}}");
        entity.setDurationMs(138L);

        Instant createdAt = Instant.parse("2026-03-31T02:00:00Z");
        setCreatedAt(entity, createdAt);

        when(userAccountRepository.findById(1L)).thenReturn(Optional.of(user));
        when(invocationRepository.findByUserAndConversationIdOrderByIdDesc(user, "conv-1"))
                .thenReturn(List.of(entity));

        List<CapabilityInvocationView> views = service.listForUser(1L, new CapabilityAuditQuery(
                "conv-1",
                null,
                null,
                null,
                null
        ));

        assertEquals(1, views.size());
        assertEquals("conv-1", views.getFirst().conversationId());
        assertEquals("Test Skill", views.getFirst().capabilityName());
        assertEquals("{\"assistantMessageSource\":\"model\",\"model\":{\"modelId\":\"glm-4.6v\"}}", views.getFirst().traceJson());
        assertEquals(138L, views.getFirst().durationMs());
        assertEquals(createdAt, views.getFirst().createdAt());
    }

    @Test
    void shouldFilterAuditRecordsByStatusAndKeyword() {
        MessageService messageService = messageService();
        CapabilityAuditQueryService service = new CapabilityAuditQueryService(
                invocationRepository,
                userAccountRepository,
                messageService
        );

        UserAccountEntity user = new UserAccountEntity();
        CapabilityInvocationEntity success = new CapabilityInvocationEntity();
        success.setCapabilityId("mcp-user:workspace:read_file");
        success.setCapabilityName("Read File");
        success.setCapabilityType("MCP_TOOL");
        success.setProvider("workspace");
        success.setStatus("SUCCESS");

        CapabilityInvocationEntity failure = new CapabilityInvocationEntity();
        failure.setCapabilityId("skill:api-doc-writer");
        failure.setCapabilityName("API Doc Writer");
        failure.setCapabilityType("SKILL");
        failure.setProvider("catalog");
        failure.setStatus("FAILED");
        failure.setErrorMessage("gateway timeout");

        when(userAccountRepository.findById(2L)).thenReturn(Optional.of(user));
        when(invocationRepository.findByUserOrderByIdDesc(user)).thenReturn(List.of(success, failure));

        List<CapabilityInvocationView> views = service.listForUser(2L, new CapabilityAuditQuery(
                null,
                "FAILED",
                "SKILL",
                "timeout",
                10
        ));

        assertEquals(1, views.size());
        assertEquals("API Doc Writer", views.getFirst().capabilityName());
        assertEquals("FAILED", views.getFirst().status());
    }

    private void setCreatedAt(CapabilityInvocationEntity entity, Instant createdAt) {
        try {
            var field = CapabilityInvocationEntity.class.getDeclaredField("createdAt");
            field.setAccessible(true);
            field.set(entity, createdAt);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private MessageService messageService() {
        StaticMessageSource source = new StaticMessageSource();
        source.addMessage("auth.error.userNotFound", Locale.ENGLISH, "User was not found.");
        return new MessageService(source);
    }
}
