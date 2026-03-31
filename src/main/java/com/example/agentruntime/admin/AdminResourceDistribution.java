package com.example.agentruntime.admin;

/**
 * 全局资源分布统计项。
 * 当前用于展示不同资源类型在各状态下的数量分布，例如 ENABLED / DISABLED。
 */
public record AdminResourceDistribution(
        String itemType,
        String status,
        long count
) {
}
