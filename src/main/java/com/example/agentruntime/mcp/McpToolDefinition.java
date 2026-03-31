package com.example.agentruntime.mcp;

import com.fasterxml.jackson.databind.JsonNode;

public record McpToolDefinition(
        String name,
        String title,
        String description,
        JsonNode inputSchema,
        JsonNode outputSchema
) {
}
