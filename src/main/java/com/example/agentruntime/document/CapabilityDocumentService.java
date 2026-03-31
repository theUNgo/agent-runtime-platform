package com.example.agentruntime.document;

import com.example.agentruntime.AgentRuntimeProperties;
import com.example.agentruntime.catalog.CatalogItemType;
import com.example.agentruntime.catalog.UnifiedCatalogService;
import com.example.agentruntime.i18n.MessageService;
import com.example.agentruntime.skill.SkillManifest;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 能力文档服务。
 * 统一管理 Skill 与 MCP 模板的摘要/详情文档，供前端和模型按需动态读取。
 */
@Service
public class CapabilityDocumentService {

    private static final String DOC_TYPE_SUMMARY = "summary";
    private static final String DOC_TYPE_DETAIL = "detail";

    private final AgentRuntimeProperties properties;
    private final UnifiedCatalogService unifiedCatalogService;
    private final MessageService messageService;

    public CapabilityDocumentService(AgentRuntimeProperties properties,
                                     @Lazy UnifiedCatalogService unifiedCatalogService,
                                     MessageService messageService) {
        this.properties = properties;
        this.unifiedCatalogService = unifiedCatalogService;
        this.messageService = messageService;
    }

    /**
     * 搜索可读的能力文档摘要。
     * 第一版覆盖本地 Skill 与内置目录模板，便于模型先读概述再决定是否读取详情。
     */
    public List<CapabilityDocumentSummary> search(String query) {
        String normalized = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        return allSummaries().stream()
                .filter(item -> normalized.isBlank() || searchableText(item).contains(normalized))
                .sorted(Comparator.comparing(CapabilityDocumentSummary::name))
                .toList();
    }

    /**
     * 读取指定能力文档。
     * 只允许读取 summary/detail 两类固定文档，避免模型借能力接口任意读文件。
     */
    public CapabilityDocumentView read(String docId, String docType) {
        String normalizedType = normalizeDocType(docType);
        if (docId == null || docId.isBlank()) {
            throw new IllegalArgumentException(messageService.get("capability.doc.error.idRequired"));
        }

        if (docId.startsWith("skill:")) {
            return readLocalSkillDocument(docId, normalizedType);
        }
        if (docId.startsWith("catalog-skill:")) {
            return readCatalogSkillDocument(docId, normalizedType);
        }
        if (docId.startsWith("catalog-mcp:")) {
            return readCatalogMcpDocument(docId, normalizedType);
        }

        throw new IllegalArgumentException(messageService.get("capability.doc.error.notFound", docId));
    }

    private List<CapabilityDocumentSummary> allSummaries() {
        List<CapabilityDocumentSummary> items = new ArrayList<>();
        items.addAll(localSkillSummaries());
        items.addAll(catalogSummaries());
        return items;
    }

    private List<CapabilityDocumentSummary> localSkillSummaries() {
        Path skillsRoot = Path.of(properties.skillsDir());
        if (!Files.exists(skillsRoot)) {
            return List.of();
        }

        List<CapabilityDocumentSummary> summaries = new ArrayList<>();
        try (var directories = Files.list(skillsRoot)) {
            directories
                    .filter(Files::isDirectory)
                    .forEach(skillDir -> {
                        Path manifestPath = skillDir.resolve("skill.yaml");
                        if (!Files.exists(manifestPath)) {
                            return;
                        }
                        SkillManifest manifest = loadSkillManifest(manifestPath);
                        if (manifest == null || manifest.id() == null || manifest.id().isBlank()) {
                            return;
                        }
                        summaries.add(new CapabilityDocumentSummary(
                                "skill:" + manifest.id(),
                                "skill:" + manifest.id(),
                                manifest.name(),
                                "SKILL",
                                "local-skill",
                                readOrFallbackSkillSummary(skillDir, manifest),
                                manifest.tags()
                        ));
                    });
        } catch (IOException exception) {
            throw new IllegalStateException(messageService.get("capability.doc.error.scanFailed"), exception);
        }
        return summaries;
    }

    private List<CapabilityDocumentSummary> catalogSummaries() {
        List<CapabilityDocumentSummary> summaries = new ArrayList<>();
        unifiedCatalogService.search(null, null).forEach(item -> {
            if (item.type() == CatalogItemType.AGENT_SKILL) {
                summaries.add(new CapabilityDocumentSummary(
                        "catalog-skill:" + item.id(),
                        "catalog-skill:" + item.id(),
                        item.name(),
                        "SKILL_TEMPLATE",
                        "catalog",
                        buildCatalogSkillSummary(item),
                        item.tags()
                ));
                return;
            }
            if (item.type() == CatalogItemType.MCP_SERVER) {
                summaries.add(new CapabilityDocumentSummary(
                        "catalog-mcp:" + item.id(),
                        "catalog-mcp:" + item.id(),
                        item.name(),
                        "MCP_SERVER_TEMPLATE",
                        "catalog",
                        buildCatalogMcpSummary(item),
                        item.tags()
                ));
            }
        });
        return summaries;
    }

