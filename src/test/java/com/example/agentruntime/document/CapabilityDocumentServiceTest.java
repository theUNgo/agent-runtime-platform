package com.example.agentruntime.document;

import com.example.agentruntime.AgentRuntimeProperties;
import com.example.agentruntime.catalog.CatalogInstallField;
import com.example.agentruntime.catalog.CatalogInstallMode;
import com.example.agentruntime.catalog.CatalogItem;
import com.example.agentruntime.catalog.CatalogItemType;
import com.example.agentruntime.catalog.UnifiedCatalogService;
import com.example.agentruntime.i18n.MessageService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.context.support.StaticMessageSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CapabilityDocumentServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldReadLocalSkillMarkdownWhenFilesExist() throws Exception {
        Path skillsDir = tempDir.resolve("skills");
        Path skillDir = skillsDir.resolve("demo-skill");
        Files.createDirectories(skillDir);
        Files.writeString(skillDir.resolve("skill.yaml"), """
                id: demo-skill
                name: Demo Skill
                version: 1.0.0
                description: 用于测试本地 skill 文档读取。
                type: prompt
                prompt: |
                  这是一个测试 skill。
                tags:
                  - testing
                  - local
                """);
        Files.writeString(skillDir.resolve("summary.md"), "# Demo Skill\n\n这是本地摘要。");
        Files.writeString(skillDir.resolve("detail.md"), "# Demo Skill Detail\n\n这是本地详情。");

        UnifiedCatalogService catalogService = mock(UnifiedCatalogService.class);
        when(catalogService.search(null, null)).thenReturn(List.of());

        CapabilityDocumentService service = new CapabilityDocumentService(
                properties(skillsDir),
                catalogService,
                messageService()
        );

        CapabilityDocumentView summary = service.read("skill:demo-skill", "summary");
        CapabilityDocumentView detail = service.read("skill:demo-skill", "detail");

        assertEquals("SKILL", summary.capabilityType());
        assertTrue(summary.content().contains("本地摘要"));
        assertTrue(detail.content().contains("本地详情"));
    }

    @Test
    void shouldFallbackToCatalogGeneratedDocuments() {
        UnifiedCatalogService catalogService = mock(UnifiedCatalogService.class);
        when(catalogService.search(null, null)).thenReturn(List.of(
                new CatalogItem(
                        "filesystem-local",
                        "Filesystem Local Server",
                        CatalogItemType.MCP_SERVER,
                        "curated-mcp-catalog",
                        "0.1.0",
                        "本地文件系统 MCP 模板。",
                        List.of("filesystem", "stdio"),
                        false,
                        "NOT_INSTALLED",
                        CatalogInstallMode.DIRECT,
                        "stdio",
                        "先安装再使用",
                        List.of(new CatalogInstallField("rootPath", "工作目录", "", true, false, "", "根目录"))
                )
        ));
        when(catalogService.search(null, CatalogItemType.MCP_SERVER)).thenReturn(List.of(
                new CatalogItem(
                        "filesystem-local",
                        "Filesystem Local Server",
                        CatalogItemType.MCP_SERVER,
                        "curated-mcp-catalog",
                        "0.1.0",
                        "本地文件系统 MCP 模板。",
                        List.of("filesystem", "stdio"),
                        false,
                        "NOT_INSTALLED",
                        CatalogInstallMode.DIRECT,
                        "stdio",
                        "先安装再使用",
                        List.of(new CatalogInstallField("rootPath", "工作目录", "", true, false, "", "根目录"))
                )
        ));

        CapabilityDocumentService service = new CapabilityDocumentService(
                properties(tempDir.resolve("skills")),
                catalogService,
                messageService()
        );

        CapabilityDocumentSummary summary = service.search("filesystem").getFirst();
        CapabilityDocumentView detail = service.read("catalog-mcp:filesystem-local", "detail");

        assertEquals("catalog-mcp:filesystem-local", summary.docId());
        assertTrue(summary.summary().contains("Filesystem Local Server"));
        assertTrue(detail.content().contains("安装字段"));
        assertTrue(detail.content().contains("工作目录"));
    }

    private AgentRuntimeProperties properties(Path skillsDir) {
        return new AgentRuntimeProperties(
                skillsDir.toString(),
                tempDir.toString(),
                new AgentRuntimeProperties.AuthProperties(true, Duration.ofDays(30), "admin", "test-bootstrap-password", "Admin"),
                new AgentRuntimeProperties.McpProperties(List.of()),
                new AgentRuntimeProperties.SecurityProperties(
                        new AgentRuntimeProperties.CryptoProperties("ChangeThisDevelopmentCryptoSecret-32CharsMin")
                )
        );
    }

    private MessageService messageService() {
        StaticMessageSource source = new StaticMessageSource();
        addMessage(source, "capability.doc.error.idRequired", "doc id required");
        addMessage(source, "capability.doc.error.notFound", "not found {0}");
        addMessage(source, "capability.doc.error.typeUnsupported", "unsupported {0}");
        addMessage(source, "capability.doc.error.scanFailed", "scan failed");
        addMessage(source, "capability.doc.error.readFailed", "read failed {0}");
        addMessage(source, "capability.doc.fallback.noDescription", "no description");
        addMessage(source, "capability.doc.fallback.noTags", "no tags");
        addMessage(source, "capability.doc.fallback.noPrompt", "no prompt");
        addMessage(source, "capability.doc.fallback.noInstallHint", "no install hint");
        addMessage(source, "capability.doc.fallback.noTransport", "unknown transport");
        addMessage(source, "capability.doc.fallback.noFieldDescription", "no field description");
        return new MessageService(source);
    }

    private void addMessage(StaticMessageSource source, String code, String message) {
        source.addMessage(code, Locale.ENGLISH, message);
        source.addMessage(code, Locale.SIMPLIFIED_CHINESE, message);
    }
}
