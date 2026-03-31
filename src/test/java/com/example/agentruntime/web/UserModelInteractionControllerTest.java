package com.example.agentruntime.web;

import com.example.agentruntime.auth.AuthenticatedUser;
import com.example.agentruntime.auth.CurrentUserService;
import com.example.agentruntime.model.ModelInvocationService;
import com.example.agentruntime.model.ModelPreviewRequest;
import com.example.agentruntime.model.ModelPreviewResponse;
import com.example.agentruntime.model.ModelValidationReport;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UserModelInteractionControllerTest {

    @Test
    void shouldDelegatePreviewToService() throws Exception {
        CurrentUserService currentUserService = new StubCurrentUserService();
        ModelInvocationService modelInvocationService = mock(ModelInvocationService.class);
        ObjectMapper objectMapper = new ObjectMapper();
        ModelPreviewRequest request = new ModelPreviewRequest(
                "请总结这张图",
                "你是一个视觉摘要助手。",
                objectMapper.readTree("""
                        [
                          {
                            "role": "assistant",
                            "content": "这是一个示例答案。"
                          }
                        ]
                        """),
                objectMapper.readTree("{\"imageUrls\":[\"https://example.com/demo.png\"]}")
        );
        when(modelInvocationService.previewProfile(any(), eq(5L), eq(request)))
                .thenReturn(new ModelPreviewResponse(5L, "Vision", "openai-compatible", "glm-4.6v", request.message(), "done", "stop", null));

        UserModelInteractionController controller = new UserModelInteractionController(currentUserService, modelInvocationService);
        ModelPreviewResponse response = controller.preview(5L, request);

        assertEquals("done", response.content());
        assertEquals("glm-4.6v", response.modelId());
    }

    @Test
    void shouldDelegateValidationToService() {
        CurrentUserService currentUserService = new StubCurrentUserService();
        ModelInvocationService modelInvocationService = mock(ModelInvocationService.class);
        when(modelInvocationService.validateProfile(any(), eq(6L)))
                .thenReturn(new ModelValidationReport(6L, "Primary", "openai-compatible", "glm-4.6v", true, "ok", "pong"));

        UserModelInteractionController controller = new UserModelInteractionController(currentUserService, modelInvocationService);
        ModelValidationReport response = controller.validate(6L);

        assertEquals("ok", response.summary());
        assertEquals("pong", response.preview());
    }

    /**
     * 控制器单元测试里只需要一个固定用户，因此用简单桩对象代替完整认证上下文。
     */
    private static final class StubCurrentUserService extends CurrentUserService {

        private StubCurrentUserService() {
            super(null);
        }

        @Override
        public AuthenticatedUser requireUser() {
            return new AuthenticatedUser(1L, "tester", "Tester");
        }
    }
}
