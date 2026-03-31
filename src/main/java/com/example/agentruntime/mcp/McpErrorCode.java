package com.example.agentruntime.mcp;

/**
 * MCP 错误分类。
 * 对外会被映射为稳定的 API 错误码，便于前端和调用方做分流处理。
 */
public enum McpErrorCode {
    MCP_TRANSPORT_ERROR,
    MCP_PROTOCOL_ERROR,
    MCP_CAPABILITY_UNSUPPORTED,
    MCP_TIMEOUT
}
