package com.example.agentruntime.web;

import com.example.agentruntime.i18n.MessageService;
import com.example.agentruntime.mcp.McpClientRegistry;
import com.example.agentruntime.mcp.McpServerStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * MCP Server 状态查询接口。
 * 用于查看每个已配置 server 的初始化结果、连接状态和最近错误，方便后续前端接入和排障。
 */
@RestController
@RequestMapping("/api/mcp/servers")
public class McpServerController {

    private final McpClientRegistry clientRegistry;
    private final MessageService messageService;

    public McpServerController(McpClientRegistry clientRegistry, MessageService messageService) {
        this.clientRegistry = clientRegistry;
        this.messageService = messageService;
    }

    @GetMapping
    public List<McpServerStatus> list() {
        return clientRegistry.listStatuses();
    }

    @GetMapping("/{serverName}")
    public McpServerStatus get(@PathVariable("serverName") String serverName) {
        return clientRegistry.findStatus(serverName)
                .orElseThrow(() -> new IllegalArgumentException(messageService.get("mcp.error.serverNotFound", serverName)));
    }

    @PostMapping("/refresh")
    public List<McpServerStatus> refresh() {
        clientRegistry.refresh();
        return clientRegistry.listStatuses();
    }
}
