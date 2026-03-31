package com.example.agentruntime.mcp;

public record McpServerStatus(
        String serverName,
        McpTransportType transportType,
        McpConnectionState connectionState,
        boolean initialized,
        String protocolVersion,
        McpServerInfo serverInfo,
        McpCapabilitySnapshot capabilities,
        String message,
        String lastError
) {
}
