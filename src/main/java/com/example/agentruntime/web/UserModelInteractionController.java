package com.example.agentruntime.web;

import com.example.agentruntime.auth.CurrentUserService;
import com.example.agentruntime.model.ModelInvocationService;
import com.example.agentruntime.model.ModelPreviewRequest;
import com.example.agentruntime.model.ModelPreviewResponse;
import com.example.agentruntime.model.ModelValidationReport;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户级模型交互入口。
 * 当前先提供“连通性验证”，后续可以继续扩展成模型预览、图文测试等能力。
 */
@RestController
@RequestMapping("/api/me/models")
public class UserModelInteractionController {

    private final CurrentUserService currentUserService;
    private final ModelInvocationService modelInvocationService;

    public UserModelInteractionController(CurrentUserService currentUserService,
                                          ModelInvocationService modelInvocationService) {
        this.currentUserService = currentUserService;
        this.modelInvocationService = modelInvocationService;
    }

    @PostMapping("/{profileId}/validate")
    public ModelValidationReport validate(@PathVariable("profileId") Long profileId) {
        return modelInvocationService.validateProfile(currentUserService.requireUser(), profileId);
    }

    /**
     * 对当前用户的指定模型档案执行一次即时试跑。
     * 该接口主要给前端管理台使用，方便做文本或图文验证。
     */
    @PostMapping("/{profileId}/preview")
    public ModelPreviewResponse preview(@PathVariable("profileId") Long profileId,
                                        @RequestBody ModelPreviewRequest request) {
        return modelInvocationService.previewProfile(currentUserService.requireUser(), profileId, request);
    }
}
