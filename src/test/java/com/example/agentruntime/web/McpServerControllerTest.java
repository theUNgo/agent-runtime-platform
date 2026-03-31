package com.example.agentruntime.web;

import com.example.agentruntime.i18n.MessageService;
import com.example.agentruntime.mcp.McpCapabilitySnapshot;
import com.example.agentruntime.mcp.McpClientRegistry;
import com.example.agentruntime.mcp.McpConnectionState;
import com.example.agentruntime.mcp.McpServerInfo;
import com.example.agentruntime.mcp.McpServerStatus;
import com.example.agentruntime.mcp.McpTransportType;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.StaticMessageSource;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class McpServerControllerTest {

    @Test
    void shouldReturnServerStatuses() {
        McpServerStatus status = new McpServerStatus(
                "demo",
                McpTransportType.STDIO,
                McpConnectionState.INITIALIZED,
                true,
                "2025-11-25",
                new McpServerInfo("demo", "Demo", "1.0.0", null),
                new McpCapabilitySnapshot(true, false, false, false, false),
                "ok",
                null
        );

        McpClientRegistry registry = new McpClientRegistry(null, List.of(), null) {
            @Override
            public synchronized List<McpServerStatus> listStatuses() {
                return List.of(status);
            }

            @Override
            public synchronized Optional<McpServerStatus> findStatus(String serverName) {
                return Optional.of(status);
            }

            @Override
            public synchronized void refresh() {
            }
        };

        StaticMessageSource source = new StaticMessageSource();
        source.addMessage("mcp.error.serverNotFound", Locale.ENGLISH, "Unknown MCP server: {0}");
        McpServerController controller = new McpServerController(registry, new MessageService(source));

        assertEquals(1, controller.list().size());
        assertEquals("demo", controller.get("demo").serverName());
        assertEquals(1, controller.refresh().size());
    }
}
