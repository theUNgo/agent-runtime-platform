package com.example.agentruntime.web;

import com.example.agentruntime.i18n.MessageService;
import com.example.agentruntime.mcp.McpClient;
import com.example.agentruntime.mcp.McpClientRegistry;
import com.example.agentruntime.mcp.McpPromptDefinition;
import com.example.agentruntime.mcp.McpPromptResult;
import com.example.agentruntime.mcp.McpResourceDefinition;
import com.example.agentruntime.mcp.McpToolDefinition;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 直接暴露 MCP 原生能力的宿主接口。
 * 这类接口面向调试、联调和管理后台，避免上层只能通过 Agent 间接访问 MCP。
 */
@Validated
@RestController
@RequestMapping("/api/mcp/servers/{serverName}")
public class McpInteractionController {

    private final McpClientRegistry clientRegistry;
    private final MessageService messageService;

    public McpInteractionController(McpClientRegistry clientRegistry, MessageService messageService) {
        this.clientRegistry = clientRegistry;
        this.messageService = messageService;
    }

    @GetMapping("/tools")
    public List<McpToolDefinition> listTools(@PathVariable("serverName") String serverName) {
        return requireClient(serverName).listTools();
    }

    @PostMapping("/tools/{toolName}")
    public JsonNode callTool(@PathVariable("serverName") String serverName,
                             @PathVariable("toolName") String toolName,
                             @RequestBody(required = false) JsonNode input) {
        return requireClient(serverName).callTool(toolName, input).output();
    }

    @GetMapping("/resources")
    public List<McpResourceDefinition> listResources(@PathVariable("serverName") String serverName) {
        return requireClient(serverName).listResources();
    }

    @GetMapping("/resources/read")
    public JsonNode readResource(@PathVariable("serverName") String serverName,
                                 @RequestParam("uri") @NotBlank String uri) {
        return requireClient(serverName).readResource(uri);
    }

    @GetMapping("/prompts")
    public List<McpPromptDefinition> listPrompts(@PathVariable("serverName") String serverName) {
        return requireClient(serverName).listPrompts();
    }

    @PostMapping("/prompts/{promptName}")
    public McpPromptResult getPrompt(@PathVariable("serverName") String serverName,
                                     @PathVariable("promptName") String promptName,
                                     @Valid @RequestBody(required = false) McpPromptArgumentsRequest request) {
        Map<String, Object> arguments = request == null ? Map.of() : request.arguments();
        return requireClient(serverName).getPrompt(promptName, arguments);
    }

    private McpClient requireClient(String serverName) {
        return clientRegistry.find(serverName)
                .orElseThrow(() -> new IllegalArgumentException(messageService.get("mcp.error.serverNotFound", serverName)));
    }

    /**
     * Prompt 参数包装对象。
     * 后续如果要挂接审批标记、调用来源等字段，可以继续在这里扩展。
     */
    public record McpPromptArgumentsRequest(Map<String, Object> arguments) {

        public McpPromptArgumentsRequest {
            arguments = arguments == null ? Map.of() : Map.copyOf(arguments);
        }
    }
}
