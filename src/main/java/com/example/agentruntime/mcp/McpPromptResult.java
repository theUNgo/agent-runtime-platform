package com.example.agentruntime.mcp;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Prompt 读取结果。
 */
public record McpPromptResult(
        String description,
        JsonNode messages
) {
}
