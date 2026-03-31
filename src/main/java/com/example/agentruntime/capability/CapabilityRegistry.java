package com.example.agentruntime.capability;

import java.util.List;
import java.util.Optional;

/**
 * 能力注册中心。
 * 负责聚合所有 Provider 暴露出来的能力，并提供检索与解析能力。
 */
public interface CapabilityRegistry {

    void refresh();

    List<CapabilityDescriptor> listAll();

    List<CapabilityDescriptor> search(String query);

    Optional<AgentCapability> get(String capabilityId);
}
