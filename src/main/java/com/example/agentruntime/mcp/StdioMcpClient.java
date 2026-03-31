package com.example.agentruntime.mcp;

import com.example.agentruntime.AgentRuntimeProperties;
import com.example.agentruntime.i18n.MessageService;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.client.transport.ServerParameters;
import io.modelcontextprotocol.client.transport.StdioClientTransport;
import io.modelcontextprotocol.spec.McpClientTransport;

import java.nio.file.Path;
import java.util.List;

/**
 * 基于官方 Java SDK 的 stdio MCP Client。
 */
public final class StdioMcpClient extends AbstractSdkMcpClient {

    private volatile ProtocolAwareStdioClientTransport transport;
    private final Path baseDirectory;

    public StdioMcpClient(AgentRuntimeProperties.McpServerProperties server,
                          Path baseDirectory,
                          ObjectMapper objectMapper,
                          MessageService messageService) {
        super(server, objectMapper, messageService);
        this.baseDirectory = baseDirectory;
    }

    @Override
    public McpTransportType transportType() {
        return McpTransportType.STDIO;
    }

    @Override
    protected McpClientTransport buildTransport() {
        if (server.command() == null || server.command().isBlank()) {
            throw new McpException(McpErrorCode.MCP_TRANSPORT_ERROR, messageService.get("mcp.stdio.init.missingCommand"));
        }
        String resolvedCommand = StdioCommandResolver.resolveCommand(server.command(), baseDirectory);
        List<String> resolvedArgs = StdioCommandResolver.resolveArgs(server.args(), baseDirectory);
        ServerParameters.Builder builder = ServerParameters.builder(resolvedCommand)
                .args(resolvedArgs)
                .env(server.env());
        this.transport = new ProtocolAwareStdioClientTransport(builder.build(), jsonMapper, SUPPORTED_PROTOCOL_VERSIONS);
        return transport;
    }

    @Override
    protected void configureTransport(McpClientTransport transport) {
        if (this.transport != null) {
            this.transport.setStdErrorHandler(stderr -> {
                // 当前阶段仅保留 stderr 处理扩展点。
            });
        }
    }

    @Override
    protected String initializationMessage(io.modelcontextprotocol.spec.McpSchema.InitializeResult result) {
        return messageService.get("mcp.stdio.initialize.success", result.protocolVersion());
    }

    @Override
    public synchronized void close() {
        super.close();
        if (transport != null) {
            transport.awaitForExit();
            transport = null;
        }
    }
}
