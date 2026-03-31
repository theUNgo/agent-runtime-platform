package com.example.agentruntime.installation;

import com.example.agentruntime.catalog.CatalogItemType;
import com.example.agentruntime.i18n.MessageService;
import com.example.agentruntime.persistence.entity.ManagedCatalogItemEntity;
import com.example.agentruntime.persistence.entity.UserAccountEntity;
import com.example.agentruntime.persistence.entity.UserInstallationEntity;
import com.example.agentruntime.persistence.repository.ManagedCatalogItemRepository;
import com.example.agentruntime.persistence.repository.UserAccountRepository;
import com.example.agentruntime.persistence.repository.UserInstallationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 用户安装项服务。
 */
@Service
public class UserInstallationService {

    public static final String STATUS_ENABLED = "ENABLED";
    public static final String STATUS_PLANNED = "PLANNED";
    public static final String STATUS_NOT_ENABLED = "NOT_ENABLED";

    private final UserInstallationRepository installationRepository;
    private final ManagedCatalogItemRepository managedCatalogItemRepository;
    private final UserAccountRepository userAccountRepository;
    private final MessageService messageService;

    public UserInstallationService(UserInstallationRepository installationRepository,
                                   ManagedCatalogItemRepository managedCatalogItemRepository,
                                   UserAccountRepository userAccountRepository,
                                   MessageService messageService) {
        this.installationRepository = installationRepository;
        this.managedCatalogItemRepository = managedCatalogItemRepository;
        this.userAccountRepository = userAccountRepository;
        this.messageService = messageService;
    }

    @Transactional
    public void markInstalled(Long userId,
                              CatalogItemType itemType,
                              String itemId,
                              String itemName,
                              String provider,
                              String metadataJson) {
        markEnabled(userId, itemType, itemId, itemName, provider, metadataJson);
    }

    /**
     * 为当前用户启用一个全局目录项。
     * 旧版本“安装”语义已经迁移为“启用”，这里保留统一入口给上层复用。
     */
    @Transactional
    public void markEnabled(Long userId,
                            CatalogItemType itemType,
                            String itemId,
                            String itemName,
                            String provider,
                            String metadataJson) {
        UserAccountEntity user = userAccountRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException(messageService.get("auth.error.userNotFound")));
        UserInstallationEntity entity = installationRepository.findByUserAndItemIdAndItemType(user, itemId, itemType.name())
                .orElseGet(UserInstallationEntity::new);
        entity.setUser(user);
        entity.setItemId(itemId);
        entity.setItemName(itemName);
        entity.setItemType(itemType.name());
        entity.setStatus(STATUS_ENABLED);
        entity.setProvider(provider);
        entity.setMetadataJson(metadataJson);
        installationRepository.save(entity);
    }

    @Transactional
    public void markPlanned(Long userId,
                            CatalogItemType itemType,
                            String itemId,
                            String itemName,
                            String provider,
                            String metadataJson) {
        UserAccountEntity user = userAccountRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException(messageService.get("auth.error.userNotFound")));
        UserInstallationEntity entity = installationRepository.findByUserAndItemIdAndItemType(user, itemId, itemType.name())
                .orElseGet(UserInstallationEntity::new);
        entity.setUser(user);
        entity.setItemId(itemId);
        entity.setItemName(itemName);
        entity.setItemType(itemType.name());
        entity.setStatus(STATUS_PLANNED);
        entity.setProvider(provider);
        entity.setMetadataJson(metadataJson);
        installationRepository.save(entity);
    }

    @Transactional(readOnly = true)
    public Set<String> installedSkillIds(Long userId) {
        return installedIds(userId, CatalogItemType.AGENT_SKILL);
    }

    @Transactional(readOnly = true)
    public Set<String> installedMcpServerIds(Long userId) {
        return installedIds(userId, CatalogItemType.MCP_SERVER);
    }

    @Transactional(readOnly = true)
    public boolean hasInstalledSkillSelection(Long userId) {
        return !installedSkillIds(userId).isEmpty();
    }

    @Transactional(readOnly = true)
    public boolean hasInstalledMcpSelection(Long userId) {
        return !installedMcpServerIds(userId).isEmpty();
    }

    @Transactional(readOnly = true)
    public String stateFor(Long userId, CatalogItemType itemType, String itemId) {
        UserAccountEntity user = userAccountRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException(messageService.get("auth.error.userNotFound")));
        return installationRepository.findByUserAndItemIdAndItemType(user, itemId, itemType.name())
                .map(entity -> normalizeStatus(entity.getStatus()))
                .orElse(STATUS_NOT_ENABLED);
    }

    @Transactional(readOnly = true)
    public List<UserInstallationView> listForUser(Long userId) {
        UserAccountEntity user = userAccountRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException(messageService.get("auth.error.userNotFound")));
        return installationRepository.findByUser(user).stream()
                .map(entity -> new UserInstallationView(
                        entity.getItemId(),
                        entity.getItemName(),
                        entity.getItemType(),
                        entity.getStatus(),
                        entity.getProvider(),
                        currentMetadata(entity)
                ))
                .toList();
    }

    @Transactional
    public void remove(Long userId, CatalogItemType itemType, String itemId) {
        UserAccountEntity user = userAccountRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException(messageService.get("auth.error.userNotFound")));
        installationRepository.deleteByUserAndItemIdAndItemType(user, itemId, itemType.name());
    }

    private Set<String> installedIds(Long userId, CatalogItemType itemType) {
        UserAccountEntity user = userAccountRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException(messageService.get("auth.error.userNotFound")));
        return installationRepository.findByUserAndItemType(user, itemType.name()).stream()
                .filter(entity -> STATUS_ENABLED.equals(normalizeStatus(entity.getStatus())))
                .map(UserInstallationEntity::getItemId)
                .collect(Collectors.toSet());
    }

    /**
     * 用户关系表里保存的是启用快照，但真正运行时应尽量以管理员维护的全局配置为准。
     */
    private String currentMetadata(UserInstallationEntity entity) {
        return managedCatalogItemRepository.findByItemIdAndItemType(entity.getItemId(), entity.getItemType())
                .map(ManagedCatalogItemEntity::getMetadataJson)
                .filter(value -> value != null && !value.isBlank())
                .orElse(entity.getMetadataJson());
    }

    private String normalizeStatus(String status) {
        if ("INSTALLED".equalsIgnoreCase(status)) {
            return STATUS_ENABLED;
        }
        return status == null || status.isBlank() ? STATUS_NOT_ENABLED : status;
    }
}
