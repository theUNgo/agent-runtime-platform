package com.example.agentruntime.mcp;

/**
 * 宿主层缓存的 server capability 摘要。
 */
public record McpCapabilitySnapshot(
        boolean toolsSupported,
        boolean resourcesSupported,
        boolean promptsSupported,
        boolean loggingSupported,
        boolean completionsSupported
) {
}
