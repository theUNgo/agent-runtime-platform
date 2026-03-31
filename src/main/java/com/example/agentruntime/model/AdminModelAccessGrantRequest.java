package com.example.agentruntime.model;

/**
 * 管理员给指定用户授权模型使用权限时的请求体。
 */
public record AdminModelAccessGrantRequest(
        Long userId,
        Long profileId,
        boolean makeDefault
) {
}
