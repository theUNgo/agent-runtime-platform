package com.example.agentruntime.mcp;

import java.util.Map;

/**
 * 用户更新自己 MCP 安装参数的请求。
 */
public record UserMcpInstallationUpdateRequest(
        String serverName,
        Map<String, String> config
) {

    public UserMcpInstallationUpdateRequest {
        config = config == null ? Map.of() : Map.copyOf(config);
    }
}
