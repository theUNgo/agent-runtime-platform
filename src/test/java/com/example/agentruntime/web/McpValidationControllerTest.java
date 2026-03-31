package com.example.agentruntime.web;

import com.example.agentruntime.i18n.MessageService;
import com.example.agentruntime.mcp.McpCallResult;
import com.example.agentruntime.mcp.McpCapabilitySnapshot;
import com.example.agentruntime.mcp.McpClient;
import com.example.agentruntime.mcp.McpClientRegistry;
import com.example.agentruntime.mcp.McpConnectionState;
import com.example.agentruntime.mcp.McpPromptDefinition;
import com.example.agentruntime.mcp.McpPromptResult;
import com.example.agentruntime.mcp.McpResourceDefinition;
import com.example.agentruntime.mcp.McpServerInfo;
import com.example.agentruntime.mcp.McpServerStatus;
import com.example.agentruntime.mcp.McpToolDefinition;
import com.example.agentruntime.mcp.McpTransportType;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.StaticMessageSource;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class McpValidationControllerTest {

    @Test
    void shouldBuildValidationReportFromClientCapabilities() {
        McpClient client = new FakeClient();
        McpClientRegistry registry = new McpClientRegistry(null, List.of(), null) {
            @Override
            public synchronized Optional<McpClient> find(String serverName) {
                return Optional.of(client);
            }

            @Override
            public synchronized Optional<com.example.agentruntime.AgentRuntimeProperties.McpServerProperties> findConfiguredServer(String serverName) {
                return Optional.of(client.server());
            }

            @Override
            public synchronized Optional<McpServerStatus> findStatus(String serverName) {
                return Optional.empty();
            }
        };

        StaticMessageSource source = new StaticMessageSource();
        source.addMessage("mcp.error.serverNotFound", Locale.ENGLISH, "Unknown MCP server: {0}");
        source.addMessage("mcp.validation.check.ok", Locale.ENGLISH, "ok");
        source.addMessage("mcp.validation.check.okPrefix", Locale.ENGLISH, "ok");
        source.addMessage("mcp.validation.check.okCount", Locale.ENGLISH, "ok ({0})");
        source.addMessage("mcp.validation.check.initFailed", Locale.ENGLISH, "initialization failed");
        source.addMessage("mcp.validation.check.unsupported", Locale.ENGLISH, "unsupported");
        source.addMessage("mcp.validation.summary.ok", Locale.ENGLISH, "Validation completed successfully.");
        source.addMessage("mcp.validation.summary.partial", Locale.ENGLISH, "Validation completed with issues.");
        source.addMessage("mcp.validation.diagnostic.serverReady", Locale.ENGLISH, "ready");
        source.addMessage("mcp.validation.diagnostic.serverReady.suggestion", Locale.ENGLISH, "continue");
        source.addMessage("mcp.validation.diagnostic.genericFailure", Locale.ENGLISH, "generic");
        source.addMessage("mcp.validation.diagnostic.genericFailure.suggestion", Locale.ENGLISH, "check logs");
        source.addMessage("mcp.validation.diagnostic.stdio.commandMissing", Locale.ENGLISH, "missing command");
        source.addMessage("mcp.validation.diagnostic.stdio.commandMissing.suggestion", Locale.ENGLISH, "configure command");
        source.addMessage("mcp.validation.diagnostic.stdio.commandNotFound", Locale.ENGLISH, "command not found");
        source.addMessage("mcp.validation.diagnostic.stdio.commandNotFound.suggestion", Locale.ENGLISH, "check path");
        source.addMessage("mcp.validation.diagnostic.stdio.scriptNotFound", Locale.ENGLISH, "script not found");
        source.addMessage("mcp.validation.diagnostic.stdio.scriptNotFound.suggestion", Locale.ENGLISH, "use absolute path");
        source.addMessage("mcp.validation.diagnostic.stdio.javaVersionMismatch", Locale.ENGLISH, "java version mismatch");
        source.addMessage("mcp.validation.diagnostic.stdio.javaVersionMismatch.suggestion", Locale.ENGLISH, "use jdk21");
        source.addMessage("mcp.validation.diagnostic.http.urlMissing", Locale.ENGLISH, "missing url");
        source.addMessage("mcp.validation.diagnostic.http.urlMissing.suggestion", Locale.ENGLISH, "set url");
        source.addMessage("mcp.validation.diagnostic.http.unreachable", Locale.ENGLISH, "unreachable");
        source.addMessage("mcp.validation.diagnostic.http.unreachable.suggestion", Locale.ENGLISH, "check host");
        source.addMessage("mcp.validation.diagnostic.http.authFailed", Locale.ENGLISH, "auth failed");
        source.addMessage("mcp.validation.diagnostic.http.authFailed.suggestion", Locale.ENGLISH, "check auth");
        source.addMessage("mcp.validation.diagnostic.http.notFound", Locale.ENGLISH, "not found");
        source.addMessage("mcp.validation.diagnostic.http.notFound.suggestion", Locale.ENGLISH, "check route");
        source.addMessage("mcp.validation.diagnostic.timeout", Locale.ENGLISH, "timeout");
        source.addMessage("mcp.validation.diagnostic.timeout.suggestion", Locale.ENGLISH, "increase timeout");
        source.addMessage("error.internal", Locale.ENGLISH, "internal");
        MessageService messageService = new MessageService(source);

        McpValidationController controller = new McpValidationController(registry, messageService);
        var report = controller.validate("demo");

        assertTrue(report.reachable());
        assertTrue(report.configured());
        assertEquals(1, report.toolCount());
        assertEquals(1, report.resourceCount());
        assertEquals(1, report.promptCount());
        assertEquals("ok", report.checks().get("initialize"));
        assertEquals("Validation completed successfully.", report.summary());
        assertEquals("stdio: java -jar demo.jar", report.configurationSummary());
        assertEquals("server.ready", report.diagnostics().getFirst().code());
    }

    @Test
    void shouldReturnDiagnosticReportWhenServerFailedToInitializeEarlier() {
        McpClientRegistry registry = new McpClientRegistry(
                new com.example.agentruntime.AgentRuntimeProperties(
                        "./skills",
                        ".",
                        null,
                        new com.example.agentruntime.AgentRuntimeProperties.McpProperties(List.of(
                                new com.example.agentruntime.AgentRuntimeProperties.McpServerProperties(
                                        "broken-stdio",
                                        "broken",
                                        true,
                                        "stdio",
                                        "powershell.exe",
                                        List.of("-File", "./scripts/missing.ps1"),
                                        null,
                                        Map.of(),
                                        Map.of(),
                                        java.time.Duration.ofSeconds(5)
                                )
                        )),
                        null
                ),
                List.of(),
                null
        ) {
            @Override
            public synchronized Optional<McpClient> find(String serverName) {
                return Optional.empty();
            }

            @Override
            public synchronized Optional<McpServerStatus> findStatus(String serverName) {
                return Optional.of(new McpServerStatus(
                        "broken-stdio",
                        McpTransportType.STDIO,
                        McpConnectionState.ERROR,
                        false,
                        null,
                        null,
                        new McpCapabilitySnapshot(false, false, false, false, false),
                        "failed",
                        "File ./scripts/missing.ps1 does not exist"
                ));
            }
        };

        StaticMessageSource source = new StaticMessageSource();
        source.addMessage("mcp.error.serverNotFound", Locale.ENGLISH, "Unknown MCP server: {0}");
        source.addMessage("mcp.validation.check.ok", Locale.ENGLISH, "ok");
        source.addMessage("mcp.validation.check.okPrefix", Locale.ENGLISH, "ok");
        source.addMessage("mcp.validation.check.okCount", Locale.ENGLISH, "ok ({0})");
        source.addMessage("mcp.validation.check.initFailed", Locale.ENGLISH, "initialization failed");
        source.addMessage("mcp.validation.check.unsupported", Locale.ENGLISH, "unsupported");
        source.addMessage("mcp.validation.summary.ok", Locale.ENGLISH, "Validation completed successfully.");
        source.addMessage("mcp.validation.summary.partial", Locale.ENGLISH, "Validation completed with issues.");
        source.addMessage("mcp.validation.diagnostic.serverReady", Locale.ENGLISH, "ready");
        source.addMessage("mcp.validation.diagnostic.serverReady.suggestion", Locale.ENGLISH, "continue");
        source.addMessage("mcp.validation.diagnostic.genericFailure", Locale.ENGLISH, "generic");
        source.addMessage("mcp.validation.diagnostic.genericFailure.suggestion", Locale.ENGLISH, "check logs");
        source.addMessage("mcp.validation.diagnostic.stdio.commandMissing", Locale.ENGLISH, "missing command");
        source.addMessage("mcp.validation.diagnostic.stdio.commandMissing.suggestion", Locale.ENGLISH, "configure command");
        source.addMessage("mcp.validation.diagnostic.stdio.commandNotFound", Locale.ENGLISH, "command not found");
        source.addMessage("mcp.validation.diagnostic.stdio.commandNotFound.suggestion", Locale.ENGLISH, "check path");
        source.addMessage("mcp.validation.diagnostic.stdio.scriptNotFound", Locale.ENGLISH, "script not found");
        source.addMessage("mcp.validation.diagnostic.stdio.scriptNotFound.suggestion", Locale.ENGLISH, "use absolute path");
        source.addMessage("mcp.validation.diagnostic.stdio.javaVersionMismatch", Locale.ENGLISH, "java version mismatch");
        source.addMessage("mcp.validation.diagnostic.stdio.javaVersionMismatch.suggestion", Locale.ENGLISH, "use jdk21");
        source.addMessage("mcp.validation.diagnostic.http.urlMissing", Locale.ENGLISH, "missing url");
        source.addMessage("mcp.validation.diagnostic.http.urlMissing.suggestion", Locale.ENGLISH, "set url");
        source.addMessage("mcp.validation.diagnostic.http.unreachable", Locale.ENGLISH, "unreachable");
        source.addMessage("mcp.validation.diagnostic.http.unreachable.suggestion", Locale.ENGLISH, "check host");
        source.addMessage("mcp.validation.diagnostic.http.authFailed", Locale.ENGLISH, "auth failed");
        source.addMessage("mcp.validation.diagnostic.http.authFailed.suggestion", Locale.ENGLISH, "check auth");
        source.addMessage("mcp.validation.diagnostic.http.notFound", Locale.ENGLISH, "not found");
        source.addMessage("mcp.validation.diagnostic.http.notFound.suggestion", Locale.ENGLISH, "check route");
        source.addMessage("mcp.validation.diagnostic.timeout", Locale.ENGLISH, "timeout");
        source.addMessage("mcp.validation.diagnostic.timeout.suggestion", Locale.ENGLISH, "increase timeout");
        source.addMessage("error.internal", Locale.ENGLISH, "internal");
        MessageService messageService = new MessageService(source);

        McpValidationController controller = new McpValidationController(registry, messageService);
        var report = controller.validate("broken-stdio");

        assertTrue(report.configured());
        assertTrue(!report.reachable());
        assertEquals("stdio: powershell.exe -File ./scripts/missing.ps1", report.configurationSummary());
        assertEquals("stdio.scriptNotFound", report.diagnostics().getFirst().code());
        assertEquals("Validation completed with issues.", report.summary());
    }

    private static final class FakeClient implements McpClient {

        private final ObjectMapper objectMapper = new ObjectMapper();

        @Override
        public com.example.agentruntime.AgentRuntimeProperties.McpServerProperties server() {
            return new com.example.agentruntime.AgentRuntimeProperties.McpServerProperties(
                    "demo",
                    "demo",
                    true,
                    "stdio",
                    "java",
                    List.of("-jar", "demo.jar"),
                    null,
                    Map.of(),
                    Map.of(),
                    java.time.Duration.ofSeconds(5)
            );
        }

        @Override
        public McpTransportType transportType() {
            return McpTransportType.STDIO;
        }

        @Override
        public McpServerStatus initialize() {
            return health();
        }

        @Override
        public McpServerStatus health() {
            return new McpServerStatus(
                    "demo",
                    McpTransportType.STDIO,
                    McpConnectionState.INITIALIZED,
                    true,
                    "2025-11-25",
                    new McpServerInfo("demo", "Demo", "1.0.0", null),
                    new McpCapabilitySnapshot(true, true, true, false, false),
                    "ok",
                    null
            );
        }

        @Override
        public McpServerInfo serverInfo() {
            return health().serverInfo();
        }

        @Override
        public McpCapabilitySnapshot serverCapabilities() {
            return health().capabilities();
        }

        @Override
        public List<McpToolDefinition> listTools() {
            return List.of(new McpToolDefinition("echo_tool", "Echo Tool", "desc", objectMapper.createObjectNode(), null));
        }

        @Override
        public McpCallResult callTool(String toolName, com.fasterxml.jackson.databind.JsonNode input) {
            return new McpCallResult(true, "ok", objectMapper.createObjectNode());
        }

        @Override
        public List<McpResourceDefinition> listResources() {
            return List.of(new McpResourceDefinition("file://demo/readme.md", "readme", "README", "desc", "text/markdown"));
        }

        @Override
        public com.fasterxml.jackson.databind.JsonNode readResource(String uri) {
            return objectMapper.createObjectNode();
        }

        @Override
        public List<McpPromptDefinition> listPrompts() {
            return List.of(new McpPromptDefinition("summarize", "Summarize", "desc"));
        }

        @Override
        public McpPromptResult getPrompt(String name, Map<String, Object> arguments) {
            return new McpPromptResult("Prompt", objectMapper.createArrayNode());
        }
    }
}
