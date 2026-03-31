package com.example.agentruntime.web;

import com.example.agentruntime.auth.CurrentUserService;
import com.example.agentruntime.catalog.CatalogInstallResult;
import com.example.agentruntime.catalog.McpCatalogInstallRequest;
import com.example.agentruntime.catalog.UnifiedCatalogService;
import com.example.agentruntime.mcp.McpServerStatus;
import com.example.agentruntime.mcp.McpToolDefinition;
import com.example.agentruntime.mcp.McpValidationReport;
import com.example.agentruntime.mcp.UserMcpInstallationUpdateRequest;
import com.example.agentruntime.mcp.UserScopedMcpCapabilityService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 当前用户自己的 MCP 运行视图。
 */
@RestController
@RequestMapping("/api/me/mcp")
public class UserScopedMcpController {

    private final CurrentUserService currentUserService;
    private final UserScopedMcpCapabilityService userScopedMcpCapabilityService;
    private final UnifiedCatalogService unifiedCatalogService;

    public UserScopedMcpController(CurrentUserService currentUserService,
                                   UserScopedMcpCapabilityService userScopedMcpCapabilityService,
                                   UnifiedCatalogService unifiedCatalogService) {
        this.currentUserService = currentUserService;
        this.userScopedMcpCapabilityService = userScopedMcpCapabilityService;
        this.unifiedCatalogService = unifiedCatalogService;
    }

    @GetMapping("/servers")
    public List<McpServerStatus> listStatuses() {
        return userScopedMcpCapabilityService.listStatuses(currentUserService.requireUser());
    }

    @GetMapping("/servers/{serverName}/tools")
    public List<McpToolDefinition> listTools(@PathVariable("serverName") String serverName) {
        return userScopedMcpCapabilityService.listTools(currentUserService.requireUser(), serverName);
    }

    @PostMapping("/servers/{serverName}/validate")
    public McpValidationReport validate(@PathVariable("serverName") String serverName) {
        return userScopedMcpCapabilityService.validate(currentUserService.requireUser(), serverName);
    }

    @PutMapping("/installations/{itemId}")
    public CatalogInstallResult updateInstallation(@PathVariable("itemId") String itemId,
                                                   @RequestBody UserMcpInstallationUpdateRequest request) {
        return unifiedCatalogService.installMcpServer(new McpCatalogInstallRequest(
                itemId,
                request.serverName(),
                request.config()
        ));
    }
}