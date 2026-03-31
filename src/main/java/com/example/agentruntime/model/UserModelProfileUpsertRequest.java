package com.example.agentruntime.model;

/**
 * 用户模型档案写入请求。
 */
public record UserModelProfileUpsertRequest(
        String profileName,
        String providerType,
        String baseUrl,
        String apiKey,
        String apiKeyEncrypted,
        String modelId,
        boolean supportsText,
        boolean supportsVision,
        boolean supportsAudio,
        boolean preferredForGeneralChat,
        boolean preferredForTools,
        boolean preferredForSkills,
        boolean enabled,
        boolean makeDefault
) {
}
