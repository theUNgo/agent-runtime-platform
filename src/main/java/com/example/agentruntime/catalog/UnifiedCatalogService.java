package com.example.agentruntime.catalog;

import com.example.agentruntime.AgentRuntimeProperties;
import com.example.agentruntime.auth.AuthenticatedUser;
import com.example.agentruntime.auth.CurrentUserService;
import com.example.agentruntime.capability.CapabilityRegistry;
import com.example.agentruntime.i18n.MessageService;
import com.example.agentruntime.installation.UserInstallationService;
import com.example.agentruntime.persistence.entity.ManagedCatalogItemEntity;
import com.example.agentruntime.persistence.repository.ManagedCatalogItemRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * 统一目录服务。
 * 当前先聚合两类可安装内容：
 * 1. 内置 MCP Server 模板
 * 2. 内置 Agent Skill 模板
 *
 * 这样前端可以先具备“搜索、查看、安装、配置”的基本工作流，
 * 后续再平滑升级到远程 marketplace。
 */
@Service
public class UnifiedCatalogService {

    private final AgentRuntimeProperties properties;
    private final CapabilityRegistry capabilityRegistry;
    private final MessageService messageService;
    private final CurrentUserService currentUserService;
    private final UserInstallationService userInstallationService;
    private final ManagedCatalogItemRepository managedCatalogItemRepository;
    private final ObjectMapper objectMapper;
    private final Map<String, CuratedSkillTemplate> skillTemplates;
    private final Map<String, CuratedMcpServerTemplate> mcpServerTemplates;

    public UnifiedCatalogService(AgentRuntimeProperties properties,
                                 CapabilityRegistry capabilityRegistry,
                                 MessageService messageService,
                                 CurrentUserService currentUserService,
                                 UserInstallationService userInstallationService,
                                 ManagedCatalogItemRepository managedCatalogItemRepository,
                                 ObjectMapper objectMapper) {
        this.properties = properties;
        this.capabilityRegistry = capabilityRegistry;
        this.messageService = messageService;
        this.currentUserService = currentUserService;
        this.userInstallationService = userInstallationService;
        this.managedCatalogItemRepository = managedCatalogItemRepository;
        this.objectMapper = objectMapper;
        this.skillTemplates = loadSkillTemplates();
        this.mcpServerTemplates = loadMcpServerTemplates();
    }

    /**
     * 返回统一目录搜索结果。
     */
    public List<CatalogItem> search(String query, CatalogItemType type) {
        String normalized = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        return allItems().stream()
                .filter(item -> type == null || item.type() == type)
                .filter(item -> normalized.isBlank() || searchableText(item).contains(normalized))
                .sorted(Comparator.comparing(CatalogItem::installed).reversed()
                        .thenComparing(CatalogItem::type)
                        .thenComparing(CatalogItem::name))
                .toList();
    }

    /**
     * 安装内置 Skill。
     */
    public CatalogInstallResult installSkill(String itemId) {
        AuthenticatedUser admin = currentUserService.requireAdmin();
        CuratedSkillTemplate template = Optional.ofNullable(skillTemplates.get(itemId))
                .orElseThrow(() -> new IllegalArgumentException(messageService.get("catalog.error.skillNotFound", itemId)));

        Path skillDirectory = Path.of(properties.skillsDir()).resolve(template.id());
        Path manifestPath = skillDirectory.resolve("skill.yaml");
        try {
            Files.createDirectories(skillDirectory);
            if (!Files.exists(manifestPath)) {
                Files.writeString(manifestPath, template.manifestYaml());
            }
            upsertManagedCatalogItem(
                    admin,
                    CatalogItemType.AGENT_SKILL,
                    template.id(),
                    template.name(),
                    UserInstallationService.STATUS_ENABLED,
                    "curated-skill-catalog",
                    template.manifestYaml()
            );
            capabilityRegistry.refresh();
            return new CatalogInstallResult(
                    itemId,
                    CatalogItemType.AGENT_SKILL,
                    true,
                    CatalogInstallMode.DIRECT,
                    messageService.get("catalog.install.skill.success", template.name()),
                    List.of(
                            messageService.get("catalog.install.skill.nextStep.refresh"),
                            messageService.get("catalog.install.skill.nextStep.invoke", template.id())
                    ),
                    null
            );
        } catch (IOException exception) {
            throw new IllegalStateException(messageService.get("catalog.install.skill.failed", template.name()), exception);
        }
    }

