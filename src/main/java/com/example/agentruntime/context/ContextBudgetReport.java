package com.example.agentruntime.context;

/**
 * 上下文预算评估结果。
 * 这里重点关注总占用率和背景占比，帮助编排层动态决定是否需要压缩历史背景信息。
 */
public record ContextBudgetReport(
        int maxTokens,
        int usedTokens,
        int backgroundTokens,
        double usageRatio,
        double backgroundRatio,
        boolean compressionRequired
) {
}
