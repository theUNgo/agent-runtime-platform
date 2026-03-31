package com.example.agentruntime.catalog;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * 内置 MCP Server 模板。
 */
record CuratedMcpServerTemplate(
        String id,
        String name,
        String version,
        String description,
        boolean requiresManualConfiguration,
        String transport,
        String command,
        List<String> args,
        String url,
        Map<String, String> headers,
        Duration timeout,
        List<String> tags,
        List<CatalogInstallField> installFields
) {
}
