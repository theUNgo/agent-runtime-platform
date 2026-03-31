package com.example.agentruntime.admin;

import com.example.agentruntime.auth.AuthenticatedUser;
import com.example.agentruntime.auth.CurrentUserService;
import com.example.agentruntime.auth.UserRole;
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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminOverviewStatsServiceTest {

    @Mock
    private CurrentUserService currentUserService;
    @Mock
    private UserAccountRepository userAccountRepository;
    @Mock
    private ManagedCatalogItemRepository managedCatalogItemRepository;
    @Mock
    private UserInstallationRepository userInstallationRepository;
    @Mock
    private GlobalModelProfileRepository globalModelProfileRepository;
    @Mock
    private UserEnabledModelRepository userEnabledModelRepository;
    @Mock
    private CapabilityInvocationRepository capabilityInvocationRepository;

    @Test
    void shouldAggregateAdminOverviewStats() {
        when(currentUserService.requireAdmin()).thenReturn(new AuthenticatedUser(1L, "admin", "Admin", UserRole.ADMIN));

        UserAccountEntity admin = user(1L, "admin", UserRole.ADMIN);
        UserAccountEntity alice = user(2L, "alice", UserRole.USER);
        UserAccountEntity bob = user(3L, "bob", UserRole.USER);
        when(userAccountRepository.findAll()).thenReturn(List.of(admin, alice, bob));

        ManagedCatalogItemEntity skill = managedItem("bug-triage", "Bug Triage", "AGENT_SKILL", "ENABLED", "curated-skill");
        ManagedCatalogItemEntity mcp = managedItem("filesystem-local", "Filesystem", "MCP_SERVER", "ENABLED", "curated-mcp");
        when(managedCatalogItemRepository.findAll()).thenReturn(List.of(skill, mcp));

        when(userInstallationRepository.findAll()).thenReturn(List.of(
                installation(alice, "bug-triage", "Bug Triage", "AGENT_SKILL", "ENABLED"),
                installation(bob, "filesystem-local", "Filesystem", "MCP_SERVER", "ENABLED"),
                installation(bob, "bug-triage", "Bug Triage", "AGENT_SKILL", "ENABLED")
        ));

        GlobalModelProfileEntity model = model(10L, "GLM 4.6V", "glm-4.6v");
        when(globalModelProfileRepository.findAll()).thenReturn(List.of(model));
        when(userEnabledModelRepository.findAll()).thenReturn(List.of(
                enabledModel(alice, model, true, true, ModelAccessStatus.APPROVED),
                enabledModel(bob, model, false, false, ModelAccessStatus.PENDING)
        ));

        when(capabilityInvocationRepository.findAll()).thenReturn(List.of(
                invocation(alice, "skill:bug-triage", "Bug Triage", "SKILL", "local-skill", "SUCCESS", Instant.now().minusSeconds(3600)),
                invocation(alice, "skill:bug-triage", "Bug Triage", "SKILL", "local-skill", "FAILED", Instant.now().minusSeconds(1800)),
                invocation(bob, "builtin:echo", "Echo", "BUILTIN", "builtin", "SUCCESS", Instant.now())
        ));

        AdminOverviewStatsService service = new AdminOverviewStatsService(
                currentUserService,
                userAccountRepository,
                managedCatalogItemRepository,
                userInstallationRepository,
                globalModelProfileRepository,
                userEnabledModelRepository,
                capabilityInvocationRepository
        );

        AdminOverviewStatsView view = service.overview();

        assertEquals(3, view.summary().totalUsers());
        assertEquals(2, view.summary().activeUsers());
        assertEquals(1, view.summary().adminUsers());
        assertEquals(1, view.summary().managedMcpServers());
        assertEquals(1, view.summary().managedSkills());
        assertEquals(1, view.summary().managedModels());
        assertEquals(3, view.summary().enabledCatalogSelections());
        assertEquals(1, view.summary().approvedModelSelections());
        assertEquals(1, view.summary().pendingModelApprovals());
        assertEquals(3, view.summary().totalCapabilityInvocations());
        assertEquals(2, view.managedCatalogItems().size());
        assertTrue(view.managedCatalogItems().stream().anyMatch(item -> item.itemId().equals("bug-triage") && item.enabledUserCount() == 2));
        assertEquals(1, view.models().size());
        assertEquals(1, view.models().getFirst().approvedUserCount());
        assertEquals(1, view.models().getFirst().pendingUserCount());
        assertEquals("skill:bug-triage", view.topCapabilities().getFirst().capabilityId());
        assertEquals(2, view.topCapabilities().getFirst().invocationCount());
        assertEquals(1, view.topCapabilities().getFirst().failureCount());
        assertEquals(7, view.invocationTrend().size());
        assertTrue(view.invocationTrend().stream().mapToLong(AdminInvocationTrendPoint::invocationCount).sum() >= 3);
        assertTrue(view.resourceDistributions().stream().anyMatch(item -> item.itemType().equals("AGENT_SKILL") && item.count() == 1));
        assertTrue(view.modelAccessDistributions().stream().anyMatch(item -> item.accessStatus().equals("APPROVED") && item.count() == 1));
        assertTrue(view.modelAccessDistributions().stream().anyMatch(item -> item.accessStatus().equals("PENDING") && item.count() == 1));
    }

    private UserAccountEntity user(Long id, String username, UserRole role) {
        UserAccountEntity entity = new UserAccountEntity();
        entity.setUsername(username);
        entity.setDisplayName(username);
        entity.setRole(role);
        setField(UserAccountEntity.class, entity, "id", id);
        return entity;
    }

    private ManagedCatalogItemEntity managedItem(String itemId, String itemName, String itemType, String status, String provider) {
        ManagedCatalogItemEntity entity = new ManagedCatalogItemEntity();
        entity.setItemId(itemId);
        entity.setItemName(itemName);
        entity.setItemType(itemType);
        entity.setStatus(status);
        entity.setProvider(provider);
        return entity;
    }

    private UserInstallationEntity installation(UserAccountEntity user, String itemId, String itemName, String itemType, String status) {
        UserInstallationEntity entity = new UserInstallationEntity();
        entity.setUser(user);
        entity.setItemId(itemId);
        entity.setItemName(itemName);
        entity.setItemType(itemType);
        entity.setStatus(status);
        return entity;
    }

    private GlobalModelProfileEntity model(Long id, String profileName, String modelId) {
        GlobalModelProfileEntity entity = new GlobalModelProfileEntity();
        entity.setProfileName(profileName);
        entity.setProviderType("openai-compatible");
        entity.setBaseUrl("https://example.com/v1");
        entity.setModelId(modelId);
        entity.setEnabled(true);
        setField(GlobalModelProfileEntity.class, entity, "id", id);
        return entity;
    }

    private UserEnabledModelEntity enabledModel(UserAccountEntity user,
                                                GlobalModelProfileEntity model,
                                                boolean enabled,
                                                boolean isDefault,
                                                ModelAccessStatus accessStatus) {
        UserEnabledModelEntity entity = new UserEnabledModelEntity();
        entity.setUser(user);
        entity.setModelProfile(model);
        entity.setEnabled(enabled);
        entity.setDefault(isDefault);
        entity.setAccessStatus(accessStatus);
        return entity;
    }

    private CapabilityInvocationEntity invocation(UserAccountEntity user,
                                                  String capabilityId,
                                                  String capabilityName,
                                                  String capabilityType,
                                                  String provider,
                                                  String status,
                                                  Instant createdAt) {
        CapabilityInvocationEntity entity = new CapabilityInvocationEntity();
        entity.setUser(user);
        entity.setCapabilityId(capabilityId);
        entity.setCapabilityName(capabilityName);
        entity.setCapabilityType(capabilityType);
        entity.setProvider(provider);
        entity.setStatus(status);
        setField(CapabilityInvocationEntity.class, entity, "createdAt", createdAt);
        return entity;
    }

    private void setField(Class<?> type, Object target, String fieldName, Object value) {
        try {
            var field = type.getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
