package com.example.agentruntime.web;

import com.example.agentruntime.auth.CurrentUserService;
import com.example.agentruntime.template.PreviewTemplateUpsertRequest;
import com.example.agentruntime.template.PreviewTemplateView;
import com.example.agentruntime.template.UserPreviewTemplateService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 当前用户的自定义试跑模板接口。
 */
@RestController
@RequestMapping("/api/me/preview-templates")
public class UserPreviewTemplateController {

    private final CurrentUserService currentUserService;
    private final UserPreviewTemplateService userPreviewTemplateService;

    public UserPreviewTemplateController(CurrentUserService currentUserService,
                                         UserPreviewTemplateService userPreviewTemplateService) {
        this.currentUserService = currentUserService;
        this.userPreviewTemplateService = userPreviewTemplateService;
    }

    @GetMapping
    public List<PreviewTemplateView> list() {
        var user = currentUserService.requireUser();
        return userPreviewTemplateService.listForUser(user.id());
    }

    @PutMapping("/{templateKey}")
    public PreviewTemplateView save(@PathVariable("templateKey") String templateKey,
                                    @RequestBody PreviewTemplateUpsertRequest request) {
        var user = currentUserService.requireUser();
        PreviewTemplateUpsertRequest normalized = new PreviewTemplateUpsertRequest(
                templateKey,
                request.label(),
                request.description(),
                request.message(),
                request.systemPrompt(),
                request.examplesJson(),
                request.outputGuide(),
                request.payloadJson(),
                request.thinkingEnabled(),
                request.notes()
        );
        return userPreviewTemplateService.save(user.id(), normalized);
    }

    @DeleteMapping("/{templateKey}")
    public void delete(@PathVariable("templateKey") String templateKey) {
        var user = currentUserService.requireUser();
        userPreviewTemplateService.delete(user.id(), templateKey);
    }
}
