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

class McpInteractionControllerTest {

    @Test
    void shouldExposeToolsResourcesAndPromptsThroughController() {
        McpClient client = new FakeClient();
        McpClientRegistry registry = new McpClientRegistry(null, List.of(), null) {
            @Override
            public synchronized Optional<McpClient> find(String serverName) {
                return Optional.of(client);
            }
        };

        StaticMessageSource source = new StaticMessageSource();
        source.addMessage("mcp.error.serverNotFound", Locale.ENGLISH, "Unknown MCP server: {0}");
        MessageService messageService = new MessageService(source);
        McpInteractionController controller = new McpInteractionController(registry, messageService);

        assertEquals(1, controller.listTools("demo").size());
        assertEquals("demo", controller.callTool("demo", "echo_tool", new ObjectMapper().createObjectNode()).path("tool").asText());
        assertEquals(1, controller.listResources("demo").size());
        assertEquals("file://demo/readme.md", controller.readResource("demo", "file://demo/readme.md")
                .path("contents").get(0).path("uri").asText());
        assertEquals(1, controller.listPrompts("demo").size());
        assertEquals("Please summarize demo", controller.getPrompt(
                "demo",
                "summarize",
                new McpInteractionController.McpPromptArgumentsRequest(Map.of("topic", "demo"))
        ).messages().get(0).path("content").path("text").asText());
    }

    private static final class FakeClient implements McpClient {

        private final ObjectMapper objectMapper = new ObjectMapper();

        @Override
        public com.example.agentruntime.AgentRuntimeProperties.McpServerProperties server() {
            return null;
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
            return new McpCallResult(true, "ok", objectMapper.createObjectNode().put("tool", "demo"));
        }

        @Override
        public List<McpResourceDefinition> listResources() {
            return List.of(new McpResourceDefinition("file://demo/readme.md", "readme", "README", "desc", "text/markdown"));
        }

        @Override
        public com.fasterxml.jackson.databind.JsonNode readResource(String uri) {
            var root = objectMapper.createObjectNode();
            root.putArray("contents")
                    .addObject()
                    .put("uri", uri)
                    .put("text", "content");
            return root;
        }

        @Override
        public List<McpPromptDefinition> listPrompts() {
            return List.of(new McpPromptDefinition("summarize", "Summarize", "desc"));
        }

        @Override
        public McpPromptResult getPrompt(String name, Map<String, Object> arguments) {
            return new McpPromptResult(
                    "Prompt",
                    objectMapper.createArrayNode()
                            .add(objectMapper.createObjectNode()
                                    .put("role", "user")
                                    .set("content", objectMapper.createObjectNode()
                                            .put("type", "text")
                                            .put("text", "Please summarize " + arguments.get("topic"))))
            );
        }
    }
}
