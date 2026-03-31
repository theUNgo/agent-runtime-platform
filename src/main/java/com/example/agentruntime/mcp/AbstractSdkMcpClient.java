package com.example.agentruntime.mcp;

import com.example.agentruntime.AgentRuntimeProperties;
import com.example.agentruntime.i18n.MessageService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.json.McpJsonDefaults;
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.spec.McpClientTransport;
import io.modelcontextprotocol.spec.McpError;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpTransportException;
import io.modelcontextprotocol.spec.McpTransportSessionClosedException;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 基于官方 Java SDK 的 MCP Client 公共实现。
 * 负责统一初始化、状态缓存、能力检查、异常分类和 SDK 结果到宿主层对象的映射。
 */
public abstract class AbstractSdkMcpClient implements com.example.agentruntime.mcp.McpClient {

    protected static final List<String> SUPPORTED_PROTOCOL_VERSIONS = List.of(
            io.modelcontextprotocol.spec.ProtocolVersions.MCP_2024_11_05,
            io.modelcontextprotocol.spec.ProtocolVersions.MCP_2025_03_26,
            io.modelcontextprotocol.spec.ProtocolVersions.MCP_2025_06_18,
            io.modelcontextprotocol.spec.ProtocolVersions.MCP_2025_11_25
    );

    protected final AgentRuntimeProperties.McpServerProperties server;
    protected final ObjectMapper objectMapper;
    protected final MessageService messageService;
    protected final McpJsonMapper jsonMapper;

    private final AtomicReference<McpServerStatus> status = new AtomicReference<>();
    private volatile McpSyncClient syncClient;

    protected AbstractSdkMcpClient(AgentRuntimeProperties.McpServerProperties server,
                                   ObjectMapper objectMapper,
                                   MessageService messageService) {
        this.server = server;
        this.objectMapper = objectMapper;
        this.messageService = messageService;
        this.jsonMapper = McpJsonDefaults.getMapper();
        this.status.set(disconnectedStatus(messageService.get("mcp.status.disconnected")));
    }

    @Override
    public AgentRuntimeProperties.McpServerProperties server() {
        return server;
    }

    @Override
    public synchronized McpServerStatus initialize() {
        if (syncClient != null && syncClient.isInitialized()) {
            return health();
        }

        status.set(updateStatus(McpConnectionState.CONNECTING, false, null, null, null,
                messageService.get("mcp.status.connecting"), null));

        try {
            syncClient = buildClient();
            McpSchema.InitializeResult result = syncClient.initialize();
            McpServerStatus initializedStatus = new McpServerStatus(
                    server.name(),
                    transportType(),
                    McpConnectionState.INITIALIZED,
                    true,
                    result.protocolVersion(),
                    toServerInfo(result),
                    toCapabilities(result.capabilities()),
                    initializationMessage(result),
                    null
            );
            status.set(initializedStatus);
            afterInitialize(syncClient);
            return initializedStatus;
        } catch (RuntimeException exception) {
            throw classifyException(exception, "mcp.error.initialize.failed");
        }
    }

    @Override
    public McpServerStatus health() {
        return status.get();
    }

    @Override
    public McpServerInfo serverInfo() {
        initializeIfNeeded();
        return status.get().serverInfo();
    }

    @Override
    public McpCapabilitySnapshot serverCapabilities() {
        initializeIfNeeded();
        return status.get().capabilities();
    }

    @Override
    public List<McpToolDefinition> listTools() {
        initializeIfNeeded();
        ensureCapabilitySupported(status.get().capabilities().toolsSupported(), "mcp.error.capability.toolsUnsupported");
        try {
            return syncClient.listTools().tools().stream()
                    .map(this::toToolDefinition)
                    .toList();
        } catch (RuntimeException exception) {
            throw classifyException(exception, "mcp.error.tools.listFailed");
        }
    }

    @Override
    public McpCallResult callTool(String toolName, JsonNode input) {
        initializeIfNeeded();
        ensureCapabilitySupported(status.get().capabilities().toolsSupported(), "mcp.error.capability.toolsUnsupported");
        try {
            Map<String, Object> arguments = input == null || input.isNull()
                    ? Map.of()
                    : objectMapper.convertValue(input, new TypeReference<Map<String, Object>>() {
                    });
            McpSchema.CallToolResult result = syncClient.callTool(new McpSchema.CallToolRequest(toolName, arguments));
            return new McpCallResult(true, messageService.get("mcp.tool.call.success", toolName),
                    objectMapper.valueToTree(result));
        } catch (RuntimeException exception) {
            throw classifyException(exception, "mcp.error.tools.callFailed", toolName);
        }
    }

