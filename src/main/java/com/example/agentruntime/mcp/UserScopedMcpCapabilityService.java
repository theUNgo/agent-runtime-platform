package com.example.agentruntime.mcp;

import com.example.agentruntime.AgentRuntimeProperties;
import com.example.agentruntime.auth.AuthenticatedUser;
import com.example.agentruntime.i18n.MessageService;
import com.example.agentruntime.installation.UserInstallationService;
import com.example.agentruntime.persistence.entity.ManagedCatalogItemEntity;
import com.example.agentruntime.persistence.entity.UserAccountEntity;
import com.example.agentruntime.persistence.entity.UserInstallationEntity;
import com.example.agentruntime.persistence.repository.ManagedCatalogItemRepository;
import com.example.agentruntime.persistence.repository.UserAccountRepository;
import com.example.agentruntime.persistence.repository.UserInstallationRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 用户级 MCP 能力服务。
 * 当前通过读取用户安装表中的 MCP 配置，按需创建 client 并完成工具发现与验证。
 */
@Service
public class UserScopedMcpCapabilityService {

    private final UserInstallationRepository installationRepository;
    private final ManagedCatalogItemRepository managedCatalogItemRepository;
    private final UserAccountRepository userAccountRepository;
    private final List<McpClientFactory> factories;
    private final ObjectMapper objectMapper;
    private final MessageService messageService;

    public UserScopedMcpCapabilityService(UserInstallationRepository installationRepository,
                                          ManagedCatalogItemRepository managedCatalogItemRepository,
                                          UserAccountRepository userAccountRepository,
                                          List<McpClientFactory> factories,
                                          ObjectMapper objectMapper,
                                          MessageService messageService) {
        this.installationRepository = installationRepository;
        this.managedCatalogItemRepository = managedCatalogItemRepository;
        this.userAccountRepository = userAccountRepository;
        this.factories = factories;
        this.objectMapper = objectMapper;
        this.messageService = messageService;
    }

    public List<McpServerStatus> listStatuses(AuthenticatedUser user) {
        List<McpServerStatus> statuses = new ArrayList<>();
        for (AgentRuntimeProperties.McpServerProperties server : installedServers(user.id())) {
            try (McpClient client = createClient(server)) {
                statuses.add(client.initialize());
            } catch (RuntimeException exception) {
                statuses.add(new McpServerStatus(
                        server.name(),
                        transportType(server.transport()),
                        McpConnectionState.ERROR,
                        false,
                        null,
                        null,
                        new McpCapabilitySnapshot(false, false, false, false, false),
                        messageService.get("mcp.registry.serverInitFailed", server.name()),
                        rootMessage(exception)
                ));
            }
        }
        return statuses;
    }

    public List<McpToolDefinition> listTools(AuthenticatedUser user, String serverName) {
        AgentRuntimeProperties.McpServerProperties server = findInstalledServer(user, serverName)
                .orElseThrow(() -> new IllegalArgumentException(messageService.get("mcp.error.serverNotFound", serverName)));
        try (McpClient client = createClient(server)) {
            client.initialize();
            return client.listTools();
        }
    }

    public List<UserScopedMcpToolCapability> discoverCapabilities(AuthenticatedUser user) {
        List<UserScopedMcpToolCapability> capabilities = new ArrayList<>();
        for (AgentRuntimeProperties.McpServerProperties server : installedServers(user.id())) {
            try (McpClient client = createClient(server)) {
                McpServerStatus status = client.initialize();
                if (!status.initialized() || !client.serverCapabilities().toolsSupported()) {
                    continue;
                }
                for (McpToolDefinition tool : client.listTools()) {
                    capabilities.add(new UserScopedMcpToolCapability(server, tool, this, messageService));
                }
            } catch (RuntimeException ignored) {
                // 用户级动态发现不应该因为某一个 server 失败就中断整批能力收集。
            }
        }
        return capabilities;
    }

    public Optional<UserScopedMcpToolCapability> resolve(AuthenticatedUser user, String capabilityId) {
        return discoverCapabilities(user).stream()
                .filter(capability -> capability.descriptor().id().equals(capabilityId))
                .findFirst();
    }

    public McpCallResult callTool(AgentRuntimeProperties.McpServerProperties server, String toolName, JsonNode input) {
        try (McpClient client = createClient(server)) {
            client.initialize();
            return client.callTool(toolName, input);
        }
    }

