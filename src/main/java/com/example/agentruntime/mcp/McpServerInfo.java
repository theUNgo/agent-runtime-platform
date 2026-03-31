package com.example.agentruntime.mcp;

/**
 * MCP Server 的基础信息。
 */
public record McpServerInfo(
        String name,
        String title,
        String version,
        String instructions
) {
}
