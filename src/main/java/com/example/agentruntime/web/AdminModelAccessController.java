package com.example.agentruntime.web;

import com.example.agentruntime.auth.CurrentUserService;
import com.example.agentruntime.model.AdminModelAccessGrantRequest;
import com.example.agentruntime.model.AdminModelAccessRequestView;
import com.example.agentruntime.model.AdminModelAccessReviewRequest;
import com.example.agentruntime.model.ModelAccessStatus;
import com.example.agentruntime.model.UserModelProfileService;
import com.example.agentruntime.model.UserModelProfileView;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 管理员侧的模型访问审批接口。
 */
@RestController
@RequestMapping("/api/admin/model-access")
public class AdminModelAccessController {

    private final CurrentUserService currentUserService;
    private final UserModelProfileService userModelProfileService;

    public AdminModelAccessController(CurrentUserService currentUserService,
                                      UserModelProfileService userModelProfileService) {
        this.currentUserService = currentUserService;
        this.userModelProfileService = userModelProfileService;
    }

    @GetMapping("/requests")
    public List<AdminModelAccessRequestView> requests(@RequestParam(value = "status", required = false) ModelAccessStatus status) {
        currentUserService.requireAdmin();
        return userModelProfileService.listAccessRequests(status);
    }

    @PostMapping("/grants")
    public UserModelProfileView grant(@RequestBody AdminModelAccessGrantRequest request) {
        var admin = currentUserService.requireAdmin();
        return userModelProfileService.grant(admin.id(), request);
    }

    @PostMapping("/requests/{requestId}/approve")
    public AdminModelAccessRequestView approve(@PathVariable("requestId") Long requestId,
                                               @RequestBody(required = false) AdminModelAccessReviewRequest request) {
        var admin = currentUserService.requireAdmin();
        AdminModelAccessReviewRequest payload = request == null
                ? new AdminModelAccessReviewRequest(null, false)
                : request;
        return userModelProfileService.approveRequest(admin.id(), requestId, payload);
    }

    @PostMapping("/requests/{requestId}/reject")
    public AdminModelAccessRequestView reject(@PathVariable("requestId") Long requestId,
                                              @RequestBody(required = false) AdminModelAccessReviewRequest request) {
        var admin = currentUserService.requireAdmin();
        AdminModelAccessReviewRequest payload = request == null
                ? new AdminModelAccessReviewRequest(null, false)
                : request;
        return userModelProfileService.rejectRequest(admin.id(), requestId, payload);
    }
}