    /**
     * 生成 MCP Server 安装方案。
     * 如果模板还需要人工补配置，前端可以先展示 YAML 片段和下一步提示。
     */
    public CatalogInstallResult buildMcpInstallPlan(String itemId) {
        AuthenticatedUser admin = currentUserService.requireAdmin();
        CuratedMcpServerTemplate template = requireTemplate(itemId);
        String snippet = toYamlSnippet(template, Map.of(), template.id());
        upsertManagedCatalogItem(
                admin,
                CatalogItemType.MCP_SERVER,
                template.id(),
                template.name(),
                UserInstallationService.STATUS_PLANNED,
                "curated-mcp-catalog",
                snippet
        );

        return new CatalogInstallResult(
                itemId,
                CatalogItemType.MCP_SERVER,
                isMcpServerInstalled(itemId),
                CatalogInstallMode.TEMPLATE,
                messageService.get("catalog.install.server.templateReady", template.name()),
                List.of(
                        messageService.get("catalog.install.server.nextStep.copy"),
                        messageService.get("catalog.install.server.nextStep.refresh")
                ),
                snippet
        );
    }

    /**
     * 安装 MCP Server。
     * 支持用户在安装时提交连接参数，安装结果会落库到当前用户名下。
     */
    public CatalogInstallResult installMcpServer(McpCatalogInstallRequest request) {
        AuthenticatedUser admin = currentUserService.requireAdmin();
        CuratedMcpServerTemplate template = requireTemplate(request.itemId());
        Map<String, String> normalizedConfig = normalizeConfig(request.config());
        String serverName = resolveServerName(template, request.serverName(), normalizedConfig);
        validateInstallConfig(template, normalizedConfig);

        if (template.requiresManualConfiguration() && normalizedConfig.isEmpty()) {
            return buildMcpInstallPlan(template.id());
        }

        String metadata = toTemplateMetadataJson(template, normalizedConfig, serverName);
        upsertManagedCatalogItem(
                admin,
                CatalogItemType.MCP_SERVER,
                template.id(),
                serverName,
                UserInstallationService.STATUS_ENABLED,
                "curated-mcp-catalog",
                metadata
        );

        return new CatalogInstallResult(
                template.id(),
                CatalogItemType.MCP_SERVER,
                true,
                CatalogInstallMode.DIRECT,
                messageService.get("catalog.install.server.success", serverName),
                List.of(
                        messageService.get("catalog.install.server.nextStep.refresh"),
                        messageService.get("catalog.install.server.nextStep.invoke", serverName)
                ),
                toYamlSnippet(template, normalizedConfig, serverName)
        );
    }

    private CuratedMcpServerTemplate requireTemplate(String itemId) {
        return Optional.ofNullable(mcpServerTemplates.get(itemId))
                .orElseThrow(() -> new IllegalArgumentException(messageService.get("catalog.error.serverNotFound", itemId)));
    }

    /**
     * 普通用户按需启用全局能力。
     * 这里会校验管理员是否已经完成全局安装，避免用户选中了不可运行的目录项。
     */
    public void enableForCurrentUser(CatalogItemType itemType, String itemId) {
        AuthenticatedUser user = currentUserService.requireUser();
        ManagedCatalogItemEntity managedItem = requireGloballyAvailable(itemType, itemId);
        userInstallationService.markEnabled(
                user.id(),
                itemType,
                itemId,
                managedItem.getItemName(),
                managedItem.getProvider(),
                managedItem.getMetadataJson()
        );
    }

    /**
     * 普通用户取消启用某个全局能力。
     */
    public void disableForCurrentUser(CatalogItemType itemType, String itemId) {
        AuthenticatedUser user = currentUserService.requireUser();
        userInstallationService.remove(user.id(), itemType, itemId);
    }

