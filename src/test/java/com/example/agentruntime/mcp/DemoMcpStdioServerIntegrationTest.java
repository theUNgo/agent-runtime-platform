package com.example.agentruntime.mcp;

import com.example.agentruntime.AgentRuntimeProperties;
import com.example.agentruntime.demo.DemoMcpStdioServer;
import com.example.agentruntime.i18n.MessageService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.StaticMessageSource;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DemoMcpStdioServerIntegrationTest {

    private StdioMcpClient client;

    @AfterEach
    void tearDown() {
        if (client != null) {
            client.close();
        }
    }

    @Test
    void shouldValidateBuiltInDemoServerViaOfficialStdioClient() {
        Locale.setDefault(Locale.ENGLISH);
        client = new StdioMcpClient(
                serverProperties(),
                Path.of("").toAbsolutePath().normalize(),
                new com.fasterxml.jackson.databind.ObjectMapper(),
                messageService()
        );

        McpServerStatus status = client.initialize();
        assertTrue(status.initialized());
        assertEquals("2025-11-25", status.protocolVersion());
        assertEquals("demo-stdio", status.serverInfo().name());

        assertEquals(2, client.listTools().size());
        assertEquals(1, client.listResources().size());
        assertEquals(1, client.listPrompts().size());
        assertEquals("Demo summary for integration", client.callTool(
                "summarize_topic",
                new com.fasterxml.jackson.databind.ObjectMapper().createObjectNode().put("topic", "integration")
        ).output().path("structuredContent").path("summary").asText());
    }

    private AgentRuntimeProperties.McpServerProperties serverProperties() {
        String javaBin = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        String classpath = System.getProperty("java.class.path");
        return new AgentRuntimeProperties.McpServerProperties(
                "demo-stdio",
                "built-in demo stdio mcp server",
                true,
                "stdio",
                javaBin,
                List.of("-cp", classpath, DemoMcpStdioServer.class.getName()),
                null,
                Map.of(),
                Map.of(),
                Duration.ofSeconds(5)
        );
    }

    private MessageService messageService() {
        StaticMessageSource source = new StaticMessageSource();
        source.addMessage("mcp.status.disconnected", Locale.ENGLISH, "disconnected");
        source.addMessage("mcp.status.connecting", Locale.ENGLISH, "connecting");
        source.addMessage("mcp.status.closed", Locale.ENGLISH, "closed");
        source.addMessage("mcp.status.toolsChanged", Locale.ENGLISH, "tools changed");
        source.addMessage("mcp.status.resourcesChanged", Locale.ENGLISH, "resources changed");
        source.addMessage("mcp.status.promptsChanged", Locale.ENGLISH, "prompts changed");
        source.addMessage("mcp.status.loggingReceived", Locale.ENGLISH, "log");
        source.addMessage("mcp.status.progressReceived", Locale.ENGLISH, "progress");
        source.addMessage("mcp.error.initialize.failed", Locale.ENGLISH, "init failed");
        source.addMessage("mcp.error.tools.listFailed", Locale.ENGLISH, "list tools failed");
        source.addMessage("mcp.error.tools.callFailed", Locale.ENGLISH, "call tool failed: {0}");
        source.addMessage("mcp.error.resources.listFailed", Locale.ENGLISH, "list resources failed");
        source.addMessage("mcp.error.resources.readFailed", Locale.ENGLISH, "read resource failed: {0}");
        source.addMessage("mcp.error.prompts.listFailed", Locale.ENGLISH, "list prompts failed");
        source.addMessage("mcp.error.prompts.getFailed", Locale.ENGLISH, "get prompt failed: {0}");
        source.addMessage("mcp.error.capability.toolsUnsupported", Locale.ENGLISH, "tools unsupported: {0}");
        source.addMessage("mcp.error.capability.resourcesUnsupported", Locale.ENGLISH, "resources unsupported: {0}");
        source.addMessage("mcp.error.capability.promptsUnsupported", Locale.ENGLISH, "prompts unsupported: {0}");
        source.addMessage("mcp.initialize.success", Locale.ENGLISH, "init success {0}");
        source.addMessage("mcp.tool.call.success", Locale.ENGLISH, "tool success {0}");
        source.addMessage("mcp.stdio.initialize.success", Locale.ENGLISH, "stdio init {0}");
        source.addMessage("mcp.stdio.init.missingCommand", Locale.ENGLISH, "missing command");
        source.addMessage("capability.mcp.descriptor.version", Locale.ENGLISH, "sdk");
        return new MessageService(source);
    }
}
