package com.example.agentruntime.mcp;

import com.example.agentruntime.AgentRuntimeProperties;
import com.example.agentruntime.i18n.MessageService;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpClientTransport;

import java.net.URI;
import java.net.http.HttpClient;

/**
 * 基于官方 Java SDK 的 streamable-http MCP Client。
 */
public final class StreamableHttpMcpClient extends AbstractSdkMcpClient {

    public StreamableHttpMcpClient(AgentRuntimeProperties.McpServerProperties server,
                                   ObjectMapper objectMapper,
                                   MessageService messageService) {
        super(server, objectMapper, messageService);
    }

    @Override
    public McpTransportType transportType() {
        return McpTransportType.STREAMABLE_HTTP;
    }

    @Override
    protected McpClientTransport buildTransport() {
        if (server.url() == null || server.url().isBlank()) {
            throw new McpException(McpErrorCode.MCP_TRANSPORT_ERROR, messageService.get("mcp.http.init.missingUrl"));
        }
        URI uri = URI.create(server.url());
        String baseUri = uri.getScheme() + "://" + uri.getAuthority();
        String endpoint = uri.getPath() == null || uri.getPath().isBlank() ? "/mcp" : uri.getPath();

        HttpClientStreamableHttpTransport.Builder builder = HttpClientStreamableHttpTransport.builder(baseUri)
                .endpoint(endpoint)
                .connectTimeout(server.timeout())
                .supportedProtocolVersions(SUPPORTED_PROTOCOL_VERSIONS)
                .customizeClient(httpClientBuilder -> httpClientBuilder.followRedirects(HttpClient.Redirect.NORMAL))
                .customizeRequest(requestBuilder -> server.headers().forEach(requestBuilder::header));

        if (uri.getQuery() != null && !uri.getQuery().isBlank()) {
            builder.customizeRequest(requestBuilder -> requestBuilder.uri(URI.create(server.url())));
        }

        HttpClientStreamableHttpTransport transport = builder.build();
        transport.setExceptionHandler(throwable -> {
            // 当前阶段保留 transport 异常扩展点，实际分类在宿主层统一处理。
        });
        return new CompatibleStreamableHttpTransport(transport);
    }

    @Override
    protected void configureTransport(McpClientTransport transport) {
        // 当前无需附加配置。
    }

    @Override
    protected String initializationMessage(io.modelcontextprotocol.spec.McpSchema.InitializeResult result) {
        return messageService.get("mcp.http.initialize.success", result.protocolVersion());
    }
}
