package com.example.agentruntime.web;

import com.example.agentruntime.catalog.CatalogInstallRequest;
import com.example.agentruntime.catalog.CatalogInstallResult;
import com.example.agentruntime.catalog.CatalogItem;
import com.example.agentruntime.catalog.CatalogItemType;
import com.example.agentruntime.catalog.McpCatalogInstallRequest;
import com.example.agentruntime.catalog.UnifiedCatalogService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 统一能力目录接口。
 * 这里同时承担两类职责：
 * 1. 给所有用户提供“查看全局可用能力”的目录查询能力
 * 2. 给管理员提供“安装全局 Skill / MCP”的管理入口
 *
 * 普通用户不再重复安装一套资源，而是通过 enable / disable 维护自己的启用关系。
 */
@RestController
@RequestMapping("/api/catalog")
public class CatalogController {

    private final UnifiedCatalogService catalogService;

    public CatalogController(UnifiedCatalogService catalogService) {
        this.catalogService = catalogService;
    }

    @GetMapping("/items")
    public List<CatalogItem> list(@RequestParam(value = "q", required = false) String query,
                                  @RequestParam(value = "type", required = false) CatalogItemType type) {
        return catalogService.search(query, type);
    }

    /**
     * 为当前用户启用某个已经全局开放的能力。
     */
    @PostMapping("/items/{itemType}/{itemId}/enable")
    public void enable(@PathVariable("itemType") CatalogItemType itemType,
                       @PathVariable("itemId") String itemId) {
        catalogService.enableForCurrentUser(itemType, itemId);
    }

    /**
     * 取消当前用户对某个能力的启用关系，不影响全局资源本身。
     */
    @PostMapping("/items/{itemType}/{itemId}/disable")
    public void disable(@PathVariable("itemType") CatalogItemType itemType,
                        @PathVariable("itemId") String itemId) {
        catalogService.disableForCurrentUser(itemType, itemId);
    }

    /**
     * 管理员安装全局 Skill。
     */
    @PostMapping("/skills/install")
    public CatalogInstallResult installSkill(@Valid @RequestBody CatalogInstallRequest request) {
        return catalogService.installSkill(request.itemId());
    }

    /**
     * 管理员安装全局 MCP 实例。
     */
    @PostMapping("/mcp-servers/install")
    public CatalogInstallResult installMcpServer(@Valid @RequestBody McpCatalogInstallRequest request) {
        return catalogService.installMcpServer(request);
    }

    /**
     * 管理员生成 MCP 安装模板或配置规划结果。
     */
    @PostMapping("/mcp-servers/{itemId}/install-plan")
    public CatalogInstallResult buildInstallPlan(@PathVariable("itemId") String itemId) {
        return catalogService.buildMcpInstallPlan(itemId);
    }
}
