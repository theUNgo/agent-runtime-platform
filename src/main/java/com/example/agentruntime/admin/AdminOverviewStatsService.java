package com.example.agentruntime.admin;

import com.example.agentruntime.auth.CurrentUserService;
import com.example.agentruntime.auth.UserRole;
import com.example.agentruntime.installation.UserInstallationService;
import com.example.agentruntime.model.ModelAccessStatus;
import com.example.agentruntime.persistence.entity.CapabilityInvocationEntity;
import com.example.agentruntime.persistence.entity.GlobalModelProfileEntity;
import com.example.agentruntime.persistence.entity.ManagedCatalogItemEntity;
import com.example.agentruntime.persistence.entity.UserAccountEntity;
import com.example.agentruntime.persistence.entity.UserEnabledModelEntity;
import com.example.agentruntime.persistence.entity.UserInstallationEntity;
import com.example.agentruntime.persistence.repository.CapabilityInvocationRepository;
import com.example.agentruntime.persistence.repository.GlobalModelProfileRepository;
import com.example.agentruntime.persistence.repository.ManagedCatalogItemRepository;
import com.example.agentruntime.persistence.repository.UserAccountRepository;
import com.example.agentruntime.persistence.repository.UserEnabledModelRepository;
import com.example.agentruntime.persistence.repository.UserInstallationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.Map;
import java.util.stream.IntStream;
import java.util.stream.Collectors;

/**
 * 管理员统计服务。
 * 这一层只做聚合与汇总，不改变任何资源状态，便于后续继续扩展运营统计与资源看板。
 */
@Service
public class AdminOverviewStatsService {

    /**
     * 管理员首页默认展示最近 7 天的调用趋势。
     * 先保持一个固定窗口，后面如果需要再扩成可配置查询参数。
     */
    private static final int TREND_DAYS = 7;

    private final CurrentUserService currentUserService;
    private final UserAccountRepository userAccountRepository;
    private final ManagedCatalogItemRepository managedCatalogItemRepository;
    private final UserInstallationRepository userInstallationRepository;
    private final GlobalModelProfileRepository globalModelProfileRepository;
    private final UserEnabledModelRepository userEnabledModelRepository;
    private final CapabilityInvocationRepository capabilityInvocationRepository;

    public AdminOverviewStatsService(CurrentUserService currentUserService,
                                     UserAccountRepository userAccountRepository,
                                     ManagedCatalogItemRepository managedCatalogItemRepository,
                                     UserInstallationRepository userInstallationRepository,
                                     GlobalModelProfileRepository globalModelProfileRepository,
                                     UserEnabledModelRepository userEnabledModelRepository,
                                     CapabilityInvocationRepository capabilityInvocationRepository) {
        this.currentUserService = currentUserService;
        this.userAccountRepository = userAccountRepository;
        this.managedCatalogItemRepository = managedCatalogItemRepository;
        this.userInstallationRepository = userInstallationRepository;
        this.globalModelProfileRepository = globalModelProfileRepository;
        this.userEnabledModelRepository = userEnabledModelRepository;
        this.capabilityInvocationRepository = capabilityInvocationRepository;
    }