    private List<CatalogItem> allItems() {
        List<CatalogItem> items = new ArrayList<>();
        Long userId = currentUserService.currentUser().map(user -> user.id()).orElse(null);

        skillTemplates.values().forEach(template -> items.add(new CatalogItem(
                template.id(),
                template.name(),
                CatalogItemType.AGENT_SKILL,
                "curated-skill-catalog",
                template.version(),
                template.description(),
                template.tags(),
                isGloballyAvailable(CatalogItemType.AGENT_SKILL, template.id(), isSkillInstalled(template.id())),
                stateForUser(userId, CatalogItemType.AGENT_SKILL, template.id()),
                CatalogInstallMode.DIRECT,
                null,
                messageService.get("catalog.installHint.skill"),
                List.of()
        )));

        mcpServerTemplates.values().forEach(template -> items.add(new CatalogItem(
                template.id(),
                template.name(),
                CatalogItemType.MCP_SERVER,
                "curated-mcp-catalog",
                template.version(),
                template.description(),
                template.tags(),
                isGloballyAvailable(CatalogItemType.MCP_SERVER, template.id(), isMcpServerInstalled(template.id())),
                stateForUser(userId, CatalogItemType.MCP_SERVER, template.id()),
                template.requiresManualConfiguration() ? CatalogInstallMode.TEMPLATE : CatalogInstallMode.DIRECT,
                template.transport(),
                messageService.get("catalog.installHint.server"),
                template.installFields()
        )));
        return items;
    }

    private boolean isSkillInstalled(String skillId) {
        return Files.exists(Path.of(properties.skillsDir()).resolve(skillId).resolve("skill.yaml"));
    }

    private boolean isMcpServerInstalled(String serverName) {
        return properties.mcp().servers().stream().anyMatch(server -> serverName.equals(server.name()));
    }

    private String stateForUser(Long userId, CatalogItemType itemType, String itemId) {
        if (userId == null) {
            return UserInstallationService.STATUS_NOT_ENABLED;
        }
        return userInstallationService.stateFor(userId, itemType, itemId);
    }

    private boolean isGloballyAvailable(CatalogItemType itemType, String itemId, boolean fallback) {
        return managedCatalogItemRepository.findByItemIdAndItemType(itemId, itemType.name())
                .map(entity -> UserInstallationService.STATUS_ENABLED.equals(normalizeManagedStatus(entity.getStatus()))
                        || "INSTALLED".equalsIgnoreCase(entity.getStatus()))
                .orElse(fallback);
    }

    private String searchableText(CatalogItem item) {
        return String.join(" ",
                item.id(),
                item.name(),
                item.provider(),
                item.description(),
                String.join(" ", item.tags()))
                .toLowerCase(Locale.ROOT);
    }

    private Map<String, CuratedSkillTemplate> loadSkillTemplates() {
        Map<String, CuratedSkillTemplate> templates = new LinkedHashMap<>();
        templates.put("bug-triage", new CuratedSkillTemplate(
                "bug-triage",
                "Bug Triage",
                "0.1.0",
                "根据用户描述、日志和上下文生成结构化问题分诊结论。",
                """
                你是一个问题分诊 Skill。
                目标是根据输入中的报错、复现步骤和上下文，输出：
                1. 问题摘要
                2. 可能根因
                3. 建议优先级
                4. 下一步排查动作
                请优先关注可验证结论，不要臆测。
                """,
                List.of("bug", "triage", "incident", "support")
        ));
        templates.put("api-doc-writer", new CuratedSkillTemplate(
                "api-doc-writer",
                "API Doc Writer",
                "0.1.0",
                "将接口描述、示例和约束整理成开发者可读的 API 文档。",
                """
                你是一个 API 文档编写 Skill。
                请把输入整理为简明、结构化的接口文档，至少包含：
                - 接口用途
                - 请求参数
                - 返回结构
                - 错误码
                - 调用示例
                如果信息不全，请明确标记待补充项。
                """,
                List.of("api", "documentation", "developer-experience")
        ));
        templates.put("research-brief", new CuratedSkillTemplate(
                "research-brief",
                "Research Brief",
                "0.1.0",
                "把零散调研材料整合成可执行的调研简报。",
                """
                你是一个调研简报 Skill。
                请把输入整理成高管和研发都能快速理解的调研摘要，至少包含：
                - 背景与目标
                - 关键发现
                - 方案对比
                - 风险与限制
                - 推荐结论
                """,
                List.of("research", "analysis", "brief")
        ));
        return Map.copyOf(templates);
    }

