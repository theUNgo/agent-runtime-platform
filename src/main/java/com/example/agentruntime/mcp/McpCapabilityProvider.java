package com.example.agentruntime.mcp;

import com.example.agentruntime.capability.AgentCapability;
import com.example.agentruntime.capability.CapabilityDescriptor;
import com.example.agentruntime.capability.CapabilityProvider;
import com.example.agentruntime.i18n.MessageService;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
public class McpCapabilityProvider implements CapabilityProvider {

    private final McpClientRegistry clientRegistry;
    private final MessageService messageService;
    private final Map<String, AgentCapability> capabilities = new LinkedHashMap<>();

    /**
     * 把 MCP 侧暴露出来的工具，适配成系统统一的 Agent 能力。
     */
    public McpCapabilityProvider(McpClientRegistry clientRegistry, MessageService messageService) {
        this.clientRegistry = clientRegistry;
        this.messageService = messageService;
    }

    @Override
    public String providerId() {
        return "mcp-capability-provider";
    }

    @Override
    public synchronized List<CapabilityDescriptor> discover() {
        clientRegistry.refresh();
        capabilities.clear();

        for (McpClient client : clientRegistry.listClients()) {
            McpServerStatus status = client.health();
            if (!status.initialized() || status.connectionState() != McpConnectionState.INITIALIZED) {
                continue;
            }
            if (!client.serverCapabilities().toolsSupported()) {
                continue;
            }
            for (McpToolDefinition tool : client.listTools()) {
                AgentCapability capability = new McpToolCapability(client, tool, messageService);
                capabilities.put(capability.descriptor().id(), capability);
            }
        }

        return capabilities.values().stream()
                .map(AgentCapability::descriptor)
                .toList();
    }

    @Override
    public synchronized Optional<AgentCapability> resolve(String capabilityId) {
        return Optional.ofNullable(capabilities.get(capabilityId));
    }
}