    private CapabilityDocumentView readLocalSkillDocument(String docId, String docType) {
        String skillId = docId.substring("skill:".length());
        Path skillDir = Path.of(properties.skillsDir()).resolve(skillId);
        Path manifestPath = skillDir.resolve("skill.yaml");
        if (!Files.exists(manifestPath)) {
            throw new IllegalArgumentException(messageService.get("capability.doc.error.notFound", docId));
        }

        SkillManifest manifest = loadSkillManifest(manifestPath);
        String content = switch (docType) {
            case DOC_TYPE_SUMMARY -> readOrFallbackSkillSummary(skillDir, manifest);
            case DOC_TYPE_DETAIL -> readOrFallbackSkillDetail(skillDir, manifest);
            default -> throw new IllegalArgumentException(messageService.get("capability.doc.error.typeUnsupported", docType));
        };
        return new CapabilityDocumentView(
                docId,
                docId,
                manifest.name(),
                "SKILL",
                docType,
                content,
                manifest.tags()
        );
    }

    private CapabilityDocumentView readCatalogSkillDocument(String docId, String docType) {
        String itemId = docId.substring("catalog-skill:".length());
        var item = unifiedCatalogService.search(null, CatalogItemType.AGENT_SKILL).stream()
                .filter(candidate -> candidate.id().equals(itemId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(messageService.get("capability.doc.error.notFound", docId)));
        String content = DOC_TYPE_DETAIL.equals(docType)
                ? buildCatalogSkillDetail(item)
                : buildCatalogSkillSummary(item);
        return new CapabilityDocumentView(
                docId,
                docId,
                item.name(),
                "SKILL_TEMPLATE",
                docType,
                content,
                item.tags()
        );
    }

    private CapabilityDocumentView readCatalogMcpDocument(String docId, String docType) {
        String itemId = docId.substring("catalog-mcp:".length());
        var item = unifiedCatalogService.search(null, CatalogItemType.MCP_SERVER).stream()
                .filter(candidate -> candidate.id().equals(itemId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(messageService.get("capability.doc.error.notFound", docId)));
        String content = DOC_TYPE_DETAIL.equals(docType)
                ? buildCatalogMcpDetail(item)
                : buildCatalogMcpSummary(item);
        return new CapabilityDocumentView(
                docId,
                docId,
                item.name(),
                "MCP_SERVER_TEMPLATE",
                docType,
                content,
                item.tags()
        );
    }

    private String readOrFallbackSkillSummary(Path skillDir, SkillManifest manifest) {
        Path summaryPath = skillDir.resolve("summary.md");
        if (Files.exists(summaryPath)) {
            return readMarkdown(summaryPath);
        }
        return """
                # %s

                %s

                适用标签：%s

                使用建议：
                - 适合在需要 %s 时优先考虑。
                - 如果只是普通闲聊或不需要该 Skill 的专门策略，可以不用加载详情文档。
                """.formatted(
                manifest.name(),
                blankToFallback(manifest.description(), messageService.get("capability.doc.fallback.noDescription")),
                manifest.tags().isEmpty() ? messageService.get("capability.doc.fallback.noTags") : String.join(" / ", manifest.tags()),
                manifest.name()
        ).trim();
    }

    private String readOrFallbackSkillDetail(Path skillDir, SkillManifest manifest) {
        Path detailPath = skillDir.resolve("detail.md");
        if (Files.exists(detailPath)) {
            return readMarkdown(detailPath);
        }
        return """
                # %s 详情说明

                ## 能力定位
                %s

                ## 输入与触发
                - 输入来源：用户消息与当前轮输入 JSON
                - 触发方式：由模型或 planner 选择执行

                ## 关键提示
                %s

                ## 适用标签
                %s
                """.formatted(
                manifest.name(),
                blankToFallback(manifest.description(), messageService.get("capability.doc.fallback.noDescription")),
                blankToFallback(manifest.prompt(), messageService.get("capability.doc.fallback.noPrompt")),
                manifest.tags().isEmpty() ? messageService.get("capability.doc.fallback.noTags") : String.join(" / ", manifest.tags())
        ).trim();
    }

    private String buildCatalogSkillSummary(com.example.agentruntime.catalog.CatalogItem item) {
        return """
                # %s

                %s

                来源：内置 Skill 目录模板
                标签：%s

                使用建议：
                - 先看本摘要判断是否适合当前任务。
                - 真正安装前，如需更细信息可继续读取 detail 文档。
                """.formatted(
                item.name(),
                blankToFallback(item.description(), messageService.get("capability.doc.fallback.noDescription")),
                item.tags().isEmpty() ? messageService.get("capability.doc.fallback.noTags") : String.join(" / ", item.tags())
        ).trim();
    }

    private String buildCatalogSkillDetail(com.example.agentruntime.catalog.CatalogItem item) {
        return """
                # %s 详情说明

                ## 能力定位
                %s

                ## 安装方式
                - 安装模式：%s
                - 安装提示：%s

                ## 标签
                %s
                """.formatted(
                item.name(),
                blankToFallback(item.description(), messageService.get("capability.doc.fallback.noDescription")),
                item.installMode(),
                blankToFallback(item.installHint(), messageService.get("capability.doc.fallback.noInstallHint")),
                item.tags().isEmpty() ? messageService.get("capability.doc.fallback.noTags") : String.join(" / ", item.tags())
        ).trim();
    }

    private String buildCatalogMcpSummary(com.example.agentruntime.catalog.CatalogItem item) {
        return """
                # %s

                %s

                传输方式：%s
                标签：%s

                使用建议：
                - 适合先判断当前任务是否需要接入这类 MCP 服务。
                - 如果要看具体安装字段，再读取 detail 文档。
                """.formatted(
                item.name(),
                blankToFallback(item.description(), messageService.get("capability.doc.fallback.noDescription")),
                blankToFallback(item.transport(), messageService.get("capability.doc.fallback.noTransport")),
                item.tags().isEmpty() ? messageService.get("capability.doc.fallback.noTags") : String.join(" / ", item.tags())
        ).trim();
    }

    private String buildCatalogMcpDetail(com.example.agentruntime.catalog.CatalogItem item) {
        StringBuilder fieldBuilder = new StringBuilder();
        item.installFields().forEach(field -> fieldBuilder
                .append("- ")
                .append(field.label())
                .append("（key: ")
                .append(field.key())
                .append(field.required() ? "，必填" : "，可选")
                .append("）：")
                .append(blankToFallback(field.description(), messageService.get("capability.doc.fallback.noFieldDescription")))
                .append('\n'));

        return """
                # %s 详情说明

                ## 能力定位
                %s

                ## 连接信息
                - 传输方式：%s
                - 安装模式：%s

                ## 安装字段
                %s
                """.formatted(
                item.name(),
                blankToFallback(item.description(), messageService.get("capability.doc.fallback.noDescription")),
                blankToFallback(item.transport(), messageService.get("capability.doc.fallback.noTransport")),
                item.installMode(),
                fieldBuilder.toString().trim().isEmpty()
                        ? messageService.get("capability.doc.fallback.noFieldDescription")
                        : fieldBuilder.toString().trim()
        ).trim();
    }

    private SkillManifest loadSkillManifest(Path manifestPath) {
        Yaml yaml = new Yaml();
        try (InputStream inputStream = Files.newInputStream(manifestPath)) {
            @SuppressWarnings("unchecked")
            Map<String, Object> manifestMap = yaml.loadAs(inputStream, Map.class);
            if (manifestMap == null) {
                return null;
            }
            return new SkillManifest(
                    stringValue(manifestMap.get("id")),
                    stringValue(manifestMap.get("name")),
                    stringValue(manifestMap.get("version")),
                    stringValue(manifestMap.get("description")),
                    stringValue(manifestMap.get("type")),
                    stringValue(manifestMap.get("entry")),
                    stringValue(manifestMap.get("prompt")),
                    null,
                    manifestMap.get("tags") instanceof List<?> tags ? tags.stream().map(String::valueOf).toList() : List.of(),
                    List.of()
            );
        } catch (IOException exception) {
            throw new IllegalStateException(messageService.get("capability.doc.error.readFailed", manifestPath), exception);
        }
    }

    private String readMarkdown(Path path) {
        try {
            return Files.readString(path);
        } catch (IOException exception) {
            throw new IllegalStateException(messageService.get("capability.doc.error.readFailed", path), exception);
        }
    }

    private String searchableText(CapabilityDocumentSummary summary) {
        return String.join(" ",
                summary.docId(),
                summary.capabilityId(),
                summary.name(),
                blankToFallback(summary.summary(), ""),
                String.join(" ", summary.tags()))
                .toLowerCase(Locale.ROOT);
    }

    private String normalizeDocType(String docType) {
        String normalized = docType == null || docType.isBlank() ? DOC_TYPE_SUMMARY : docType.trim().toLowerCase(Locale.ROOT);
        if (DOC_TYPE_SUMMARY.equals(normalized) || DOC_TYPE_DETAIL.equals(normalized)) {
            return normalized;
        }
        throw new IllegalArgumentException(messageService.get("capability.doc.error.typeUnsupported", docType));
    }

    private String blankToFallback(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