    private Map<String, CuratedMcpServerTemplate> loadMcpServerTemplates() {
        Map<String, CuratedMcpServerTemplate> templates = new LinkedHashMap<>();
        templates.put("filesystem-local", new CuratedMcpServerTemplate(
                "filesystem-local",
                "Filesystem Local Server",
                "0.1.0",
                "本地文件系统 stdio MCP server，适合快速接入工程目录。",
                false,
                "stdio",
                "npx.cmd",
                List.of("-y", "@modelcontextprotocol/server-filesystem", "${rootPath}"),
                null,
                Map.of(),
                Duration.ofSeconds(20),
                List.of("filesystem", "local", "stdio"),
                List.of(
                        new CatalogInstallField(
                                "rootPath",
                                "工作目录",
                                properties.workspaceRoot(),
                                true,
                                false,
                                normalizePath(properties.workspaceRoot()),
                                "MCP 文件系统 server 可访问的根目录。"
                        ),
                        new CatalogInstallField(
                                "serverName",
                                "实例名称",
                                "filesystem-local",
                                false,
                                false,
                                "filesystem-local",
                                "用于区分你自己的安装实例名称。"
                        )
                )
        ));
        templates.put("fetch-http", new CuratedMcpServerTemplate(
                "fetch-http",
                "Fetch HTTP Server",
                "0.1.0",
                "远程 streamable-http MCP gateway，适合接入统一网关。",
                false,
                "streamable-http",
                null,
                List.of(),
                "${url}",
                Map.of("Authorization", "${authorizationHeader}"),
                Duration.ofSeconds(15),
                List.of("http", "gateway", "remote"),
                List.of(
                        new CatalogInstallField(
                                "url",
                                "MCP 网关地址",
                                "https://your-mcp-gateway.example.com/mcp",
                                true,
                                false,
                                "",
                                "远程 MCP streamable-http 入口地址。"
                        ),
                        new CatalogInstallField(
                                "authorizationHeader",
                                "Authorization Header",
                                "Bearer <token>",
                                false,
                                true,
                                "",
                                "如果网关需要鉴权，可在这里填写完整的 Authorization 头。"
                        ),
                        new CatalogInstallField(
                                "serverName",
                                "实例名称",
                                "fetch-http",
                                false,
                                false,
                                "fetch-http",
                                "用于区分你自己的安装实例名称。"
                        )
                )
        ));
        return Map.copyOf(templates);
    }

    private Map<String, String> normalizeConfig(Map<String, String> config) {
        if (config == null || config.isEmpty()) {
            return Map.of();
        }
        Map<String, String> normalized = new LinkedHashMap<>();
        config.forEach((key, value) -> {
            if (key != null) {
                normalized.put(key.trim(), value == null ? "" : value.trim());
            }
        });
        return Map.copyOf(normalized);
    }

    private String resolveServerName(CuratedMcpServerTemplate template, String requestServerName, Map<String, String> config) {
        String candidate = requestServerName;
        if (candidate == null || candidate.isBlank()) {
            candidate = config.getOrDefault("serverName", template.id());
        }
        return candidate == null || candidate.isBlank() ? template.id() : candidate.trim();
    }

    private void validateInstallConfig(CuratedMcpServerTemplate template, Map<String, String> config) {
        for (CatalogInstallField field : template.installFields()) {
            if (field.required() && resolveValue(field.key(), template, config).isBlank()) {
                throw new IllegalArgumentException(messageService.get("catalog.install.server.missingField", field.label()));
            }
        }
    }

    private String toYamlSnippet(CuratedMcpServerTemplate template, Map<String, String> config, String serverName) {
        StringBuilder builder = new StringBuilder();
        builder.append("- name: ").append(serverName).append('\n');
        builder.append("  description: ").append(template.description()).append('\n');
        builder.append("  enabled: true\n");
        builder.append("  transport: ").append(template.transport()).append('\n');
        String command = resolveValue("command", template, config);
        if (!command.isBlank()) {
            builder.append("  command: ").append(command).append('\n');
            List<String> args = resolveArgs(template, config);
            if (!args.isEmpty()) {
                builder.append("  args:\n");
                for (String arg : args) {
                    builder.append("    - ").append(arg).append('\n');
                }
            }
        }
        String url = resolveValue("url", template, config);
        if (!url.isBlank()) {
            builder.append("  url: ").append(url).append('\n');
        }
        Map<String, String> headers = resolveHeaders(template, config);
        if (!headers.isEmpty()) {
            builder.append("  headers:\n");
            headers.forEach((key, value) -> builder.append("    ")
                    .append(key)
                    .append(": ")
                    .append(value)
                    .append('\n'));
        }
        builder.append("  timeout: ").append(template.timeout().toSeconds()).append("s\n");
        return builder.toString();
    }

