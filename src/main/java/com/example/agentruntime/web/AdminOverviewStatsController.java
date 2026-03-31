package com.example.agentruntime.web;

import com.example.agentruntime.admin.AdminOverviewStatsService;
import com.example.agentruntime.admin.AdminOverviewStatsView;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理员总览统计接口。
 * 这一层用于给管理台提供全局资源规模、启用关系和调用热度等平台级信息。
 */
@RestController
@RequestMapping("/api/admin/overview")
public class AdminOverviewStatsController {

    private final AdminOverviewStatsService adminOverviewStatsService;

    public AdminOverviewStatsController(AdminOverviewStatsService adminOverviewStatsService) {
        this.adminOverviewStatsService = adminOverviewStatsService;
    }

    @GetMapping
    public AdminOverviewStatsView overview() {
        return adminOverviewStatsService.overview();
    }
}
