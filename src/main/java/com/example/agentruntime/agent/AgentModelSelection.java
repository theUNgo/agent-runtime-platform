package com.example.agentruntime.agent;

/**
 * 当前一次 Agent 执行所使用的模型选择信息。
 */
public record AgentModelSelection(
        Long profileId,
        String profileName,
        String providerType,
        String baseUrl,
        String modelId,
        boolean supportsVision,
        boolean supportsAudio
) {
}