    @Override
    public List<McpResourceDefinition> listResources() {
        initializeIfNeeded();
        ensureCapabilitySupported(status.get().capabilities().resourcesSupported(), "mcp.error.capability.resourcesUnsupported");
        try {
            return syncClient.listResources().resources().stream()
                    .map(resource -> new McpResourceDefinition(
                            resource.uri(),
                            resource.name(),
                            resource.title(),
                            resource.description(),
                            resource.mimeType()))
                    .toList();
        } catch (RuntimeException exception) {
            throw classifyException(exception, "mcp.error.resources.listFailed");
        }
    }

    @Override
    public JsonNode readResource(String uri) {
        initializeIfNeeded();
        ensureCapabilitySupported(status.get().capabilities().resourcesSupported(), "mcp.error.capability.resourcesUnsupported");
        try {
            return objectMapper.valueToTree(syncClient.readResource(new McpSchema.ReadResourceRequest(uri)));
        } catch (RuntimeException exception) {
            throw classifyException(exception, "mcp.error.resources.readFailed", uri);
        }
    }

    @Override
    public List<McpPromptDefinition> listPrompts() {
        initializeIfNeeded();
        ensureCapabilitySupported(status.get().capabilities().promptsSupported(), "mcp.error.capability.promptsUnsupported");
        try {
            return syncClient.listPrompts().prompts().stream()
                    .map(prompt -> new McpPromptDefinition(prompt.name(), prompt.title(), prompt.description()))
                    .toList();
        } catch (RuntimeException exception) {
            throw classifyException(exception, "mcp.error.prompts.listFailed");
        }
    }

    @Override
    public McpPromptResult getPrompt(String name, Map<String, Object> arguments) {
        initializeIfNeeded();
        ensureCapabilitySupported(status.get().capabilities().promptsSupported(), "mcp.error.capability.promptsUnsupported");
        try {
            McpSchema.GetPromptResult result = syncClient.getPrompt(new McpSchema.GetPromptRequest(
                    name,
                    arguments == null ? Map.of() : Map.copyOf(arguments)
            ));
            return new McpPromptResult(result.description(), objectMapper.valueToTree(result.messages()));
        } catch (RuntimeException exception) {
            throw classifyException(exception, "mcp.error.prompts.getFailed", name);
        }
    }

    @Override
    public synchronized void close() {
        if (syncClient != null) {
            try {
                syncClient.closeGracefully();
            } catch (RuntimeException ignored) {
            } finally {
                syncClient.close();
                syncClient = null;
                status.set(updateStatus(McpConnectionState.CLOSED, false, null, null, null,
                        messageService.get("mcp.status.closed"), null));
            }
        }
    }

    protected abstract McpClientTransport buildTransport();

    protected abstract void configureTransport(McpClientTransport transport);

    protected String initializationMessage(McpSchema.InitializeResult result) {
        return messageService.get("mcp.initialize.success", result.protocolVersion());
    }

    protected void afterInitialize(McpSyncClient client) {
    }

    protected McpSyncClient buildClient() {
        McpClientTransport transport = buildTransport();
        configureTransport(transport);

        return McpClient.sync(transport)
                .requestTimeout(server.timeout())
                .initializationTimeout(initializationTimeout(server.timeout()))
                .clientInfo(clientInfo())
                .capabilities(clientCapabilities())
                .toolsChangeConsumer(tools -> status.set(withMessage(messageService.get("mcp.status.toolsChanged"))))
                .resourcesChangeConsumer(resources -> status.set(withMessage(messageService.get("mcp.status.resourcesChanged"))))
                .promptsChangeConsumer(prompts -> status.set(withMessage(messageService.get("mcp.status.promptsChanged"))))
                .loggingConsumer(logging -> status.set(withMessage(messageService.get("mcp.status.loggingReceived"))))
                .progressConsumer(progress -> status.set(withMessage(messageService.get("mcp.status.progressReceived"))))
                .build();
    }

    protected McpSchema.ClientCapabilities clientCapabilities() {
        return McpSchema.ClientCapabilities.builder()
                .roots(false)
                .build();
    }

