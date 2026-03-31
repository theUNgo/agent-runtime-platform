package com.example.agentruntime.mcp;

import com.example.agentruntime.AgentRuntimeProperties;
import com.example.agentruntime.i18n.MessageService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.StaticMessageSource;

import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class McpClientRegistryTest {

    @Test
    void shouldKeepHealthyServersWhenOneServerFailsToInitialize() {
        MessageService messageService = messageService();
        AgentRuntimeProperties properties = new AgentRuntimeProperties(
                "./skills",
                ".",
                null,
                new AgentRuntimeProperties.McpProperties(List.of(
                        server("healthy", "stdio"),
                        server("broken", "streamable-http")
                )),
                null
        );

        McpClientRegistry registry = new McpClientRegistry(
                properties,
                List.of(new HealthyFactory(messageService), new FailingFactory()),
                messageService
        );

        registry.refresh();

        assertEquals(1, registry.listClients().size());
        assertEquals(2, registry.listStatuses().size());
        assertTrue(registry.findStatus("healthy").orElseThrow().initialized());
        McpServerStatus failed = registry.findStatus("broken").orElseThrow();
        assertFalse(failed.initialized());
        assertEquals(McpConnectionState.ERROR, failed.connectionState());
        assertEquals("boom", failed.lastError());
    }

    private AgentRuntimeProperties.McpServerProperties server(String name, String transport) {
        return new AgentRuntimeProperties.McpServerProperties(
                name,
                name + " description",
                true,
                transport,
                null,
                List.of(),
                null,
                Map.of(),
                Map.of(),
                Duration.ofSeconds(2)
        );
    }

    private MessageService messageService() {
        StaticMessageSource source = new StaticMessageSource();
        source.addMessage("mcp.error.unsupportedTransport", Locale.ENGLISH, "Unsupported MCP transport: {0}");
        source.addMessage("mcp.error.unsupportedTransport", Locale.CHINA, "Unsupported MCP transport: {0}");
        source.addMessage("mcp.error.noFactory", Locale.ENGLISH, "No MCP client factory for transport {0}");
        source.addMessage("mcp.error.noFactory", Locale.CHINA, "No MCP client factory for transport {0}");
        source.addMessage("mcp.registry.serverInitFailed", Locale.ENGLISH, "Failed to initialize MCP server {0}.");
        source.addMessage("mcp.registry.serverInitFailed", Locale.CHINA, "Failed to initialize MCP server {0}.");
        source.addMessage("mcp.status.disconnected", Locale.ENGLISH, "disconnected");
        source.addMessage("mcp.status.disconnected", Locale.CHINA, "disconnected");
        return new MessageService(source);
    }

    private static final class HealthyFactory implements McpClientFactory {

        private final MessageService messageService;

        private HealthyFactory(MessageService messageService) {
            this.messageService = messageService;
        }

        @Override
        public boolean supports(McpTransportType transportType) {
            return transportType == McpTransportType.STDIO;
        }

        @Override
        public McpClient create(AgentRuntimeProperties.McpServerProperties server) {
            return new FakeHealthyClient(server, messageService);
        }
    }

    private static final class FailingFactory implements McpClientFactory {

        @Override
        public boolean supports(McpTransportType transportType) {
            return transportType == McpTransportType.STREAMABLE_HTTP;
        }

        @Override
        public McpClient create(AgentRuntimeProperties.McpServerProperties server) {
            return new McpClient() {
                @Override
                public AgentRuntimeProperties.McpServerProperties server() {
                    return server;
                }

                @Override
                public McpTransportType transportType() {
                    return McpTransportType.STREAMABLE_HTTP;
                }

                @Override
                public McpServerStatus initialize() {
                    throw new McpException(McpErrorCode.MCP_TRANSPORT_ERROR, "broken", new RuntimeException("boom"));
                }

                @Override
                public McpServerStatus health() {
                    return null;
                }

                @Override
                public McpServerInfo serverInfo() {
                    return null;
                }

                @Override
                public McpCapabilitySnapshot serverCapabilities() {
                    return null;
                }

                @Override
                public List<McpToolDefinition> listTools() {
                    return List.of();
                }

                @Override
                public McpCallResult callTool(String toolName, com.fasterxml.jackson.databind.JsonNode input) {
                    return null;
                }

                @Override
                public List<McpResourceDefinition> listResources() {
                    return List.of();
                }

                @Override
                public com.fasterxml.jackson.databind.JsonNode readResource(String uri) {
                    return null;
                }

                @Override
                public List<McpPromptDefinition> listPrompts() {
                    return List.of();
                }

                @Override
                public McpPromptResult getPrompt(String name, Map<String, Object> arguments) {
                    return null;
                }
            };
        }
    }

    private static final class FakeHealthyClient implements McpClient {

        private final AgentRuntimeProperties.McpServerProperties server;
        private final ObjectMapper objectMapper = new ObjectMapper();
        private final MessageService messageService;

        private FakeHealthyClient(AgentRuntimeProperties.McpServerProperties server, MessageService messageService) {
            this.server = server;
            this.messageService = messageService;
        }

        @Override
        public AgentRuntimeProperties.McpServerProperties server() {
            return server;
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
                    server.name(),
                    McpTransportType.STDIO,
                    McpConnectionState.INITIALIZED,
                    true,
                    "2025-11-25",
                    new McpServerInfo(server.name(), "Healthy", "1.0.0", null),
                    new McpCapabilitySnapshot(true, false, false, false, false),
                    messageService.get("mcp.status.disconnected"),
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
            return List.of();
        }

        @Override
        public com.fasterxml.jackson.databind.JsonNode readResource(String uri) {
            return objectMapper.createObjectNode();
        }

        @Override
        public List<McpPromptDefinition> listPrompts() {
            return List.of();
        }

        @Override
        public McpPromptResult getPrompt(String name, Map<String, Object> arguments) {
            return new McpPromptResult("", objectMapper.createArrayNode());
        }
    }
}
