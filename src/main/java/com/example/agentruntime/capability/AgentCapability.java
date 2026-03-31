package com.example.agentruntime.capability;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * 系统内部统一能力接口。
 * 无论底层是内置能力、Skill 还是 MCP Tool，最终都会抽象成同一套调用方式。
 */
public interface AgentCapability {

    CapabilityDescriptor descriptor();

    CapabilityResult execute(CapabilityContext context, JsonNode input);
}
