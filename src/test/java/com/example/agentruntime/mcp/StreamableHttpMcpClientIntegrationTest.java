package com.example.agentruntime.mcp;

import com.example.agentruntime.AgentRuntimeProperties;
import com.example.agentruntime.i18n.MessageService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.StaticMessageSource;

import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StreamableHttpMcpClientIntegrationTest {

    private FakeStreamableHttpMcpServer server;
    private StreamableHttpMcpClient client;

    @AfterEach
    void tearDown() {
        if (client != null) {
            client.close();
        }
        if (server != null) {
            server.close();
        }
    }

    @Test
    void shouldInitializeListToolsAndCallToolViaOfficialStreamableHttpTransport() throws Exception {
        Locale.setDefault(Locale.ENGLISH);
        server = new FakeStreamableHttpMcpServer();
        client = new StreamableHttpMcpClient(serverProperties(server.baseUrl()),
                new com.fasterxml.jackson.databind.ObjectMapper(),
                messageService());

        McpServerStatus status = client.initialize();
        assertTrue(status.initialized());
        assertEquals("2025-11-25", status.protocolVersion());

        var tools = client.listTools();
        assertEquals(1, tools.size());
        assertEquals("echo_tool", tools.getFirst().name());

        McpCallResult result = client.callTool("echo_tool",
                new com.fasterxml.jackson.databind.ObjectMapper().createObjectNode().put("text", "http"));
        assertTrue(result.success());
        assertEquals("http", result.output().path("structuredContent").path("echo").asText());

        var resources = client.listResources();
        assertEquals(1, resources.size());
        assertEquals("file://docs/http-readme.md", resources.getFirst().uri());
        assertEquals("fake http content", client.readResource("file://docs/http-readme.md")
                .path("contents").get(0).path("text").asText().replace("# HTTP README\n", ""));

        var prompts = client.listPrompts();
        assertEquals(1, prompts.size());
        assertEquals("summarize", prompts.getFirst().name());
        McpPromptResult prompt = client.getPrompt("summarize", Map.of("topic", "http"));
        assertEquals("Prompt for summarize", prompt.description());
        assertEquals("Please summarize http", prompt.messages().get(0).path("content").path("text").asText());
    }

    private AgentRuntimeProperties.McpServerProperties serverProperties(String url) {
        return new AgentRuntimeProperties.McpServerProperties(
                "fake-http",
                "fake streamable http test server",
                true,
                "streamable-http",
                null,
                List.of(),
                url,
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
        source.addMessage("mcp.http.initialize.success", Locale.ENGLISH, "http init {0}");
        source.addMessage("mcp.http.init.missingUrl", Locale.ENGLISH, "missing url");
        source.addMessage("capability.mcp.descriptor.version", Locale.ENGLISH, "sdk");
        return new MessageService(source);
    }
}
