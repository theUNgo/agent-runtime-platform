package com.example.agentruntime.mcp;

/**
 * MCP 联调诊断项。
 * 用于向前端或调用方返回更结构化的排障信息，而不是只给一段错误文本。
 */
public record McpValidationDiagnostic(
        String code,
        String severity,
        String message,
        String suggestion
) {
}
