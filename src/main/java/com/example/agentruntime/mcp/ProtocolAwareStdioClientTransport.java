package com.example.agentruntime.mcp;

import io.modelcontextprotocol.client.transport.ServerParameters;
import io.modelcontextprotocol.client.transport.StdioClientTransport;
import io.modelcontextprotocol.json.McpJsonMapper;

import java.util.List;

/**
 * 对官方 Stdio transport 做一层极薄的适配。
 * 官方实现默认只暴露 2024-11-05，无法满足当前工程以 2025-11-25 为主协议的协商目标；
 * 这里仅覆盖协议版本声明，进程管理和收发逻辑仍完全复用官方实现。
 */
public final class ProtocolAwareStdioClientTransport extends StdioClientTransport {

    private final List<String> protocolVersions;

    public ProtocolAwareStdioClientTransport(ServerParameters params,
                                             McpJsonMapper jsonMapper,
                                             List<String> protocolVersions) {
        super(params, jsonMapper);
        this.protocolVersions = List.copyOf(protocolVersions);
    }

    @Override
    public List<String> protocolVersions() {
        return protocolVersions;
    }
}
