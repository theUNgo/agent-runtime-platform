package com.example.agentruntime.auth;

/**
 * 管理员侧的用户简要信息。
 * 这里主要用于给模型授权面板选择授权对象。
 */
public record AdminUserView(
        Long userId,
        String username,
        String displayName,
        String role,
        boolean enabled
) {
}
