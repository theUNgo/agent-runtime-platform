package com.example.agentruntime.catalog;

import java.util.List;

/**
 * 目录安装结果。
 */
public record CatalogInstallResult(
        String itemId,
        CatalogItemType itemType,
        boolean installed,
        CatalogInstallMode installMode,
        String message,
        List<String> nextSteps,
        String generatedSnippet
) {

    public CatalogInstallResult {
        nextSteps = nextSteps == null ? List.of() : List.copyOf(nextSteps);
    }
}
