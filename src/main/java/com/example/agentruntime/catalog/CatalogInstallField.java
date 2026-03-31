package com.example.agentruntime.catalog;

/**
 * 目录安装表单字段。
 * 这类字段主要给前端动态渲染安装配置表单使用。
 */
public record CatalogInstallField(
        String key,
        String label,
        String placeholder,
        boolean required,
        boolean secret,
        String defaultValue,
        String description
) {
}
