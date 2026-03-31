package com.example.agentruntime.mcp;

import com.example.agentruntime.capability.AgentCapability;
import com.example.agentruntime.capability.CapabilityContext;
import com.example.agentruntime.capability.CapabilityDescriptor;
import com.example.agentruntime.capability.CapabilityMetadata;
import com.example.agentruntime.capability.CapabilityResult;
import com.example.agentruntime.capability.CapabilityType;
import com.example.agentruntime.capability.RiskLevel;
import com.example.agentruntime.i18n.MessageService;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * 将 MCP 工具定义适配成系统内部统一能力。
 */
public class McpToolCapability implements AgentCapability {

    private final McpClient client;
    private final McpToolDefinition tool;
    private final MessageService messageService;

    public McpToolCapability(McpClient client, McpToolDefinition tool, MessageService messageService) {
        this.client = client;
        this.tool = tool;
        this.messageService = messageService;
    }

    @Override
    public CapabilityDescriptor descriptor() {
        return new CapabilityDescriptor(
                "mcp:" + client.server().name() + ":" + tool.name(),
                tool.title() != null && !tool.title().isBlank() ? tool.title() : tool.name(),
                CapabilityType.MCP_TOOL,
                new CapabilityMetadata(
                        client.server().name(),
                        messageService.get("capability.mcp.descriptor.version"),
                        tool.description(),
                        RiskLevel.MEDIUM,
                        null),
                tool.inputSchema(),
                null
        );
    }

    @Override
    public CapabilityResult execute(CapabilityContext context, JsonNode input) {
        McpCallResult result = client.callTool(tool.name(), input);
        return new CapabilityResult(result.success(), result.message(), result.output());
    }
}