    /**
     * 将管理员配置的全局目录项写入统一存储。
     * 这样用户侧只需要记录启用关系，真正的配置与文档都以这一份为准。
     */
    private void upsertManagedCatalogItem(AuthenticatedUser admin,
                                          CatalogItemType itemType,
                                          String itemId,
                                          String itemName,
                                          String status,
                                          String provider,
                                          String metadataJson) {
        ManagedCatalogItemEntity entity = managedCatalogItemRepository.findByItemIdAndItemType(itemId, itemType.name())
                .orElseGet(ManagedCatalogItemEntity::new);
        entity.setItemId(itemId);
        entity.setItemName(itemName);
        entity.setItemType(itemType.name());
        entity.setStatus(status);
        entity.setProvider(provider);
        entity.setMetadataJson(metadataJson);
        managedCatalogItemRepository.save(entity);
    }

    private ManagedCatalogItemEntity requireGloballyAvailable(CatalogItemType itemType, String itemId) {
        return managedCatalogItemRepository.findByItemIdAndItemType(itemId, itemType.name())
                .filter(entity -> UserInstallationService.STATUS_ENABLED.equals(normalizeManagedStatus(entity.getStatus())))
                .orElseThrow(() -> new IllegalArgumentException(messageService.get("catalog.error.itemNotEnabledByAdmin", itemId)));
    }

    private String normalizeManagedStatus(String status) {
        if ("INSTALLED".equalsIgnoreCase(status)) {
            return UserInstallationService.STATUS_ENABLED;
        }
        return status;
    }

    private String toTemplateMetadataJson(CuratedMcpServerTemplate template, Map<String, String> config, String serverName) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("templateId", template.id());
        metadata.put("serverName", serverName);
        metadata.put("description", template.name());
        metadata.put("transport", template.transport());
        metadata.put("command", blankToNull(resolveValue("command", template, config)));
        metadata.put("url", blankToNull(resolveValue("url", template, config)));
        metadata.put("timeoutSeconds", template.timeout().toSeconds());
        metadata.put("args", resolveArgs(template, config));
        metadata.put("headers", resolveHeaders(template, config));
        metadata.put("config", config);
        try {
            return objectMapper.writeValueAsString(metadata);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(messageService.get("error.internal"), exception);
        }
    }

    private List<String> resolveArgs(CuratedMcpServerTemplate template, Map<String, String> config) {
        return template.args().stream()
                .map(arg -> replacePlaceholders(arg, template, config))
                .filter(value -> value != null && !value.isBlank())
                .toList();
    }

    private Map<String, String> resolveHeaders(CuratedMcpServerTemplate template, Map<String, String> config) {
        Map<String, String> headers = new LinkedHashMap<>();
        template.headers().forEach((key, value) -> {
            String resolved = replacePlaceholders(value, template, config);
            if (!resolved.isBlank()) {
                headers.put(key, resolved);
            }
        });
        return Map.copyOf(headers);
    }

    private String resolveValue(String key, CuratedMcpServerTemplate template, Map<String, String> config) {
        return switch (key) {
            case "command" -> blankToEmpty(template.command());
            case "url" -> replacePlaceholders(template.url(), template, config);
            case "rootPath" -> normalizePath(config.getOrDefault("rootPath", properties.workspaceRoot()));
            default -> config.getOrDefault(key, defaultFieldValue(template, key));
        };
    }

    private String defaultFieldValue(CuratedMcpServerTemplate template, String key) {
        return template.installFields().stream()
                .filter(field -> field.key().equals(key))
                .map(CatalogInstallField::defaultValue)
                .findFirst()
                .orElse("");
    }

    private String replacePlaceholders(String source, CuratedMcpServerTemplate template, Map<String, String> config) {
        if (source == null || source.isBlank()) {
            return "";
        }
        String result = source;
        for (CatalogInstallField field : template.installFields()) {
            String value = config.getOrDefault(field.key(), blankToEmpty(field.defaultValue()));
            if ("rootPath".equals(field.key()) && !value.isBlank()) {
                value = normalizePath(value);
            }
            result = result.replace("${" + field.key() + "}", value);
        }
        return result.replace("${workspaceRoot}", normalizePath(properties.workspaceRoot())).trim();
    }

    private String blankToEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private String normalizePath(String value) {
        if (value == null || value.isBlank()) {
            return Path.of(properties.workspaceRoot()).toAbsolutePath().normalize().toString().replace('\\', '/');
        }
        return Path.of(value).toAbsolutePath().normalize().toString().replace('\\', '/');
    }
}
