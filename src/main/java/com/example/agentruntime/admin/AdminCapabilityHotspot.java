package com.example.agentruntime.admin;

/**
 * 能力调用热点统计项。
 * 用于帮助管理员判断当前平台最常被调用的是哪些能力，以及它们的成功/失败情况。
 */
public record AdminCapabilityHotspot(
        String capabilityId,
        String capabilityName,
        String capabilityType,
        String provider,
        long invocationCount,
        long successCount,
        long failureCount
) {
}
