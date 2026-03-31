package com.example.agentruntime.mcp;

import com.example.agentruntime.AgentRuntimeProperties;

public interface McpClientFactory {

    boolean supports(McpTransportType transportType);

    McpClient create(AgentRuntimeProperties.McpServerProperties server);
}
