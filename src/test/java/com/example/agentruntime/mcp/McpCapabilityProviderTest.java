package com.example.agentruntime.mcp;

import com.example.agentruntime.AgentRuntimeProperties;
import com.example.agentruntime.capability.AgentCapability;
import com.example.agentruntime.capability.CapabilityContext;
import com.example.agentruntime.capability.CapabilityResult;
import com.example.agentruntime.i18n.MessageService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.StaticMessageSource;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class McpCapabilityProviderTest {

    @Test
    void shouldExposeSdkBackedToolsAsUnifiedCapabilities() {
        MessageService messageService = messageService();
        McpClient fakeClient = new FakeMcpClient();
        McpClientRegistry registry = new McpClientRegistry(
                new AgentRuntimeProperties("./skills", ".", null, new AgentRuntimeProperties.McpProperties(List.of()), null),
                List.of(),
                messageService
        ) {
            @Override
            public synchronized List<McpClient> listClients() {
                return List.of(fakeClient);
            }

            @Override
            public synchronized void refresh() {
            }

            @Override
            public synchronized Optional<McpClient> find(String serverName) {
                return Optional.of(fakeClient);
            }
        };

        McpCapabilityProvider provider = new McpCapabilityProvider(registry, messageService);
        var descriptors = provider.discover();

        assertEquals(1, descriptors.size());
        assertTrue(descriptors.getFirst().id().startsWith("mcp:test-server:"));
        AgentCapability capability = provider.resolve(descriptors.getFirst().id()).orElseThrow();
        CapabilityResult result = capability.execute(new CapabilityContext("c1", "hello", "."), null);
        assertTrue(result.success());
        assertFalse(result.output().isEmpty());
    }

    private MessageService messageService() {
        StaticMessageSource source = new StaticMessageSource();
        source.addMessage("capability.mcp.descriptor.version", Locale.ENGLISH, "sdk");
        source.addMessage("capability.mcp.descriptor.version", Locale.CHINA, "sdk");
        source.addMessage("mcp.error.capability.toolsUnsupported", Locale.ENGLISH, "tools unsupported");
        source.addMessage("mcp.error.capability.toolsUnsupported", Locale.CHINA, "tools unsupported");
        return new MessageService(source);
    }

    private static final class FakeMcpClient implements McpClient {

        private final ObjectMapper objectMapper = new ObjectMapper();

        @Override
        public AgentRuntimeProperties.McpServerProperties server() {
            return new AgentRuntimeProperties.McpServerProperties(
                    "test-server", "test", true, "stdio", "echo", List.of(), null, Map.of(), Map.of(), java.time.Duration.ofSeconds(1));
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
                    "test-server",
                    McpTransportType.STDIO,
                    McpConnectionState.INITIALIZED,
                    true,
                    "2025-11-25",
                    new McpServerInfo("test-server", "Test", "1.0.0", null),
                    new McpCapabilitySnapshot(true, false, false, false, false),
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
            return new McpCallResult(true, "ok", objectMapper.createObjectNode().put("tool", toolName));
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
