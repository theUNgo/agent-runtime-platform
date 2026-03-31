package com.example.agentruntime.admin;

/**
 * 全局 MCP / Skill 资源统计项。
 * 用于展示某个全局资源当前被多少用户启用，以及它在目录中的基本状态。
 */
public record AdminManagedResourceStat(
        String itemId,
        String itemName,
        String itemType,
        String status,
        String provider,
        int enabledUserCount
) {
}
