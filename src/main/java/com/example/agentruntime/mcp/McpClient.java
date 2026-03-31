package com.example.agentruntime.mcp;

import com.example.agentruntime.AgentRuntimeProperties;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;
import java.util.Map;

/**
 * MCP Client 的宿主层抽象。
 * 上层只依赖这里定义的稳定接口，不直接依赖官方 SDK 类型。
 */
public interface McpClient extends AutoCloseable {

    AgentRuntimeProperties.McpServerProperties server();

    McpTransportType transportType();

    McpServerStatus initialize();

    McpServerStatus health();

    McpServerInfo serverInfo();

    McpCapabilitySnapshot serverCapabilities();

    List<McpToolDefinition> listTools();

    McpCallResult callTool(String toolName, JsonNode input);

    List<McpResourceDefinition> listResources();

    JsonNode readResource(String uri);

    List<McpPromptDefinition> listPrompts();

    McpPromptResult getPrompt(String name, Map<String, Object> arguments);

    @Override
    default void close() {
    }
}
