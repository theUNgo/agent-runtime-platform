package com.example.agentruntime.mcp;

import java.time.OffsetDateTime;
import java.util.Map;

/**
 * MCP Server 联调验证报告。
 * 用于汇总一次宿主侧探针执行结果，帮助快速判断 server 是否已经具备接入条件。
 */
public record McpValidationReport(
        String serverName,
        McpTransportType transportType,
        boolean configured,
        boolean reachable,
        boolean initialized,
        String protocolVersion,
        String configurationSummary,
        McpServerInfo serverInfo,
        McpCapabilitySnapshot capabilities,
        int toolCount,
        int resourceCount,
        int promptCount,
        Map<String, String> checks,
        java.util.List<McpValidationDiagnostic> diagnostics,
        String summary,
        OffsetDateTime validatedAt
) {

    public McpValidationReport {
        checks = checks == null ? Map.of() : Map.copyOf(checks);
        diagnostics = diagnostics == null ? java.util.List.of() : java.util.List.copyOf(diagnostics);
        validatedAt = validatedAt == null ? OffsetDateTime.now() : validatedAt;
    }
}
