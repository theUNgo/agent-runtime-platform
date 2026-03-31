package com.example.agentruntime.web;

import com.example.agentruntime.auth.AuthenticatedUser;
import com.example.agentruntime.auth.CurrentUserService;
import com.example.agentruntime.template.PreviewTemplateUpsertRequest;
import com.example.agentruntime.template.PreviewTemplateView;
import com.example.agentruntime.template.UserPreviewTemplateService;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UserPreviewTemplateControllerTest {

    @Test
    void shouldDelegateListAndSave() {
        CurrentUserService currentUserService = new StubCurrentUserService();
        UserPreviewTemplateService service = mock(UserPreviewTemplateService.class);

        when(service.listForUser(1L)).thenReturn(List.of(new PreviewTemplateView(
                "custom-1",
                "模板 A",
                "说明",
                "我的模板",
                "消息",
                "系统提示词",
                "[]",
                "字段说明",
                "{}",
                true,
                List.of("来源：测试"),
                Instant.now(),
                Instant.now()
        )));
        when(service.save(eq(1L), any(PreviewTemplateUpsertRequest.class))).thenReturn(new PreviewTemplateView(
                "custom-2",
                "模板 B",
                "说明",
                "我的模板",
                "消息",
                "系统提示词",
                "[]",
                "字段说明",
                "{}",
                false,
                List.of(),
                Instant.now(),
                Instant.now()
        ));

        UserPreviewTemplateController controller = new UserPreviewTemplateController(currentUserService, service);
        assertEquals(1, controller.list().size());
        PreviewTemplateView saved = controller.save("custom-2", new PreviewTemplateUpsertRequest(
                "ignored",
                "模板 B",
                "说明",
                "消息",
                "系统提示词",
                "[]",
                "字段说明",
                "{}",
                false,
                List.of()
        ));
        assertEquals("custom-2", saved.templateKey());
    }

    /**
     * 控制器单测只需要一个固定用户，用简单桩对象代替完整认证上下文。
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
