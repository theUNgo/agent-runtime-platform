package com.example.agentruntime.mcp;

import com.example.agentruntime.AgentRuntimeProperties;
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
 * 用户级 MCP Tool 能力。
 * 与全局注册中心不同，这类能力会在执行时临时创建对应 client，
 * 从而支持每个用户拥有不同的 MCP 安装组合。
 */
public class UserScopedMcpToolCapability implements AgentCapability {

    private final AgentRuntimeProperties.McpServerProperties server;
    private final McpToolDefinition tool;
    private final UserScopedMcpCapabilityService capabilityService;
    private final MessageService messageService;

    public UserScopedMcpToolCapability(AgentRuntimeProperties.McpServerProperties server,
                                       McpToolDefinition tool,
                                       UserScopedMcpCapabilityService capabilityService,
                                       MessageService messageService) {
        this.server = server;
        this.tool = tool;
        this.capabilityService = capabilityService;
        this.messageService = messageService;
    }

    @Override
    public CapabilityDescriptor descriptor() {
        return new CapabilityDescriptor(
                capabilityId(server.name(), tool.name()),
                tool.title() != null && !tool.title().isBlank() ? tool.title() : tool.name(),
                CapabilityType.MCP_TOOL,
                new CapabilityMetadata(
                        server.name(),
                        messageService.get("capability.mcp.descriptor.version"),
                        tool.description(),
                        RiskLevel.MEDIUM,
                        null
                ),
                tool.inputSchema(),
                tool.outputSchema()
        );
    }

    @Override
    public CapabilityResult execute(CapabilityContext context, JsonNode input) {
        McpCallResult result = capabilityService.callTool(server, tool.name(), input);
        return new CapabilityResult(result.success(), result.message(), result.output());
    }

    public static String capabilityId(String serverName, String toolName) {
        return "mcp-user:" + serverName + ":" + toolName;
    }
}
