package com.example.agentruntime.mcp;

import com.example.agentruntime.AgentRuntimeProperties;
import com.example.agentruntime.i18n.MessageService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 旧版自研 skeleton client。
 * 当前已降级为 fallback/参考实现，主路径由官方 Java SDK 接管。
 */
@Deprecated(forRemoval = false)
public abstract class AbstractSkeletonMcpClient implements McpClient {

    protected final AgentRuntimeProperties.McpServerProperties server;
    protected final ObjectMapper objectMapper;
    protected final MessageService messageService;
    private final AtomicBoolean initialized = new AtomicBoolean(false);

    /**
     * MCP Client 的抽象骨架。
     * 这里封装了初始化、占位工具、占位调用结果等通用逻辑，方便后续替换成真实协议实现。
     */
    protected AbstractSkeletonMcpClient(AgentRuntimeProperties.McpServerProperties server,
                                        ObjectMapper objectMapper,
                                        MessageService messageService) {
        this.server = server;
        this.objectMapper = objectMapper;
        this.messageService = messageService;
    }

    @Override
    public AgentRuntimeProperties.McpServerProperties server() {
        return server;
    }

    @Override
    public McpServerStatus initialize() {
        initialized.set(true);
        return new McpServerStatus(
                server.name(),
                transportType(),
                McpConnectionState.INITIALIZED,
                true,
                null,
                null,
                new McpCapabilitySnapshot(false, false, false, false, false),
                initializationMessage(),
                null
        );
    }

    @Override
    public McpServerStatus health() {
        return initialize();
    }

    @Override
    public McpServerInfo serverInfo() {
        return null;
    }

    @Override
    public McpCapabilitySnapshot serverCapabilities() {
        return new McpCapabilitySnapshot(false, false, false, false, false);
    }

    @Override
    public List<McpToolDefinition> listTools() {
        if (!initialized.get()) {
            initialize();
        }
        return skeletonTools();
    }

    @Override
    public McpCallResult callTool(String toolName, JsonNode input) {
        if (!initialized.get()) {
            initialize();
        }
        ObjectNode payload = JsonNodeFactory.instance.objectNode();
        payload.put("server", server.name());
        payload.put("transport", transportType().name());
        payload.put("tool", toolName);
        payload.put("implemented", false);
        payload.put("nextStep", implementationHint());
        payload.set("input", input == null ? objectMapper.createObjectNode() : input);
        return new McpCallResult(true, messageService.get("capability.mcp.call.success"), payload);
    }

    protected List<McpToolDefinition> skeletonTools() {
        return List.of(
                new McpToolDefinition(
                        messageService.get("capability.mcp.tool.connectionProbe.name"),
                        null,
                        messageService.get("capability.mcp.tool.connectionProbe.description"),
                        probeSchema(),
                        null),
                new McpToolDefinition(
                        messageService.get("capability.mcp.tool.rawCall.name"),
                        null,
                        messageService.get("capability.mcp.tool.rawCall.description"),
                        rawCallSchema(),
                        null)
        );
    }

    protected JsonNode probeSchema() {
        ObjectNode schema = objectMapper.createObjectNode();
        schema.put("type", "object");
        schema.putArray("required");
        schema.set("properties", objectMapper.createObjectNode());
        return schema;
    }

    protected JsonNode rawCallSchema() {
        ObjectNode schema = objectMapper.createObjectNode();
        schema.put("type", "object");
        schema.putArray("required").add("method");
        ObjectNode properties = objectMapper.createObjectNode();
        properties.set("method", stringProperty(messageService.get("capability.mcp.tool.rawCall.method.description")));
        properties.set("arguments", objectMapper.createObjectNode().put("type", "object"));
        schema.set("properties", properties);
        return schema;
    }

    protected ObjectNode stringProperty(String description) {
        return objectMapper.createObjectNode()
                .put("type", "string")
                .put("description", description);
    }

    protected abstract String initializationMessage();

    protected abstract String implementationHint();

    @Override
    public List<McpResourceDefinition> listResources() {
        throw new McpException(McpErrorCode.MCP_CAPABILITY_UNSUPPORTED,
                messageService.get("mcp.error.capability.resourcesUnsupported", server.name()));
    }

    @Override
    public JsonNode readResource(String uri) {
        throw new McpException(McpErrorCode.MCP_CAPABILITY_UNSUPPORTED,
                messageService.get("mcp.error.capability.resourcesUnsupported", server.name()));
    }

    @Override
    public List<McpPromptDefinition> listPrompts() {
        throw new McpException(McpErrorCode.MCP_CAPABILITY_UNSUPPORTED,
                messageService.get("mcp.error.capability.promptsUnsupported", server.name()));
    }

    @Override
    public McpPromptResult getPrompt(String name, java.util.Map<String, Object> arguments) {
        throw new McpException(McpErrorCode.MCP_CAPABILITY_UNSUPPORTED,
                messageService.get("mcp.error.capability.promptsUnsupported", server.name()));
    }
}
