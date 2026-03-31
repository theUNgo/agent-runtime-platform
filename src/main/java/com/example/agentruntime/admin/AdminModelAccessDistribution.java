package com.example.agentruntime.admin;

/**
 * 模型授权状态分布统计项。
 * 这一层直接按审批状态聚合，便于管理员快速判断平台模型授权积压情况。
 */
public record AdminModelAccessDistribution(
        String accessStatus,
        long count
) {
}
