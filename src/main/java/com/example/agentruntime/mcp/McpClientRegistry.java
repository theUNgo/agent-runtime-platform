package com.example.agentruntime.mcp;

import com.example.agentruntime.AgentRuntimeProperties;
import com.example.agentruntime.i18n.MessageService;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
public class McpClientRegistry {

    private final AgentRuntimeProperties properties;
    private final List<McpClientFactory> factories;
    private final Map<String, McpClient> clients = new LinkedHashMap<>();
    private final Map<String, McpServerStatus> statuses = new LinkedHashMap<>();
    private final MessageService messageService;

    /**
     * 统一管理所有 MCP Client 的创建、刷新和销毁。
     */
    public McpClientRegistry(AgentRuntimeProperties properties,
                             List<McpClientFactory> factories,
                             MessageService messageService) {
        this.properties = properties;
        this.factories = factories;
        this.messageService = messageService;
    }

    public synchronized List<McpClient> listClients() {
        if (clients.isEmpty()) {
            refresh();
        }
        return List.copyOf(clients.values());
    }

    public synchronized Optional<McpClient> find(String serverName) {
        if (clients.isEmpty() && statuses.isEmpty()) {
            refresh();
        }
        return Optional.ofNullable(clients.get(serverName));
    }

    /**
     * 返回所有已配置 MCP Server 的当前状态。
     * 即使某个 server 初始化失败，也会保留一条 ERROR 状态，方便前端和排障接口查看。
     */
    public synchronized List<McpServerStatus> listStatuses() {
        if (clients.isEmpty() && statuses.isEmpty()) {
            refresh();
        }
        return new ArrayList<>(statuses.values());
    }

    public synchronized Optional<McpServerStatus> findStatus(String serverName) {
        if (clients.isEmpty() && statuses.isEmpty()) {
            refresh();
        }
        return Optional.ofNullable(statuses.get(serverName));
    }

    /**
     * 返回配置文件中声明的 MCP Server 定义。
     * 这个方法用于联调诊断场景，即使 server 初始化失败，也能回看原始配置。
     */
    public synchronized Optional<AgentRuntimeProperties.McpServerProperties> findConfiguredServer(String serverName) {
        if (properties == null || properties.mcp() == null || properties.mcp().servers() == null) {
            return Optional.empty();
        }
        return properties.mcp().servers().stream()
                .filter(server -> serverName.equals(server.name()))
                .findFirst();
    }

    public synchronized void refresh() {
        closeAll();
        clients.clear();
        statuses.clear();

        for (AgentRuntimeProperties.McpServerProperties server : properties.mcp().servers()) {
            if (!server.enabled()) {
                continue;
            }
            try {
                McpTransportType transportType = McpTransportType.from(server.transport(), messageService);
                McpClientFactory factory = factories.stream()
                        .filter(candidate -> candidate.supports(transportType))
                        .findFirst()
                        .orElseThrow(() -> new IllegalStateException(messageService.get("mcp.error.noFactory", transportType)));
                McpClient client = factory.create(server);
                McpServerStatus status = client.initialize();
                clients.put(server.name(), client);
                statuses.put(server.name(), status);
            } catch (RuntimeException exception) {
                statuses.put(server.name(), failedStatus(server, exception));
            }
        }
    }

    @PreDestroy
    public synchronized void closeAll() {
        for (McpClient client : clients.values()) {
            try {
                client.close();
            } catch (Exception ignored) {
            }
        }
    }

    private McpServerStatus failedStatus(AgentRuntimeProperties.McpServerProperties server, RuntimeException exception) {
        McpTransportType transportType = fallbackTransportType(server.transport());
        String errorSummary = rootMessage(exception);
        return new McpServerStatus(
                server.name(),
                transportType,
                McpConnectionState.ERROR,
                false,
                null,
                null,
                new McpCapabilitySnapshot(false, false, false, false, false),
                messageService.get("mcp.registry.serverInitFailed", server.name()),
                errorSummary
        );
    }

    private McpTransportType fallbackTransportType(String transport) {
        try {
            return McpTransportType.from(transport, messageService);
        } catch (RuntimeException ignored) {
            return McpTransportType.STDIO;
        }
    }

    private String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current.getMessage();
    }
}
