package com.example.agentruntime.catalog;

import jakarta.validation.constraints.NotBlank;

import java.util.Map;

/**
 * MCP 安装请求。
 * 除了模板 id 以外，还允许前端把用户填写的连接参数一起提交。
 */
public record McpCatalogInstallRequest(
        @NotBlank String itemId,
        String serverName,
        Map<String, String> config
) {

    public McpCatalogInstallRequest {
        config = config == null ? Map.of() : Map.copyOf(config);
    }
}
