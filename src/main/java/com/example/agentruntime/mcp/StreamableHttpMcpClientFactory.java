package com.example.agentruntime.mcp;

import com.example.agentruntime.AgentRuntimeProperties;
import com.example.agentruntime.i18n.MessageService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

/**
 * streamable-http 传输专用的 MCP Client 工厂。
 */
@Component
public class StreamableHttpMcpClientFactory implements McpClientFactory {

    private final ObjectMapper objectMapper;
    private final MessageService messageService;

    public StreamableHttpMcpClientFactory(ObjectMapper objectMapper, MessageService messageService) {
        this.objectMapper = objectMapper;
        this.messageService = messageService;
    }

    @Override
    public boolean supports(McpTransportType transportType) {
        return transportType == McpTransportType.STREAMABLE_HTTP;
    }

    @Override
    public McpClient create(AgentRuntimeProperties.McpServerProperties server) {
        return new StreamableHttpMcpClient(server, objectMapper, messageService);
    }
}
