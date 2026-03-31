package com.example.agentruntime.mcp;

import com.example.agentruntime.AgentRuntimeProperties;
import com.example.agentruntime.i18n.MessageService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.nio.file.Path;

/**
 * stdio 传输专用的 MCP Client 工厂。
 */
@Component
public class StdioMcpClientFactory implements McpClientFactory {

    private final ObjectMapper objectMapper;
    private final MessageService messageService;
    private final Path baseDirectory;

    public StdioMcpClientFactory(ObjectMapper objectMapper,
                                 MessageService messageService,
                                 AgentRuntimeProperties properties) {
        this.objectMapper = objectMapper;
        this.messageService = messageService;
        this.baseDirectory = Path.of(properties.workspaceRoot()).toAbsolutePath().normalize();
    }

    @Override
    public boolean supports(McpTransportType transportType) {
        return transportType == McpTransportType.STDIO;
    }

    @Override
    public McpClient create(AgentRuntimeProperties.McpServerProperties server) {
        return new StdioMcpClient(server, baseDirectory, objectMapper, messageService);
    }
}
