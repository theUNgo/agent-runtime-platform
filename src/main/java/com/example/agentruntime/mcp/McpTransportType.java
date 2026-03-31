package com.example.agentruntime.mcp;

import com.example.agentruntime.i18n.MessageService;

import java.util.Locale;

public enum McpTransportType {
    STDIO,
    STREAMABLE_HTTP;

    /**
     * 将配置中的字符串传输类型转换成枚举。
     */
    public static McpTransportType from(String value, MessageService messageService) {
        if (value == null || value.isBlank()) {
            return STDIO;
        }
        return switch (value.trim().toUpperCase(Locale.ROOT).replace('-', '_')) {
            case "STDIO" -> STDIO;
            case "STREAMABLE_HTTP" -> STREAMABLE_HTTP;
            default -> throw new IllegalArgumentException(messageService.get("mcp.error.unsupportedTransport", value));
        };
    }
}
