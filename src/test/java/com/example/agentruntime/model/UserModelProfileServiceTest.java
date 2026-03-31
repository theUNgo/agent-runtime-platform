package com.example.agentruntime.model;

import com.example.agentruntime.i18n.MessageService;
import com.example.agentruntime.auth.AuthenticatedUser;
import com.example.agentruntime.auth.CurrentUserService;
import com.example.agentruntime.auth.UserRole;
import com.example.agentruntime.persistence.entity.GlobalModelProfileEntity;
import com.example.agentruntime.persistence.entity.UserAccountEntity;
import com.example.agentruntime.persistence.entity.UserEnabledModelEntity;
import com.example.agentruntime.persistence.repository.GlobalModelProfileRepository;
import com.example.agentruntime.persistence.repository.UserAccountRepository;
import com.example.agentruntime.persistence.repository.UserEnabledModelRepository;
import com.example.agentruntime.security.ApiKeyCryptoService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.support.StaticMessageSource;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserModelProfileServiceTest {

    @Mock
    private GlobalModelProfileRepository profileRepository;

    @Mock
    private UserEnabledModelRepository userEnabledModelRepository;

    @Mock
    private UserAccountRepository userAccountRepository;

    @Mock
    private CurrentUserService currentUserService;

    private final ApiKeyCryptoService apiKeyCryptoService = new ApiKeyCryptoService(
            new com.example.agentruntime.AgentRuntimeProperties(
                    "./skills",
                    ".",
                    null,
                    null,
                    new com.example.agentruntime.AgentRuntimeProperties.SecurityProperties(
                    new com.example.agentruntime.AgentRuntimeProperties.CryptoProperties("UnitTestSecret-32Chars-Minimum-123456")
                    )
            ),
            messageService()
    );

    @Test
    void shouldCreateDefaultProfileAndMaskApiKey() {
        UserAccountEntity user = new UserAccountEntity();
        user.setRole(UserRole.ADMIN);
        when(userAccountRepository.findById(1L)).thenReturn(Optional.of(user));
        when(profileRepository.findByProfileName("Primary")).thenReturn(Optional.empty());
        when(profileRepository.save(any(GlobalModelProfileEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(userEnabledModelRepository.findByUserAndModelProfile(any(), any())).thenReturn(Optional.empty());
        when(userEnabledModelRepository.findByUserOrderByIsDefaultDescIdDesc(user)).thenReturn(List.of());
        when(currentUserService.requireAdmin()).thenReturn(new AuthenticatedUser(1L, "admin", "Admin", UserRole.ADMIN));

        UserModelProfileService service = new UserModelProfileService(
                profileRepository,
                userEnabledModelRepository,
                userAccountRepository,
                currentUserService,
                messageService(),
                apiKeyCryptoService
        );
        UserModelProfileView view = service.create(1L, new UserModelProfileUpsertRequest(
                "Primary",
                "openai-compatible",
                "https://api.openai.com/v1",
                "sk-example-secret",
                null,
                "gpt-4.1-mini",
                true,
                true,
                false,
                true,
                true,
                true,
                true,
                true
        ));

        ArgumentCaptor<GlobalModelProfileEntity> captor = ArgumentCaptor.forClass(GlobalModelProfileEntity.class);
        verify(profileRepository, atLeastOnce()).save(captor.capture());
        assertTrue(captor.getValue().getApiKey().startsWith("enc:v1:"));
        assertTrue(view.hasApiKey());
        assertTrue(view.maskedApiKey().startsWith("sk-"));
    }

    @Test
    void shouldExposeActiveSelection() {
        UserAccountEntity user = new UserAccountEntity();
        GlobalModelProfileEntity profile = new GlobalModelProfileEntity();
        profile.setProfileName("Vision");
        profile.setProviderType("openai-compatible");
        profile.setBaseUrl("https://example.com/v1");
        profile.setModelId("gpt-4.1");
        profile.setSupportsVision(true);
        profile.setSupportsAudio(false);
        profile.setEnabled(true);
        setId(profile, 7L);

        when(userAccountRepository.findById(2L)).thenReturn(Optional.of(user));
        UserEnabledModelEntity relation = new UserEnabledModelEntity();
        relation.setUser(user);
        relation.setModelProfile(profile);
        relation.setEnabled(true);
        relation.setDefault(true);
        when(userEnabledModelRepository.findByUserOrderByIsDefaultDescIdDesc(user)).thenReturn(List.of(relation));

        UserModelProfileService service = new UserModelProfileService(
                profileRepository,
                userEnabledModelRepository,
                userAccountRepository,
                currentUserService,
                messageService(),
                apiKeyCryptoService
        );
        var selection = service.activeSelection(2L);

        assertTrue(selection.isPresent());
        assertEquals("Vision", selection.get().profileName());
        assertEquals("gpt-4.1", selection.get().modelId());
        assertTrue(selection.get().supportsVision());
    }

    @Test
    void shouldReEncryptLegacyPlaintextApiKeyOnUpdateWithoutResubmittingKey() {
        UserAccountEntity user = new UserAccountEntity();
        user.setRole(UserRole.ADMIN);
        GlobalModelProfileEntity profile = new GlobalModelProfileEntity();
        setId(profile, 9L);
        profile.setApiKey("legacy-plain-secret");
        profile.setProfileName("Legacy");
        profile.setProviderType("openai-compatible");
        profile.setBaseUrl("https://example.com/v1");
        profile.setModelId("glm-4.6v");
        profile.setSupportsText(true);
        profile.setEnabled(true);

        when(userAccountRepository.findById(3L)).thenReturn(Optional.of(user));
        when(profileRepository.findById(9L)).thenReturn(Optional.of(profile));
        when(profileRepository.findByProfileName("Legacy")).thenReturn(Optional.of(profile));
        when(profileRepository.save(any(GlobalModelProfileEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(userEnabledModelRepository.findByUserOrderByIsDefaultDescIdDesc(user)).thenReturn(List.of());
        when(currentUserService.requireAdmin()).thenReturn(new AuthenticatedUser(3L, "admin", "Admin", UserRole.ADMIN));

        UserModelProfileService service = new UserModelProfileService(
                profileRepository,
                userEnabledModelRepository,
                userAccountRepository,
                currentUserService,
                messageService(),
                apiKeyCryptoService
        );
        service.update(3L, 9L, new UserModelProfileUpsertRequest(
                "Legacy",
                "openai-compatible",
                "https://example.com/v1",
                null,
                null,
                "glm-4.6v",
                true,
                false,
                false,
                true,
                true,
                true,
                true,
                false
        ));

        assertTrue(profile.getApiKey().startsWith("enc:v1:"));
        assertEquals("legacy-plain-secret", apiKeyCryptoService.decryptFromStorage(profile.getApiKey()));
    }

    @Test
    void shouldCreatePendingAccessRequestForRegularUser() {
        UserAccountEntity user = new UserAccountEntity();
        user.setRole(UserRole.USER);
        GlobalModelProfileEntity profile = new GlobalModelProfileEntity();
        setId(profile, 11L);
        profile.setProfileName("Restricted");
        profile.setProviderType("openai-compatible");
        profile.setBaseUrl("https://example.com/v1");
        profile.setModelId("gpt-4.1");
        profile.setEnabled(true);

        when(userAccountRepository.findById(5L)).thenReturn(Optional.of(user));
        when(profileRepository.findById(11L)).thenReturn(Optional.of(profile));
        when(userEnabledModelRepository.findByUserAndModelProfile(user, profile)).thenReturn(Optional.empty());
        AtomicReference<UserEnabledModelEntity> savedRelation = new AtomicReference<>();
        when(userEnabledModelRepository.save(any(UserEnabledModelEntity.class))).thenAnswer(invocation -> {
            UserEnabledModelEntity relation = invocation.getArgument(0);
            savedRelation.set(relation);
            return relation;
        });
        when(userEnabledModelRepository.findByUserOrderByIsDefaultDescIdDesc(user)).thenAnswer(invocation -> {
            UserEnabledModelEntity relation = savedRelation.get();
            return relation == null ? List.of() : List.of(relation);
        });

        UserModelProfileService service = new UserModelProfileService(
                profileRepository,
                userEnabledModelRepository,
                userAccountRepository,
                currentUserService,
                messageService(),
                apiKeyCryptoService
        );
        UserModelProfileView view = service.enable(5L, 11L);

        assertEquals("PENDING", view.accessStatus());
        assertFalse(view.enabled());
    }

    @Test
    void shouldGrantApprovedAccessToTargetUser() {
        UserAccountEntity admin = new UserAccountEntity();
        admin.setRole(UserRole.ADMIN);
        UserAccountEntity targetUser = new UserAccountEntity();
        targetUser.setRole(UserRole.USER);
        GlobalModelProfileEntity profile = new GlobalModelProfileEntity();
        setId(profile, 13L);
        profile.setProfileName("Approved");
        profile.setProviderType("openai-compatible");
        profile.setBaseUrl("https://example.com/v1");
        profile.setModelId("glm-4.6v");
        profile.setEnabled(true);

        when(userAccountRepository.findById(1L)).thenReturn(Optional.of(admin));
        when(userAccountRepository.findById(6L)).thenReturn(Optional.of(targetUser));
        when(profileRepository.findById(13L)).thenReturn(Optional.of(profile));
        when(userEnabledModelRepository.findByUserAndModelProfile(targetUser, profile)).thenReturn(Optional.empty());
        when(userEnabledModelRepository.findByUserAndIsDefaultTrue(targetUser)).thenReturn(Optional.empty());
        AtomicReference<UserEnabledModelEntity> savedRelation = new AtomicReference<>();
        when(userEnabledModelRepository.save(any(UserEnabledModelEntity.class))).thenAnswer(invocation -> {
            UserEnabledModelEntity relation = invocation.getArgument(0);
            savedRelation.set(relation);
            return relation;
        });
        when(userEnabledModelRepository.findByUserOrderByIsDefaultDescIdDesc(targetUser)).thenAnswer(invocation -> {
            UserEnabledModelEntity relation = savedRelation.get();
            return relation == null ? List.of() : List.of(relation);
        });

        UserModelProfileService service = new UserModelProfileService(
                profileRepository,
                userEnabledModelRepository,
                userAccountRepository,
                currentUserService,
                messageService(),
                apiKeyCryptoService
        );
        UserModelProfileView view = service.grant(1L, new AdminModelAccessGrantRequest(6L, 13L, false));

        assertEquals("APPROVED", view.accessStatus());
        assertTrue(view.enabled());
    }

    private void setId(GlobalModelProfileEntity entity, Long id) {
        try {
            var field = GlobalModelProfileEntity.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(entity, id);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private MessageService messageService() {
        StaticMessageSource source = new StaticMessageSource();
        source.addMessage("auth.error.userNotFound", Locale.ENGLISH, "User was not found.");
        source.addMessage("model.error.profileNameRequired", Locale.ENGLISH, "profile required");
        source.addMessage("model.error.baseUrlRequired", Locale.ENGLISH, "url required");
        source.addMessage("model.error.modelIdRequired", Locale.ENGLISH, "model required");
        source.addMessage("model.error.profileNameExists", Locale.ENGLISH, "exists");
        source.addMessage("model.error.profileNotFound", Locale.ENGLISH, "not found");
        source.addMessage("model.error.accessApprovalRequired", Locale.ENGLISH, "approval required");
        source.addMessage("model.error.accessRequestNotFound", Locale.ENGLISH, "request not found");
        return new MessageService(source);
    }
}
