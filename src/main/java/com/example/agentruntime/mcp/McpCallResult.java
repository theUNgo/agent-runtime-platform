package com.example.agentruntime.mcp;

import com.fasterxml.jackson.databind.JsonNode;

public record McpCallResult(
        boolean success,
        String message,
        JsonNode output
) {
}
