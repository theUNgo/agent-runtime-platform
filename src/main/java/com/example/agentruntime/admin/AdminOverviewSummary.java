package com.example.agentruntime.admin;

/**
 * 管理员首页摘要统计。
 * 这里优先展示平台当前最关心的资源规模、启用关系和调用规模。
 */
public record AdminOverviewSummary(
        int totalUsers,
        int activeUsers,
        int adminUsers,
        int managedMcpServers,
        int managedSkills,
        int managedModels,
        int enabledCatalogSelections,
        int approvedModelSelections,
        int pendingModelApprovals,
        long totalCapabilityInvocations
) {
}
