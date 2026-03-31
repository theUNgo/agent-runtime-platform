package com.example.agentruntime.model;

import com.example.agentruntime.auth.AuthenticatedUser;
import com.example.agentruntime.i18n.MessageService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.StaticMessageSource;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ModelInvocationServiceTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Test
    void shouldPreviewProfileWithInputPayload() throws Exception {
        UserModelProfileService profileService = mock(UserModelProfileService.class);
        ModelRuntimeProfile profile = new ModelRuntimeProfile(
                9L,
                "Vision",
                "openai-compatible",
                "https://example.com/v1",
                "secret",
                "glm-4.6v",
                true,
                true,
                false,
                true,
                true,
                true,
                true
        );
        when(profileService.runtimeProfile(1L, 9L)).thenReturn(Optional.of(profile));

        CapturingProviderClient client = new CapturingProviderClient();
        ModelInvocationService service = new ModelInvocationService(profileService, List.of(client), messageService());

        JsonNode input = OBJECT_MAPPER.readTree("""
                {
                  "imageUrls": ["https://example.com/demo.png"],
                  "thinking": {"type": "enabled"}
                }
                """);
        JsonNode examples = OBJECT_MAPPER.readTree("""
                [
                  {
                    "role": "assistant",
                    "content": "这是一个 few-shot 示例。"
                  }
                ]
                """);

        ModelPreviewResponse response = service.previewProfile(
                new AuthenticatedUser(1L, "tester", "Tester"),
                9L,
                new ModelPreviewRequest("帮我描述图片内容", "你是一个视觉理解助手。", examples, input)
        );

        assertEquals("glm-4.6v", response.modelId());
        assertEquals("预览成功", response.content());
        assertEquals("stop", response.finishReason());
        assertEquals("帮我描述图片内容", client.lastRequest().userMessage());
        assertEquals("你是一个视觉理解助手。", client.lastRequest().systemPrompt());
        assertEquals("assistant", client.lastRequest().examples().get(0).path("role").asText());
        assertNotNull(client.lastRequest().input());
        assertEquals("https://example.com/demo.png", client.lastRequest().input().path("imageUrls").get(0).asText());
    }

    @Test
    void shouldRejectBlankPreviewMessage() {
        UserModelProfileService profileService = mock(UserModelProfileService.class);
        ModelInvocationService service = new ModelInvocationService(profileService, List.of(new CapturingProviderClient()), messageService());

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.previewProfile(
                        new AuthenticatedUser(1L, "tester", "Tester"),
                        9L,
                        new ModelPreviewRequest("   ", null, null, null)
                )
        );

        assertEquals("preview required", exception.getMessage());
    }

    private MessageService messageService() {
        StaticMessageSource source = new StaticMessageSource();
        source.addMessage("model.error.activeProfileMissing", Locale.ENGLISH, "active profile missing");
        source.addMessage("model.error.profileNotFound", Locale.ENGLISH, "profile not found");
        source.addMessage("model.error.providerUnsupported", Locale.ENGLISH, "unsupported provider");
        source.addMessage("model.error.previewMessageRequired", Locale.ENGLISH, "preview required");
        source.addMessage("model.error.previewExamplesArray", Locale.ENGLISH, "examples must be an array");
        source.addMessage("model.error.previewExampleInvalid", Locale.ENGLISH, "example item invalid");
        source.addMessage("model.preview.systemPrompt", Locale.ENGLISH, "preview prompt");
        source.addMessage("model.validation.success", Locale.ENGLISH, "validation success");
        return new MessageService(source);
    }

    /**
     * 使用最小假实现记录服务层下发给 provider 的请求，避免把测试耦合到具体 HTTP 客户端。
     */
    private static final class CapturingProviderClient implements ModelProviderClient {

        private ModelChatRequest lastRequest;

        @Override
        public boolean supports(String providerType) {
            return "openai-compatible".equals(providerType);
        }

        @Override
        public ModelChatResponse chat(ModelRuntimeProfile profile, ModelChatRequest request) {
            this.lastRequest = request;
            return new ModelChatResponse(
                    "预览成功",
                    "stop",
                    profile.providerType(),
                    profile.modelId(),
                    OBJECT_MAPPER.createObjectNode().put("ok", true)
            );
        }

        private ModelChatRequest lastRequest() {
            return lastRequest;
        }
    }
}
