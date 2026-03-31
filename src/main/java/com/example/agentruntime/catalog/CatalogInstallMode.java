package com.example.agentruntime.catalog;

/**
 * 目录项的安装模式。
 * DIRECT 表示宿主可以直接完成安装；
 * TEMPLATE 表示当前仅提供可落地的配置模板或安装方案。
 */
public enum CatalogInstallMode {
    DIRECT,
    TEMPLATE
}
