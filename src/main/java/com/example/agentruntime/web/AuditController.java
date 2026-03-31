package com.example.agentruntime.web;

import com.example.agentruntime.audit.CapabilityAuditQuery;
import com.example.agentruntime.audit.CapabilityAuditQueryService;
import com.example.agentruntime.audit.CapabilityInvocationView;
import com.example.agentruntime.auth.CurrentUserService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 当前用户的能力调用审计接口。
 */
@RestController
@RequestMapping("/api/me/audit")
public class AuditController {

    private final CurrentUserService currentUserService;
    private final CapabilityAuditQueryService capabilityAuditQueryService;

    public AuditController(CurrentUserService currentUserService,
                           CapabilityAuditQueryService capabilityAuditQueryService) {
        this.currentUserService = currentUserService;
        this.capabilityAuditQueryService = capabilityAuditQueryService;
    }

    @GetMapping
    public List<CapabilityInvocationView> list(@RequestParam(value = "conversationId", required = false) String conversationId,
                                               @RequestParam(value = "status", required = false) String status,
                                               @RequestParam(value = "capabilityType", required = false) String capabilityType,
                                               @RequestParam(value = "keyword", required = false) String keyword,
                                               @RequestParam(value = "limit", required = false) Integer limit) {
        var user = currentUserService.requireUser();
        return capabilityAuditQueryService.listForUser(user.id(), new CapabilityAuditQuery(
                conversationId,
                status,
                capabilityType,
                keyword,
                limit
        ));
    }
}