package com.example.agentruntime.catalog;

import jakarta.validation.constraints.NotBlank;

/**
 * 统一安装请求。
 */
public record CatalogInstallRequest(
        @NotBlank String itemId
) {
}
