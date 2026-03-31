package com.example.agentruntime.auth;

/**
 * 当前登录用户的轻量身份对象。
 */
public record AuthenticatedUser(
        Long id,
        String username,
        String displayName,
        UserRole role
) {

    public AuthenticatedUser(Long id, String username, String displayName) {
        this(id, username, displayName, UserRole.USER);
    }

    /**
     * 统一判断当前登录用户是否具备管理员权限，
     * 避免各个服务层重复写字符串比较逻辑。
     */
    public boolean isAdmin() {
        return role == UserRole.ADMIN;
    }
}
