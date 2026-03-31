package com.example.agentruntime.model;

/**
 * 用户模型档案视图。
 * 出于安全考虑，接口不会直接返回明文 apiKey。
 * 同时这里会带上用户当前的模型访问审批状态，便于前端决定显示“启用”“申请”还是“审批中”。
 */
public record UserModelProfileView(
        Long id,
        String profileName,
        String providerType,
        String baseUrl,
        String modelId,
        boolean supportsText,
        boolean supportsVision,
        boolean supportsAudio,
        boolean preferredForGeneralChat,
        boolean preferredForTools,
        boolean preferredForSkills,
        boolean enabled,
        boolean isDefault,
        boolean hasApiKey,
        String maskedApiKey,
        String accessStatus,
        String reviewComment
) {
}
