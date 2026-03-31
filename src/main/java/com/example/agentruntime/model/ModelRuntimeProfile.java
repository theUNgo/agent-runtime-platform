package com.example.agentruntime.model;

/**
 * 仅供服务端运行时使用的模型档案快照。
 * 这里会包含真正的 apiKey，因此不能直接透传到前端。
 */
public record ModelRuntimeProfile(
        Long profileId,
        String profileName,
        String providerType,
        String baseUrl,
        String apiKey,
        String modelId,
        boolean supportsText,
        boolean supportsVision,
        boolean supportsAudio,
        boolean preferredForGeneralChat,
        boolean preferredForTools,
        boolean preferredForSkills,
        boolean enabled
) {
}
