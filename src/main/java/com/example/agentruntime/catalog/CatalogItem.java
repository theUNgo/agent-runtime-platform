package com.example.agentruntime.catalog;

import java.util.List;

/**
 * 面向前端目录视图的统一条目。
 */
public record CatalogItem(
        String id,
        String name,
        CatalogItemType type,
        String provider,
        String version,
        String description,
        List<String> tags,
        boolean installed,
        String userState,
        CatalogInstallMode installMode,
        String transport,
        String installHint,
        List<CatalogInstallField> installFields
) {

    public CatalogItem {
        tags = tags == null ? List.of() : List.copyOf(tags);
        installFields = installFields == null ? List.of() : List.copyOf(installFields);
    }
}
