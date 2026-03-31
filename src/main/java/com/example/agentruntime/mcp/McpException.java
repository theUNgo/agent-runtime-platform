package com.example.agentruntime.mcp;

/**
 * MCP 宿主层统一异常。
 */
public class McpException extends RuntimeException {

    private final McpErrorCode code;

    public McpException(McpErrorCode code, String message) {
        super(message);
        this.code = code;
    }

    public McpException(McpErrorCode code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public McpErrorCode getCode() {
        return code;
    }
}
