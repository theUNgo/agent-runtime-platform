package com.example.agentruntime.web;

import com.example.agentruntime.i18n.MessageService;
import com.example.agentruntime.mcp.McpConnectionState;
import com.example.agentruntime.mcp.McpCapabilitySnapshot;
import com.example.agentruntime.mcp.McpClient;
import com.example.agentruntime.mcp.McpClientRegistry;
import com.example.agentruntime.mcp.McpException;
import com.example.agentruntime.mcp.McpServerStatus;
import com.example.agentruntime.mcp.McpValidationReport;
import com.example.agentruntime.mcp.McpValidationDiagnostics;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * MCP Server 联调验证接口。
 * 会主动执行一组轻量探针，帮助用户判断 server 当前是否已经可以被宿主稳定接入。
 */
@RestController
@RequestMapping("/api/mcp/servers/{serverName}")
public class McpValidationController {

    private final McpClientRegistry clientRegistry;
    private final MessageService messageService;

    public McpValidationController(McpClientRegistry clientRegistry, MessageService messageService) {
        this.clientRegistry = clientRegistry;
        this.messageService = messageService;
    }

    @PostMapping("/validate")
    public McpValidationReport validate(@PathVariable("serverName") String serverName) {
        var configuredServer = clientRegistry.findConfiguredServer(serverName)
                .orElseThrow(() -> new IllegalArgumentException(messageService.get("mcp.error.serverNotFound", serverName)));
        var existingStatus = clientRegistry.findStatus(serverName).orElse(null);
        var clientOptional = clientRegistry.find(serverName);

        if (clientOptional.isEmpty()) {
            McpServerStatus failedStatus = existingStatus != null
                    ? existingStatus
                    : new McpServerStatus(
                    serverName,
                    com.example.agentruntime.mcp.McpTransportType.from(configuredServer.transport(), messageService),
                    McpConnectionState.ERROR,
                    false,
                    null,
                    null,
                    new McpCapabilitySnapshot(false, false, false, false, false),
                    messageService.get("mcp.validation.summary.partial"),
                    messageService.get("mcp.validation.check.initFailed")
            );
            Map<String, String> failedChecks = new LinkedHashMap<>();
            failedChecks.put("initialize", failedStatus.lastError() == null || failedStatus.lastError().isBlank()
                    ? messageService.get("mcp.validation.check.initFailed")
                    : failedStatus.lastError());
            return new McpValidationReport(
                    serverName,
                    failedStatus.transportType(),
                    true,
                    false,
                    false,
                    failedStatus.protocolVersion(),
                    McpValidationDiagnostics.describeConfiguration(configuredServer),
                    failedStatus.serverInfo(),
                    failedStatus.capabilities(),
                    0,
                    0,
                    0,
                    failedChecks,
                    McpValidationDiagnostics.diagnose(configuredServer, failedStatus, messageService),
                    messageService.get("mcp.validation.summary.partial"),
                    OffsetDateTime.now()
            );
        }

        McpClient client = clientOptional.get();

        McpServerStatus status = client.initialize();
        Map<String, String> checks = new LinkedHashMap<>();
        checks.put("initialize", messageService.get("mcp.validation.check.ok"));

        int toolCount = probeTools(client, checks);
        int resourceCount = probeResources(client, checks);
        int promptCount = probePrompts(client, checks);

        boolean reachable = status.initialized();
        String summary = summarize(checks);

        return new McpValidationReport(
                status.serverName(),
                status.transportType(),
                true,
                reachable,
                status.initialized(),
                status.protocolVersion(),
                McpValidationDiagnostics.describeConfiguration(configuredServer),
                status.serverInfo(),
                status.capabilities(),
                toolCount,
                resourceCount,
                promptCount,
                checks,
                McpValidationDiagnostics.diagnose(configuredServer, status, messageService),
                summary,
                OffsetDateTime.now()
        );
    }

    private int probeTools(McpClient client, Map<String, String> checks) {
        McpCapabilitySnapshot capabilities = client.serverCapabilities();
        if (!capabilities.toolsSupported()) {
            checks.put("tools", messageService.get("mcp.validation.check.unsupported"));
            return 0;
        }
        try {
            int count = client.listTools().size();
            checks.put("tools", messageService.get("mcp.validation.check.okCount", count));
            return count;
        } catch (RuntimeException exception) {
            checks.put("tools", extractMessage(exception));
            return 0;
        }
    }

    private int probeResources(McpClient client, Map<String, String> checks) {
        McpCapabilitySnapshot capabilities = client.serverCapabilities();
        if (!capabilities.resourcesSupported()) {
            checks.put("resources", messageService.get("mcp.validation.check.unsupported"));
            return 0;
        }
        try {
            int count = client.listResources().size();
            checks.put("resources", messageService.get("mcp.validation.check.okCount", count));
            return count;
        } catch (RuntimeException exception) {
            checks.put("resources", extractMessage(exception));
            return 0;
        }
    }

    private int probePrompts(McpClient client, Map<String, String> checks) {
        McpCapabilitySnapshot capabilities = client.serverCapabilities();
        if (!capabilities.promptsSupported()) {
            checks.put("prompts", messageService.get("mcp.validation.check.unsupported"));
            return 0;
        }
        try {
            int count = client.listPrompts().size();
            checks.put("prompts", messageService.get("mcp.validation.check.okCount", count));
            return count;
        } catch (RuntimeException exception) {
            checks.put("prompts", extractMessage(exception));
            return 0;
        }
    }

    private String summarize(Map<String, String> checks) {
        boolean hasFailure = checks.values().stream()
                .anyMatch(value -> !value.equals(messageService.get("mcp.validation.check.ok"))
                        && !value.startsWith(messageService.get("mcp.validation.check.okPrefix"))
                        && !value.equals(messageService.get("mcp.validation.check.unsupported")));
        if (hasFailure) {
            return messageService.get("mcp.validation.summary.partial");
        }
        return messageService.get("mcp.validation.summary.ok");
    }

    private String extractMessage(RuntimeException exception) {
        if (exception instanceof McpException mcpException) {
            return mcpException.getMessage();
        }
        return exception.getMessage() == null || exception.getMessage().isBlank()
                ? messageService.get("error.internal")
                : exception.getMessage();
    }
}