    public Optional<AgentRuntimeProperties.McpServerProperties> findInstalledServer(AuthenticatedUser user, String serverName) {
        return installedServers(user.id()).stream()
                .filter(candidate -> serverName.equals(candidate.name()))
                .findFirst();
    }

    public McpValidationReport validate(AuthenticatedUser user, String serverName) {
        AgentRuntimeProperties.McpServerProperties server = findInstalledServer(user, serverName)
                .orElseThrow(() -> new IllegalArgumentException(messageService.get("mcp.error.serverNotFound", serverName)));
        try (McpClient client = createClient(server)) {
            McpServerStatus status = client.initialize();
            Map<String, String> checks = new LinkedHashMap<>();
            checks.put("initialize", messageService.get("mcp.validation.check.ok"));

            int toolCount = probeTools(client, checks);
            int resourceCount = probeResources(client, checks);
            int promptCount = probePrompts(client, checks);

            return new McpValidationReport(
                    status.serverName(),
                    status.transportType(),
                    true,
                    status.initialized(),
                    status.initialized(),
                    status.protocolVersion(),
                    McpValidationDiagnostics.describeConfiguration(server),
                    status.serverInfo(),
                    status.capabilities(),
                    toolCount,
                    resourceCount,
                    promptCount,
                    checks,
                    McpValidationDiagnostics.diagnose(server, status, messageService),
                    summarize(checks),
                    OffsetDateTime.now()
            );
        } catch (RuntimeException exception) {
            McpServerStatus failedStatus = new McpServerStatus(
                    server.name(),
                    transportType(server.transport()),
                    McpConnectionState.ERROR,
                    false,
                    null,
                    null,
                    new McpCapabilitySnapshot(false, false, false, false, false),
                    messageService.get("mcp.validation.summary.partial"),
                    rootMessage(exception)
            );
            Map<String, String> checks = new LinkedHashMap<>();
            checks.put("initialize", extractMessage(exception));
            return new McpValidationReport(
                    server.name(),
                    transportType(server.transport()),
                    true,
                    false,
                    false,
                    null,
                    McpValidationDiagnostics.describeConfiguration(server),
                    null,
                    failedStatus.capabilities(),
                    0,
                    0,
                    0,
                    checks,
                    McpValidationDiagnostics.diagnose(server, failedStatus, messageService),
                    messageService.get("mcp.validation.summary.partial"),
                    OffsetDateTime.now()
            );
        }
    }

