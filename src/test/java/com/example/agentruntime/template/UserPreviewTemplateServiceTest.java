package com.example.agentruntime.template;

import com.example.agentruntime.i18n.MessageService;
import com.example.agentruntime.persistence.entity.UserAccountEntity;
import com.example.agentruntime.persistence.entity.UserPreviewTemplateEntity;
import com.example.agentruntime.persistence.repository.UserAccountRepository;
import com.example.agentruntime.persistence.repository.UserPreviewTemplateRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.StaticMessageSource;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UserPreviewTemplateServiceTest {

    @Test
    void shouldSaveAndMapTemplate() {
        UserPreviewTemplateRepository repository = mock(UserPreviewTemplateRepository.class);
        UserAccountRepository userAccountRepository = mock(UserAccountRepository.class);
        UserAccountEntity user = new UserAccountEntity();
        user.setUsername("tester");
        user.setDisplayName("Tester");
        user.setPasswordHash("hashed");

        when(userAccountRepository.findById(1L)).thenReturn(Optional.of(user));
        when(repository.findByUserAndTemplateKey(user, "custom-1")).thenReturn(Optional.empty());
        when(repository.save(any(UserPreviewTemplateEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UserPreviewTemplateService service = new UserPreviewTemplateService(
                repository,
                userAccountRepository,
                new ObjectMapper(),
                messageService()
        );

        PreviewTemplateView view = service.save(1L, new PreviewTemplateUpsertRequest(
                "custom-1",
                "我的模板",
                "说明",
                "请帮我提取字段",
                "你是结构化助手",
                "[{\"role\":\"assistant\",\"content\":\"示例\"}]",
                "字段：title、summary",
                "{\"temperature\":0.1}",
                true,
                List.of("来源：测试")
        ));

        assertEquals("custom-1", view.templateKey());
        assertEquals("我的模板", view.label());
        assertEquals("我的模板", view.label());
        assertEquals(true, view.thinkingEnabled());
        assertEquals("我的模板", view.label());
    }

    @Test
    void shouldRejectBlankLabel() {
        UserPreviewTemplateRepository repository = mock(UserPreviewTemplateRepository.class);
        UserAccountRepository userAccountRepository = mock(UserAccountRepository.class);
        UserAccountEntity user = new UserAccountEntity();
        user.setUsername("tester");
        user.setDisplayName("Tester");
        user.setPasswordHash("hashed");
        when(userAccountRepository.findById(1L)).thenReturn(Optional.of(user));

        UserPreviewTemplateService service = new UserPreviewTemplateService(
                repository,
                userAccountRepository,
                new ObjectMapper(),
                messageService()
        );

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                service.save(1L, new PreviewTemplateUpsertRequest(
                        "custom-1",
                        " ",
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        false,
                        List.of()
                )));

        assertEquals("label required", exception.getMessage());
    }

    private MessageService messageService() {
        StaticMessageSource source = new StaticMessageSource();
        source.addMessage("auth.error.userNotFound", Locale.ENGLISH, "user missing");
        source.addMessage("template.preview.keyRequired", Locale.ENGLISH, "key required");
        source.addMessage("template.preview.labelRequired", Locale.ENGLISH, "label required");
        source.addMessage("template.preview.notFound", Locale.ENGLISH, "template missing");
        source.addMessage("template.preview.category.custom", Locale.ENGLISH, "My Templates");
        source.addMessage("template.preview.description.default", Locale.ENGLISH, "custom template");
        source.addMessage("template.preview.notesSerializeFailed", Locale.ENGLISH, "notes serialize failed");
        source.addMessage("template.preview.notesDeserializeFailed", Locale.ENGLISH, "notes deserialize failed");
        return new MessageService(source);
    }
}
