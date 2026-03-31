package com.example.agentruntime.model;

import java.time.Instant;

/**
 * 管理员查看模型访问申请列表时使用的视图对象。
 */
public record AdminModelAccessRequestView(
        Long requestId,
        Long userId,
        String username,
        String displayName,
        Long profileId,
        String profileName,
        ModelAccessStatus accessStatus,
        boolean enabled,
        boolean isDefault,
        String reviewComment,
        Instant requestedAt,
        Instant reviewedAt,
        String reviewedBy
) {
}
