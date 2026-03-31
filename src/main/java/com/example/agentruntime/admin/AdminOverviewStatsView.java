package com.example.agentruntime.admin;

import java.time.Instant;
import java.util.List;

/**
 * 管理员总览统计视图。
 * 这一层把全局资源、用户启用关系和调用热度汇总成一个可直接给前端展示的结构。
 */
public record AdminOverviewStatsView(
        Instant generatedAt,
        AdminOverviewSummary summary,
        List<AdminManagedResourceStat> managedCatalogItems,
        List<AdminModelStat> models,
        List<AdminCapabilityHotspot> topCapabilities,
        List<AdminInvocationTrendPoint> invocationTrend,
        List<AdminResourceDistribution> resourceDistributions,
        List<AdminModelAccessDistribution> modelAccessDistributions
) {

    public AdminOverviewStatsView {
        managedCatalogItems = managedCatalogItems == null ? List.of() : List.copyOf(managedCatalogItems);
        models = models == null ? List.of() : List.copyOf(models);
        topCapabilities = topCapabilities == null ? List.of() : List.copyOf(topCapabilities);
        invocationTrend = invocationTrend == null ? List.of() : List.copyOf(invocationTrend);
        resourceDistributions = resourceDistributions == null ? List.of() : List.copyOf(resourceDistributions);
        modelAccessDistributions = modelAccessDistributions == null ? List.of() : List.copyOf(modelAccessDistributions);
    }
}
