package com.example.agentruntime.mcp;

/**
 * MCP Prompt 的宿主层稳定描述。
 */
public record McpPromptDefinition(
        String name,
        String title,
        String description
) {
}
