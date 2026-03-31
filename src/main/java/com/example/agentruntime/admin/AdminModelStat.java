package com.example.agentruntime.admin;

/**
 * 全局模型统计项。
 * 这里聚焦审批、启用和默认选择三类与平台治理最相关的指标。
 */
public record AdminModelStat(
        long profileId,
        String profileName,
        String providerType,
        String modelId,
        boolean enabled,
        int approvedUserCount,
        int pendingUserCount,
        int defaultUserCount
) {
}
