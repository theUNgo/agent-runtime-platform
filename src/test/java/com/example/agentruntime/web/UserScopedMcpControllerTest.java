package com.example.agentruntime.web;

import com.example.agentruntime.auth.AuthenticatedUser;
import com.example.agentruntime.auth.CurrentUserService;
import com.example.agentruntime.catalog.CatalogInstallMode;
import com.example.agentruntime.catalog.CatalogInstallResult;
import com.example.agentruntime.catalog.CatalogItemType;
import com.example.agentruntime.catalog.UnifiedCatalogService;
import com.example.agentruntime.mcp.McpCapabilitySnapshot;
import com.example.agentruntime.mcp.McpConnectionState;
import com.example.agentruntime.mcp.McpServerStatus;
import com.example.agentruntime.mcp.McpTransportType;
import com.example.agentruntime.mcp.McpValidationReport;
import com.example.agentruntime.mcp.UserMcpInstallationUpdateRequest;
import com.example.agentruntime.mcp.UserScopedMcpCapabilityService;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UserScopedMcpControllerTest {

    @Test
    void shouldValidateUserScopedServer() {
        CurrentUserService currentUserService = mock(CurrentUserService.class);
        UserScopedMcpCapabilityService capabilityService = mock(UserScopedMcpCapabilityService.class);
        UnifiedCatalogService catalogService = mock(UnifiedCatalogService.class);
        when(currentUserService.requireUser()).thenReturn(new AuthenticatedUser(1L, "demo", "Demo"));
        when(capabilityService.validate(any(), eq("demo-server"))).thenReturn(new McpValidationReport(
                "demo-server",
                McpTransportType.STDIO,
                true,
                true,
                true,
                "2025-11-25",
                "stdio: demo",
                null,
                new McpCapabilitySnapshot(true, false, false, false, false),
                1,
                0,
                0,
                Map.of("initialize", "ok"),
                List.of(),
                "ok",
                OffsetDateTime.now()
        ));

        UserScopedMcpController controller = new UserScopedMcpController(currentUserService, capabilityService, catalogService);

        McpValidationReport report = controller.validate("demo-server");

        assertEquals("demo-server", report.serverName());
        assertEquals("2025-11-25", report.protocolVersion());
    }

    @Test
    void shouldUpdateInstallationByDelegatingToCatalogService() {
        CurrentUserService currentUserService = mock(CurrentUserService.class);
        UserScopedMcpCapabilityService capabilityService = mock(UserScopedMcpCapabilityService.class);
        UnifiedCatalogService catalogService = mock(UnifiedCatalogService.class);
        when(catalogService.installMcpServer(any())).thenReturn(new CatalogInstallResult(
                "filesystem-local",
                CatalogItemType.MCP_SERVER,
                true,
                CatalogInstallMode.DIRECT,
                "ok",
                List.of("refresh"),
                null
        ));

        UserScopedMcpController controller = new UserScopedMcpController(currentUserService, capabilityService, catalogService);

        CatalogInstallResult result = controller.updateInstallation(
                "filesystem-local",
                new UserMcpInstallationUpdateRequest("workspace-files", Map.of("rootPath", "D:/AI project"))
        );

        assertEquals("filesystem-local", result.itemId());
        assertEquals(CatalogItemType.MCP_SERVER, result.itemType());
    }
}
