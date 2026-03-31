package com.example.agentruntime.mcp;

/**
 * MCP 连接状态。
 */
public enum McpConnectionState {
    DISCONNECTED,
    CONNECTING,
    INITIALIZED,
    ERROR,
    CLOSED
}
