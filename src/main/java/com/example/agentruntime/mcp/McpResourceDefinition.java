package com.example.agentruntime.mcp;

/**
 * MCP Resource 的宿主层稳定描述。
 */
public record McpResourceDefinition(
        String uri,
        String name,
        String title,
        String description,
        String mimeType
) {
}