    protected McpSchema.Implementation clientInfo() {
        return new McpSchema.Implementation("agent-runtime", "Agent Runtime", "0.1.0");
    }

    protected Duration initializationTimeout(Duration timeout) {
        return timeout.plusSeconds(5);
    }

    protected McpToolDefinition toToolDefinition(McpSchema.Tool tool) {
        return new McpToolDefinition(
                tool.name(),
                tool.title(),
                tool.description(),
                objectMapper.valueToTree(tool.inputSchema()),
                objectMapper.valueToTree(tool.outputSchema())
        );
    }

    protected McpServerInfo toServerInfo(McpSchema.InitializeResult result) {
        return new McpServerInfo(
                result.serverInfo().name(),
                result.serverInfo().title(),
                result.serverInfo().version(),
                result.instructions()
        );
    }

    protected McpCapabilitySnapshot toCapabilities(McpSchema.ServerCapabilities capabilities) {
        return new McpCapabilitySnapshot(
                capabilities != null && capabilities.tools() != null,
                capabilities != null && capabilities.resources() != null,
                capabilities != null && capabilities.prompts() != null,
                capabilities != null && capabilities.logging() != null,
                capabilities != null && capabilities.completions() != null
        );
    }

    protected McpException classifyException(Throwable throwable, String messageCode, Object... args) {
        Throwable root = findRootCause(throwable);
        String message = messageService.get(messageCode, args);
        McpServerStatus current = status.get();

        if (root instanceof TimeoutException) {
            status.set(updateStatus(McpConnectionState.ERROR, current.initialized(), current.protocolVersion(),
                    current.serverInfo(), current.capabilities(), message, root.getMessage()));
            return new McpException(McpErrorCode.MCP_TIMEOUT, message, throwable);
        }
        if (root instanceof McpTransportSessionClosedException
                || root instanceof McpTransportException
                || root instanceof io.modelcontextprotocol.client.transport.McpHttpClientTransportAuthorizationException) {
            status.set(updateStatus(McpConnectionState.ERROR, current.initialized(), current.protocolVersion(),
                    current.serverInfo(), current.capabilities(), message, root.getMessage()));
            return new McpException(McpErrorCode.MCP_TRANSPORT_ERROR, message, throwable);
        }
        if (root instanceof McpError) {
            status.set(updateStatus(McpConnectionState.ERROR, current.initialized(), current.protocolVersion(),
                    current.serverInfo(), current.capabilities(), message, root.getMessage()));
            return new McpException(McpErrorCode.MCP_PROTOCOL_ERROR, message, throwable);
        }
        status.set(updateStatus(McpConnectionState.ERROR, current.initialized(), current.protocolVersion(),
                current.serverInfo(), current.capabilities(), message, root.getMessage()));
        return new McpException(McpErrorCode.MCP_PROTOCOL_ERROR, message, throwable);
    }

    protected void ensureCapabilitySupported(boolean supported, String messageCode) {
        if (!supported) {
            throw new McpException(McpErrorCode.MCP_CAPABILITY_UNSUPPORTED, messageService.get(messageCode, server.name()));
        }
    }

    protected void initializeIfNeeded() {
        if (syncClient == null || !syncClient.isInitialized()) {
            initialize();
        }
    }

    protected McpServerStatus withMessage(String message) {
        McpServerStatus current = status.get();
        return updateStatus(current.connectionState(), current.initialized(), current.protocolVersion(),
                current.serverInfo(), current.capabilities(), message, current.lastError());
    }

    private McpServerStatus disconnectedStatus(String message) {
        return new McpServerStatus(
                server.name(),
                transportType(),
                McpConnectionState.DISCONNECTED,
                false,
                null,
                null,
                new McpCapabilitySnapshot(false, false, false, false, false),
                message,
                null
        );
    }

    private McpServerStatus updateStatus(McpConnectionState state,
                                         boolean initialized,
                                         String protocolVersion,
                                         McpServerInfo serverInfo,
                                         McpCapabilitySnapshot capabilities,
                                         String message,
                                         String lastError) {
        return new McpServerStatus(
                server.name(),
                transportType(),
                state,
                initialized,
                protocolVersion,
                serverInfo,
                capabilities == null ? new McpCapabilitySnapshot(false, false, false, false, false) : capabilities,
                message,
                lastError
        );
    }

    private Throwable findRootCause(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current;
    }
}
