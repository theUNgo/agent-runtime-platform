package com.example.agentruntime.capability;

import java.util.Optional;
import java.util.List;

/**
 * 能力提供者接口。
 * 一个 Provider 可以来自内置模块、Skill 系统或 MCP 服务器。
 */
public interface CapabilityProvider {

    String providerId();

    List<CapabilityDescriptor> discover();

    Optional<AgentCapability> resolve(String capabilityId);
}
