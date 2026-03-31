package com.example.agentruntime.model;

/**
 * 模型档案连通性验证结果。
 */
public record ModelValidationReport(
        Long profileId,
        String profileName,
        String providerType,
        String modelId,
        boolean reachable,
        String summary,
        String preview
) {
}