    private List<AgentRuntimeProperties.McpServerProperties> installedServers(Long userId) {
        UserAccountEntity user = userAccountRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException(messageService.get("auth.error.userNotFound")));
        return installationRepository.findByUserAndItemType(user, "MCP_SERVER").stream()
                .filter(entity -> UserInstallationService.STATUS_ENABLED.equals(normalizeSelectionStatus(entity.getStatus())))
                .map(this::resolveManagedMetadata)
                .flatMap(Optional::stream)
                .map(this::toServerProperties)
                .flatMap(Optional::stream)
                .toList();
    }

    private Optional<UserInstallationEntity> resolveManagedMetadata(UserInstallationEntity selection) {
        return managedCatalogItemRepository.findByItemIdAndItemType(selection.getItemId(), selection.getItemType())
                .filter(entity -> UserInstallationService.STATUS_ENABLED.equals(normalizeSelectionStatus(entity.getStatus())))
                .map(managed -> copySelectionWithManagedMetadata(selection, managed))
                .or(() -> Optional.of(selection));
    }

    private UserInstallationEntity copySelectionWithManagedMetadata(UserInstallationEntity selection, ManagedCatalogItemEntity managed) {
        UserInstallationEntity resolved = new UserInstallationEntity();
        resolved.setUser(selection.getUser());
        resolved.setItemId(selection.getItemId());
        resolved.setItemName(managed.getItemName());
        resolved.setItemType(selection.getItemType());
        resolved.setStatus(selection.getStatus());
        resolved.setProvider(managed.getProvider());
        resolved.setMetadataJson(managed.getMetadataJson());
        return resolved;
    }

    private String normalizeSelectionStatus(String status) {
        if ("INSTALLED".equalsIgnoreCase(status)) {
            return UserInstallationService.STATUS_ENABLED;
        }
        return status;
    }

    private Optional<AgentRuntimeProperties.McpServerProperties> toServerProperties(UserInstallationEntity entity) {
        if (entity.getMetadataJson() == null || entity.getMetadataJson().isBlank()) {
            return Optional.empty();
        }
        try {
            Map<String, Object> raw = objectMapper.readValue(entity.getMetadataJson(), new TypeReference<Map<String, Object>>() {
            });
            String transport = stringValue(raw.get("transport"), "stdio");
            List<String> args = raw.get("args") instanceof List<?> list
                    ? list.stream().map(String::valueOf).toList()
                    : List.of();
            Map<String, String> headers = raw.get("headers") instanceof Map<?, ?> map
                    ? map.entrySet().stream().collect(LinkedHashMap::new,
                    (target, entry) -> target.put(String.valueOf(entry.getKey()), String.valueOf(entry.getValue())),
                    LinkedHashMap::putAll)
                    : Map.of();
            long timeoutSeconds = raw.get("timeoutSeconds") instanceof Number number ? number.longValue() : 15L;
            String serverName = stringValue(raw.get("serverName"), entity.getItemId());
            String description = stringValue(raw.get("description"), entity.getItemName());
            return Optional.of(new AgentRuntimeProperties.McpServerProperties(
                    serverName,
                    description,
                    true,
                    transport,
                    stringValue(raw.get("command"), null),
                    args,
                    stringValue(raw.get("url"), null),
                    Map.of(),
                    headers,
                    Duration.ofSeconds(timeoutSeconds)
            ));
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }

    private McpClient createClient(AgentRuntimeProperties.McpServerProperties server) {
        McpTransportType transportType = transportType(server.transport());
        McpClientFactory factory = factories.stream()
                .filter(candidate -> candidate.supports(transportType))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(messageService.get("mcp.error.noFactory", transportType)));
        return factory.create(server);
    }

    private int probeTools(McpClient client, Map<String, String> checks) {
        McpCapabilitySnapshot capabilities = client.serverCapabilities();
        if (!capabilities.toolsSupported()) {
            checks.put("tools", messageService.get("mcp.validation.check.unsupported"));
            return 0;
        }
        try {
            int count = client.listTools().size();
            checks.put("tools", messageService.get("mcp.validation.check.okCount", count));
            return count;
        } catch (RuntimeException exception) {
            checks.put("tools", extractMessage(exception));
            return 0;
        }
    }

    private int probeResources(McpClient client, Map<String, String> checks) {
        McpCapabilitySnapshot capabilities = client.serverCapabilities();
        if (!capabilities.resourcesSupported()) {
            checks.put("resources", messageService.get("mcp.validation.check.unsupported"));
            return 0;
        }
        try {
            int count = client.listResources().size();
            checks.put("resources", messageService.get("mcp.validation.check.okCount", count));
            return count;
        } catch (RuntimeException exception) {
            checks.put("resources", extractMessage(exception));
            return 0;
        }
    }

    private int probePrompts(McpClient client, Map<String, String> checks) {
        McpCapabilitySnapshot capabilities = client.serverCapabilities();
        if (!capabilities.promptsSupported()) {
            checks.put("prompts", messageService.get("mcp.validation.check.unsupported"));
            return 0;
        }
        try {
            int count = client.listPrompts().size();
            checks.put("prompts", messageService.get("mcp.validation.check.okCount", count));
            return count;
        } catch (RuntimeException exception) {
            checks.put("prompts", extractMessage(exception));
            return 0;
        }
    }

    private String summarize(Map<String, String> checks) {
        boolean hasFailure = checks.values().stream()
                .anyMatch(value -> !value.equals(messageService.get("mcp.validation.check.ok"))
                        && !value.startsWith(messageService.get("mcp.validation.check.okPrefix"))
                        && !value.equals(messageService.get("mcp.validation.check.unsupported")));
        if (hasFailure) {
            return messageService.get("mcp.validation.summary.partial");
        }
        return messageService.get("mcp.validation.summary.ok");
    }

    private McpTransportType transportType(String transport) {
        return McpTransportType.from(transport, messageService);
    }

    private String stringValue(Object value, String fallback) {
        return value == null || String.valueOf(value).isBlank() ? fallback : String.valueOf(value);
    }

    private String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current.getMessage();
    }

    private String extractMessage(RuntimeException exception) {
        if (exception instanceof McpException mcpException) {
            return mcpException.getMessage();
        }
        String message = rootMessage(exception);
        return message == null || message.isBlank()
                ? messageService.get("error.internal")
                : message;
    }
}