    /**
     * 生成管理员首页统计总览。
     * 当前先聚焦资源数量、启用人数和调用热度，保证这一版能直接支撑管理台展示。
     */
    @Transactional(readOnly = true)
    public AdminOverviewStatsView overview() {
        currentUserService.requireAdmin();

        List<UserAccountEntity> users = userAccountRepository.findAll();
        List<ManagedCatalogItemEntity> managedCatalogItems = managedCatalogItemRepository.findAll();
        List<UserInstallationEntity> userInstallations = userInstallationRepository.findAll();
        List<GlobalModelProfileEntity> models = globalModelProfileRepository.findAll();
        List<UserEnabledModelEntity> userEnabledModels = userEnabledModelRepository.findAll();
        List<CapabilityInvocationEntity> invocations = capabilityInvocationRepository.findAll();

        Map<String, Long> enabledCatalogCounts = userInstallations.stream()
                .filter(item -> UserInstallationService.STATUS_ENABLED.equalsIgnoreCase(item.getStatus()))
                .collect(Collectors.groupingBy(
                        item -> item.getItemType() + "::" + item.getItemId(),
                        Collectors.mapping(item -> item.getUser().getId(), Collectors.collectingAndThen(Collectors.toSet(), set -> (long) set.size()))
                ));

        List<AdminManagedResourceStat> catalogStats = managedCatalogItems.stream()
                .sorted(Comparator.comparing(ManagedCatalogItemEntity::getItemType).thenComparing(ManagedCatalogItemEntity::getItemName))
                .map(item -> new AdminManagedResourceStat(
                        item.getItemId(),
                        item.getItemName(),
                        item.getItemType(),
                        item.getStatus(),
                        item.getProvider(),
                        enabledCatalogCounts.getOrDefault(item.getItemType() + "::" + item.getItemId(), 0L).intValue()
                ))
                .toList();

        Map<Long, List<UserEnabledModelEntity>> modelRelations = userEnabledModels.stream()
                .filter(item -> item.getModelProfile() != null)
                .collect(Collectors.groupingBy(item -> item.getModelProfile().getId()));

        List<AdminModelStat> modelStats = models.stream()
                .sorted(Comparator.comparing(GlobalModelProfileEntity::getProfileName, Comparator.nullsLast(String::compareToIgnoreCase)))
                .map(model -> {
                    List<UserEnabledModelEntity> relations = modelRelations.getOrDefault(model.getId(), List.of());
                    int approvedUserCount = (int) relations.stream()
                            .filter(UserEnabledModelEntity::isEnabled)
                            .filter(item -> item.getAccessStatus() == ModelAccessStatus.APPROVED)
                            .map(item -> item.getUser().getId())
                            .distinct()
                            .count();
                    int pendingUserCount = (int) relations.stream()
                            .filter(item -> item.getAccessStatus() == ModelAccessStatus.PENDING)
                            .map(item -> item.getUser().getId())
                            .distinct()
                            .count();
                    int defaultUserCount = (int) relations.stream()
                            .filter(UserEnabledModelEntity::isDefault)
                            .filter(item -> item.getAccessStatus() == ModelAccessStatus.APPROVED)
                            .map(item -> item.getUser().getId())
                            .distinct()
                            .count();
                    return new AdminModelStat(
                            model.getId(),
                            model.getProfileName(),
                            model.getProviderType(),
                            model.getModelId(),
                            model.isEnabled(),
                            approvedUserCount,
                            pendingUserCount,
                            defaultUserCount
                    );
                })
                .toList();

        List<AdminCapabilityHotspot> topCapabilities = invocations.stream()
                .collect(Collectors.groupingBy(
                        invocation -> new CapabilityHotspotKey(
                                invocation.getCapabilityId(),
                                invocation.getCapabilityName(),
                                invocation.getCapabilityType(),
                                invocation.getProvider()
                        )
                ))
                .entrySet()
                .stream()
                .map(entry -> {
                    long invocationCount = entry.getValue().size();
                    long successCount = entry.getValue().stream().filter(item -> "SUCCESS".equalsIgnoreCase(item.getStatus())).count();
                    long failureCount = entry.getValue().stream().filter(item -> "FAILED".equalsIgnoreCase(item.getStatus())).count();
                    return new AdminCapabilityHotspot(
                            entry.getKey().capabilityId(),
                            entry.getKey().capabilityName(),
                            entry.getKey().capabilityType(),
                            entry.getKey().provider(),
                            invocationCount,
                            successCount,
                            failureCount
                    );
                })
                .sorted(Comparator.comparingLong(AdminCapabilityHotspot::invocationCount).reversed()
                        .thenComparing(AdminCapabilityHotspot::capabilityName, Comparator.nullsLast(String::compareToIgnoreCase)))
                .limit(10)
                .toList();

        List<AdminInvocationTrendPoint> invocationTrend = buildInvocationTrend(invocations);
        List<AdminResourceDistribution> resourceDistributions = buildResourceDistributions(managedCatalogItems);
        List<AdminModelAccessDistribution> modelAccessDistributions = buildModelAccessDistributions(userEnabledModels);

        Set<Long> activeUserIds = collectActiveUserIds(userInstallations, userEnabledModels, invocations);
        int adminUsers = (int) users.stream().filter(user -> user.getRole() == UserRole.ADMIN).count();
        int totalUsers = users.size();
        int managedMcpServers = (int) managedCatalogItems.stream().filter(item -> "MCP_SERVER".equals(item.getItemType())).count();
        int managedSkills = (int) managedCatalogItems.stream().filter(item -> "AGENT_SKILL".equals(item.getItemType())).count();
        int enabledCatalogSelections = (int) userInstallations.stream().filter(item -> UserInstallationService.STATUS_ENABLED.equalsIgnoreCase(item.getStatus())).count();
        int approvedModelSelections = (int) userEnabledModels.stream()
                .filter(UserEnabledModelEntity::isEnabled)
                .filter(item -> item.getAccessStatus() == ModelAccessStatus.APPROVED)
                .count();
        int pendingModelApprovals = (int) userEnabledModels.stream()
                .filter(item -> item.getAccessStatus() == ModelAccessStatus.PENDING)
                .count();

        return new AdminOverviewStatsView(
                Instant.now(),
                new AdminOverviewSummary(
                        totalUsers,
                        activeUserIds.size(),
                        adminUsers,
                        managedMcpServers,
                        managedSkills,
                        models.size(),
                        enabledCatalogSelections,
                        approvedModelSelections,
                        pendingModelApprovals,
                        invocations.size()
                ),
                catalogStats,
                modelStats,
                topCapabilities,
                invocationTrend,
                resourceDistributions,
                modelAccessDistributions
        );
    }

