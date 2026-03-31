package com.example.agentruntime.auth;

/**
 * 平台用户角色。
 * 当前先区分管理员与普通用户，后续如果要扩展审核员、只读访客等角色，
 * 可以继续在这里补充枚举值，并在鉴权层统一收口。
 */
public enum UserRole {
    ADMIN,
    USER;

    public boolean isAdmin() {
        return this == ADMIN;
    }

    /**
     * 将任意字符串安全归一成已知角色。
     * 旧数据或空值会默认回落为普通用户，避免历史库数据导致启动失败。
     */
    public static UserRole from(String value) {
        if (value == null || value.isBlank()) {
            return USER;
        }
        try {
            return UserRole.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException ignored) {
            return USER;
        }
    }
}