    /**
     * 构建最近 N 天的能力调用趋势。
     * 没有数据的日期也会补零，保证前端时间轴稳定。
     */
    private List<AdminInvocationTrendPoint> buildInvocationTrend(List<CapabilityInvocationEntity> invocations) {
        ZoneId zoneId = ZoneId.systemDefault();
        LocalDate today = LocalDate.now(zoneId);
        LocalDate startDate = today.minusDays(TREND_DAYS - 1L);

        Map<LocalDate, List<CapabilityInvocationEntity>> groupedByDate = invocations.stream()
                .filter(invocation -> invocation.getCreatedAt() != null)
                .collect(Collectors.groupingBy(invocation -> invocation.getCreatedAt().atZone(zoneId).toLocalDate()));

        return IntStream.range(0, TREND_DAYS)
                .mapToObj(offset -> startDate.plusDays(offset))
                .map(date -> {
                    List<CapabilityInvocationEntity> items = groupedByDate.getOrDefault(date, List.of());
                    long successCount = items.stream()
                            .filter(item -> "SUCCESS".equalsIgnoreCase(item.getStatus()))
                            .count();
                    long failureCount = items.stream()
                            .filter(item -> "FAILED".equalsIgnoreCase(item.getStatus()))
                            .count();
                    return new AdminInvocationTrendPoint(
                            date,
                            items.size(),
                            successCount,
                            failureCount
                    );
                })
                .toList();
    }

    /**
     * 统计全局资源的类型 / 状态分布。
     * 这一层帮助管理员快速判断当前是哪些资源处于启用态、禁用态或其他状态。
     */
    private List<AdminResourceDistribution> buildResourceDistributions(List<ManagedCatalogItemEntity> managedCatalogItems) {
        return managedCatalogItems.stream()
                .collect(Collectors.groupingBy(
                        item -> new ResourceDistributionKey(item.getItemType(), item.getStatus()),
                        Collectors.counting()
                ))
                .entrySet()
                .stream()
                .map(entry -> new AdminResourceDistribution(
                        entry.getKey().itemType(),
                        entry.getKey().status(),
                        entry.getValue()
                ))
                .sorted(Comparator.comparing(AdminResourceDistribution::itemType, Comparator.nullsLast(String::compareToIgnoreCase))
                        .thenComparing(AdminResourceDistribution::status, Comparator.nullsLast(String::compareToIgnoreCase)))
                .toList();
    }

    /**
     * 统计模型授权状态分布。
     * 这里按用户-模型关系聚合，能直观看到待审批和已批准的总体规模。
     */
    private List<AdminModelAccessDistribution> buildModelAccessDistributions(List<UserEnabledModelEntity> userEnabledModels) {
        return userEnabledModels.stream()
                .collect(Collectors.groupingBy(
                        item -> item.getAccessStatus() == null ? ModelAccessStatus.NOT_REQUESTED.name() : item.getAccessStatus().name(),
                        Collectors.counting()
                ))
                .entrySet()
                .stream()
                .map(entry -> new AdminModelAccessDistribution(entry.getKey(), entry.getValue()))
                .sorted(Comparator.comparing(AdminModelAccessDistribution::accessStatus, Comparator.nullsLast(String::compareToIgnoreCase)))
                .toList();
    }

    /**
     * 汇总“当前活跃用户”。
     * 当前版本将有启用关系、模型关系或调用审计的用户视为平台活跃用户。
     */
    private Set<Long> collectActiveUserIds(List<UserInstallationEntity> userInstallations,
                                           List<UserEnabledModelEntity> userEnabledModels,
                                           List<CapabilityInvocationEntity> invocations) {
        return List.of(
                        userInstallations.stream().map(item -> item.getUser().getId()),
                        userEnabledModels.stream().map(item -> item.getUser().getId()),
                        invocations.stream()
                                .map(CapabilityInvocationEntity::getUser)
                                .filter(Objects::nonNull)
                                .map(UserAccountEntity::getId)
                )
                .stream()
                .flatMap(stream -> stream)
                .collect(Collectors.toSet());
    }

    /**
     * 调用热点的分组键。
     * 单独提取成 record，避免聚合逻辑里散落重复字段。
     */
    private record CapabilityHotspotKey(
            String capabilityId,
            String capabilityName,
            String capabilityType,
            String provider
    ) {
    }

    /**
     * 资源分布统计的分组键。
     * 单独抽出来是为了让按类型和状态的聚合逻辑更清晰。
     */
    private record ResourceDistributionKey(
            String itemType,
            String status
    ) {
    }
}
