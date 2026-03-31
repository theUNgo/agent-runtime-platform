package com.example.agentruntime.model;

import com.example.agentruntime.agent.AgentModelSelection;
import com.example.agentruntime.auth.CurrentUserService;
import com.example.agentruntime.i18n.MessageService;
import com.example.agentruntime.persistence.entity.GlobalModelProfileEntity;
import com.example.agentruntime.persistence.entity.UserAccountEntity;
import com.example.agentruntime.persistence.entity.UserEnabledModelEntity;
import com.example.agentruntime.persistence.repository.GlobalModelProfileRepository;
import com.example.agentruntime.persistence.repository.UserAccountRepository;
import com.example.agentruntime.persistence.repository.UserEnabledModelRepository;
import com.example.agentruntime.security.ApiKeyCryptoService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * 用户模型档案服务。
 * 当前先提供“多模型档案 + 默认模型切换”的基础能力，为后续真正接入远程模型调用做准备。
 */
@Service
public class UserModelProfileService {

    private final GlobalModelProfileRepository profileRepository;
    private final UserEnabledModelRepository userEnabledModelRepository;
    private final UserAccountRepository userAccountRepository;
    private final CurrentUserService currentUserService;
    private final MessageService messageService;
    private final ApiKeyCryptoService apiKeyCryptoService;

    public UserModelProfileService(GlobalModelProfileRepository profileRepository,
                                   UserEnabledModelRepository userEnabledModelRepository,
                                   UserAccountRepository userAccountRepository,
                                   CurrentUserService currentUserService,
                                   MessageService messageService,
                                   ApiKeyCryptoService apiKeyCryptoService) {
        this.profileRepository = profileRepository;
        this.userEnabledModelRepository = userEnabledModelRepository;
        this.userAccountRepository = userAccountRepository;
        this.currentUserService = currentUserService;
        this.messageService = messageService;
        this.apiKeyCryptoService = apiKeyCryptoService;
    }

    @Transactional(readOnly = true)
    public List<UserModelProfileView> list(Long userId) {
        UserAccountEntity user = requireUser(userId);
        List<UserEnabledModelEntity> selections = userEnabledModelRepository.findByUserOrderByIsDefaultDescIdDesc(user);
        return profileRepository.findAllByOrderByIdDesc().stream()
                .map(profile -> toView(user, profile, selections))
                .toList();
    }

    @Transactional(readOnly = true)
    public Optional<UserModelProfileView> active(Long userId) {
        UserAccountEntity user = requireUser(userId);
        return resolvePreferredProfile(user)
                .map(profile -> toView(user, profile, userEnabledModelRepository.findByUserOrderByIsDefaultDescIdDesc(user)));
    }

    @Transactional(readOnly = true)
    public Optional<AgentModelSelection> activeSelection(Long userId) {
        UserAccountEntity user = requireUser(userId);
        return resolveEnabledProfile(user)
                .map(entity -> new AgentModelSelection(
                        entity.getId(),
                        entity.getProfileName(),
                        entity.getProviderType(),
                        entity.getBaseUrl(),
                        entity.getModelId(),
                        entity.isSupportsVision(),
                        entity.isSupportsAudio()
                ));
    }

    /**
     * 读取指定模型档案对应的轻量选择信息。
     * 该方法主要给会话阶段的“临时模型覆盖”使用。
     */
    @Transactional(readOnly = true)
    public Optional<AgentModelSelection> selection(Long userId, Long profileId) {
        UserAccountEntity user = requireUser(userId);
        return resolveAccessibleProfile(user, profileId).map(this::toSelection);
    }

    @Transactional(readOnly = true)
    public Optional<ModelRuntimeProfile> activeRuntimeProfile(Long userId) {
        UserAccountEntity user = requireUser(userId);
        return resolveEnabledProfile(user).map(this::toRuntimeProfile);
    }

    @Transactional(readOnly = true)
    public Optional<ModelRuntimeProfile> runtimeProfile(Long userId, Long profileId) {
        UserAccountEntity user = requireUser(userId);
        return resolveAccessibleProfile(user, profileId).map(this::toRuntimeProfile);
    }

    @Transactional
    public UserModelProfileView create(Long userId, UserModelProfileUpsertRequest request) {
        UserAccountEntity user = requireUser(userId);
        currentUserService.requireAdmin();
        validateRequest(request);

        profileRepository.findByProfileName(request.profileName().trim())
                .ifPresent(existing -> {
                    throw new IllegalArgumentException(messageService.get("model.error.profileNameExists", request.profileName()));
                });

        GlobalModelProfileEntity entity = new GlobalModelProfileEntity();
        entity.setManagedByUser(user);
        applyRequest(entity, request, true);
        GlobalModelProfileEntity saved = profileRepository.save(entity);
        approveForUser(user, user, saved, request.makeDefault(), null);
        return toView(user, saved, userEnabledModelRepository.findByUserOrderByIsDefaultDescIdDesc(user));
    }

    @Transactional
    public UserModelProfileView update(Long userId, Long profileId, UserModelProfileUpsertRequest request) {
        UserAccountEntity user = requireUser(userId);
        currentUserService.requireAdmin();
        validateRequest(request);

        GlobalModelProfileEntity entity = profileRepository.findById(profileId)
                .orElseThrow(() -> new IllegalArgumentException(messageService.get("model.error.profileNotFound", profileId)));

        profileRepository.findByProfileName(request.profileName().trim())
                .filter(existing -> !existing.getId().equals(profileId))
                .ifPresent(existing -> {
                    throw new IllegalArgumentException(messageService.get("model.error.profileNameExists", request.profileName()));
                });

        applyRequest(entity, request, false);
        GlobalModelProfileEntity saved = profileRepository.save(entity);
        if (request.makeDefault()) {
            approveForUser(user, user, saved, true, null);
        }
        return toView(user, saved, userEnabledModelRepository.findByUserOrderByIsDefaultDescIdDesc(user));
    }

    @Transactional
    public UserModelProfileView activate(Long userId, Long profileId) {
        UserAccountEntity user = requireUser(userId);
        GlobalModelProfileEntity entity = profileRepository.findById(profileId)
                .filter(GlobalModelProfileEntity::isEnabled)
                .orElseThrow(() -> new IllegalArgumentException(messageService.get("model.error.profileNotFound", profileId)));
        if (isAdmin(user)) {
            approveForUser(user, user, entity, true, null);
        } else {
            UserEnabledModelEntity relation = userEnabledModelRepository.findByUserAndModelProfile(user, entity)
                    .filter(candidate -> candidate.getAccessStatus() == ModelAccessStatus.APPROVED)
                    .orElseThrow(() -> new IllegalArgumentException(messageService.get("model.error.accessApprovalRequired", profileId)));
            relation.setEnabled(true);
            clearDefault(user);
            relation.setDefault(true);
            userEnabledModelRepository.save(relation);
        }
        return toView(user, entity, userEnabledModelRepository.findByUserOrderByIsDefaultDescIdDesc(user));
    }

    @Transactional
    public void delete(Long userId, Long profileId) {
        currentUserService.requireAdmin();
        GlobalModelProfileEntity entity = profileRepository.findById(profileId)
                .orElseThrow(() -> new IllegalArgumentException(messageService.get("model.error.profileNotFound", profileId)));
        profileRepository.delete(entity);
    }

    @Transactional
    public UserModelProfileView enable(Long userId, Long profileId) {
        UserAccountEntity user = requireUser(userId);
        GlobalModelProfileEntity profile = profileRepository.findById(profileId)
                .filter(GlobalModelProfileEntity::isEnabled)
                .orElseThrow(() -> new IllegalArgumentException(messageService.get("model.error.profileNotFound", profileId)));
        if (isAdmin(user)) {
            approveForUser(user, user, profile, false, null);
        } else {
            requestAccess(user, profile);
        }
        return toView(user, profile, userEnabledModelRepository.findByUserOrderByIsDefaultDescIdDesc(user));
    }

    @Transactional
    public void disable(Long userId, Long profileId) {
        UserAccountEntity user = requireUser(userId);
        GlobalModelProfileEntity profile = profileRepository.findById(profileId)
                .orElseThrow(() -> new IllegalArgumentException(messageService.get("model.error.profileNotFound", profileId)));
        userEnabledModelRepository.findByUserAndModelProfile(user, profile).ifPresent(userEnabledModelRepository::delete);
    }

    /**
     * 管理员查看模型访问申请。
     * 这里默认按申请时间升序返回，便于优先处理较早的请求。
     */
    @Transactional(readOnly = true)
    public List<AdminModelAccessRequestView> listAccessRequests(ModelAccessStatus status) {
        List<UserEnabledModelEntity> relations = status == null
                ? userEnabledModelRepository.findAll().stream()
                .filter(candidate -> candidate.getAccessStatus() != null && candidate.getAccessStatus() != ModelAccessStatus.NOT_REQUESTED)
                .sorted(Comparator.comparing(UserEnabledModelEntity::getRequestedAt, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList()
                : userEnabledModelRepository.findByAccessStatusOrderByRequestedAtAsc(status);
        return relations.stream()
                .map(this::toRequestView)
                .toList();
    }

    /**
     * 管理员给指定用户直接授权某个模型。
     */
    @Transactional
    public UserModelProfileView grant(Long adminUserId, AdminModelAccessGrantRequest request) {
        UserAccountEntity admin = requireUser(adminUserId);
        UserAccountEntity targetUser = requireUser(request.userId());
        GlobalModelProfileEntity profile = profileRepository.findById(request.profileId())
                .filter(GlobalModelProfileEntity::isEnabled)
                .orElseThrow(() -> new IllegalArgumentException(messageService.get("model.error.profileNotFound", request.profileId())));
        approveForUser(admin, targetUser, profile, request.makeDefault(), null);
        return toView(targetUser, profile, userEnabledModelRepository.findByUserOrderByIsDefaultDescIdDesc(targetUser));
    }

    /**
     * 管理员审批通过某条模型访问申请。
     */
    @Transactional
    public AdminModelAccessRequestView approveRequest(Long adminUserId, Long requestId, AdminModelAccessReviewRequest request) {
        UserAccountEntity admin = requireUser(adminUserId);
        UserEnabledModelEntity relation = userEnabledModelRepository.findById(requestId)
                .orElseThrow(() -> new IllegalArgumentException(messageService.get("model.error.accessRequestNotFound", requestId)));
        approveRelation(admin, relation, request.makeDefault(), request.comment());
        return toRequestView(relation);
    }

    /**
     * 管理员拒绝某条模型访问申请。
     */
    @Transactional
    public AdminModelAccessRequestView rejectRequest(Long adminUserId, Long requestId, AdminModelAccessReviewRequest request) {
        UserAccountEntity admin = requireUser(adminUserId);
        UserEnabledModelEntity relation = userEnabledModelRepository.findById(requestId)
                .orElseThrow(() -> new IllegalArgumentException(messageService.get("model.error.accessRequestNotFound", requestId)));
        relation.setAccessStatus(ModelAccessStatus.REJECTED);
        relation.setEnabled(false);
        relation.setDefault(false);
        relation.setReviewedByUser(admin);
        relation.setReviewedAt(Instant.now());
        relation.setReviewComment(request.comment());
        userEnabledModelRepository.save(relation);
        return toRequestView(relation);
    }

    private Optional<GlobalModelProfileEntity> resolvePreferredProfile(UserAccountEntity user) {
        if (isAdmin(user)) {
            return profileRepository.findByEnabledTrueOrderByIdDesc().stream().findFirst();
        }
        List<UserEnabledModelEntity> selections = userEnabledModelRepository.findByUserOrderByIsDefaultDescIdDesc(user);
        Optional<GlobalModelProfileEntity> defaultSelection = selections.stream()
                .filter(UserEnabledModelEntity::isDefault)
                .filter(UserEnabledModelEntity::isEnabled)
                .filter(candidate -> candidate.getAccessStatus() == ModelAccessStatus.APPROVED)
                .map(UserEnabledModelEntity::getModelProfile)
                .filter(GlobalModelProfileEntity::isEnabled)
                .findFirst();
        if (defaultSelection.isPresent()) {
            return defaultSelection;
        }
        Optional<GlobalModelProfileEntity> firstSelected = selections.stream()
                .filter(UserEnabledModelEntity::isEnabled)
                .filter(candidate -> candidate.getAccessStatus() == ModelAccessStatus.APPROVED)
                .map(UserEnabledModelEntity::getModelProfile)
                .filter(GlobalModelProfileEntity::isEnabled)
                .findFirst();
        if (firstSelected.isPresent()) {
            return firstSelected;
        }
        return Optional.empty();
    }

    private Optional<GlobalModelProfileEntity> resolveEnabledProfile(UserAccountEntity user) {
        return resolvePreferredProfile(user);
    }

    private Optional<GlobalModelProfileEntity> resolveAccessibleProfile(UserAccountEntity user, Long profileId) {
        Optional<GlobalModelProfileEntity> profile = profileRepository.findById(profileId)
                .filter(GlobalModelProfileEntity::isEnabled);
        if (profile.isEmpty()) {
            return Optional.empty();
        }
        if (isAdmin(user)) {
            return profile;
        }
        List<UserEnabledModelEntity> selections = userEnabledModelRepository.findByUserOrderByIsDefaultDescIdDesc(user);
        return selections.stream()
                .filter(UserEnabledModelEntity::isEnabled)
                .filter(candidate -> candidate.getAccessStatus() == ModelAccessStatus.APPROVED)
                .map(UserEnabledModelEntity::getModelProfile)
                .filter(candidate -> candidate.getId().equals(profileId))
                .findFirst();
    }

    private void applyRequest(GlobalModelProfileEntity entity, UserModelProfileUpsertRequest request, boolean creating) {
        entity.setProfileName(request.profileName().trim());
        entity.setProviderType(normalizeProvider(request.providerType()));
        entity.setBaseUrl(request.baseUrl().trim());
        entity.setModelId(request.modelId().trim());
        entity.setSupportsText(request.supportsText());
        entity.setSupportsVision(request.supportsVision());
        entity.setSupportsAudio(request.supportsAudio());
        entity.setPreferredForGeneralChat(request.preferredForGeneralChat());
        entity.setPreferredForTools(request.preferredForTools());
        entity.setPreferredForSkills(request.preferredForSkills());
        entity.setEnabled(request.enabled());

        String resolvedApiKey = resolveIncomingApiKey(request);
        if (creating && (resolvedApiKey == null || resolvedApiKey.isBlank())) {
            throw new IllegalArgumentException(messageService.get("model.error.apiKeyRequired"));
        }
        if (resolvedApiKey != null && !resolvedApiKey.isBlank()) {
            entity.setApiKey(apiKeyCryptoService.encryptForStorage(resolvedApiKey.trim()));
        } else if (!creating && entity.getApiKey() != null && !entity.getApiKey().isBlank() && !entity.getApiKey().startsWith("enc:v1:")) {
            entity.setApiKey(apiKeyCryptoService.encryptForStorage(entity.getApiKey()));
        }
    }

    private void validateRequest(UserModelProfileUpsertRequest request) {
        if (request.profileName() == null || request.profileName().isBlank()) {
            throw new IllegalArgumentException(messageService.get("model.error.profileNameRequired"));
        }
        if (request.baseUrl() == null || request.baseUrl().isBlank()) {
            throw new IllegalArgumentException(messageService.get("model.error.baseUrlRequired"));
        }
        if (request.modelId() == null || request.modelId().isBlank()) {
            throw new IllegalArgumentException(messageService.get("model.error.modelIdRequired"));
        }
    }

    private void clearDefault(UserAccountEntity user) {
        userEnabledModelRepository.findByUserOrderByIsDefaultDescIdDesc(user).stream()
                .filter(UserEnabledModelEntity::isDefault)
                .forEach(entity -> {
                    entity.setDefault(false);
                    userEnabledModelRepository.save(entity);
                });
    }

    /**
     * 为用户启用某个全局模型，并按需设为默认。
     * 用户没有任何启用关系时，也会自动把第一个启用模型视为默认模型。
     */
    private void requestAccess(UserAccountEntity user, GlobalModelProfileEntity profile) {
        UserEnabledModelEntity relation = userEnabledModelRepository.findByUserAndModelProfile(user, profile)
                .orElseGet(UserEnabledModelEntity::new);
        boolean existingApproved = relation.getId() != null && relation.getAccessStatus() == ModelAccessStatus.APPROVED;
        relation.setUser(user);
        relation.setModelProfile(profile);
        relation.setRequestedAt(Instant.now());
        relation.setReviewedAt(null);
        relation.setReviewedByUser(null);
        relation.setDefault(false);
        if (existingApproved) {
            relation.setEnabled(true);
        } else {
            relation.setEnabled(false);
            relation.setAccessStatus(ModelAccessStatus.PENDING);
            relation.setReviewComment(null);
        }
        userEnabledModelRepository.save(relation);
    }

    private void approveForUser(UserAccountEntity admin,
                                UserAccountEntity targetUser,
                                GlobalModelProfileEntity profile,
                                boolean makeDefault,
                                String comment) {
        UserEnabledModelEntity relation = userEnabledModelRepository.findByUserAndModelProfile(targetUser, profile)
                .orElseGet(UserEnabledModelEntity::new);
        relation.setUser(targetUser);
        relation.setModelProfile(profile);
        approveRelation(admin, relation, makeDefault, comment);
    }

    private void approveRelation(UserAccountEntity admin,
                                 UserEnabledModelEntity relation,
                                 boolean makeDefault,
                                 String comment) {
        relation.setAccessStatus(ModelAccessStatus.APPROVED);
        relation.setEnabled(true);
        relation.setRequestedAt(relation.getRequestedAt() == null ? Instant.now() : relation.getRequestedAt());
        relation.setReviewedAt(Instant.now());
        relation.setReviewedByUser(admin);
        relation.setReviewComment(comment);

        boolean shouldBecomeDefault = makeDefault || userEnabledModelRepository.findByUserAndIsDefaultTrue(relation.getUser()).isEmpty();
        if (shouldBecomeDefault) {
            clearDefault(relation.getUser());
            relation.setDefault(true);
        }
        userEnabledModelRepository.save(relation);
    }

    private UserAccountEntity requireUser(Long userId) {
        return userAccountRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException(messageService.get("auth.error.userNotFound")));
    }

    private UserModelProfileView toView(UserAccountEntity user,
                                        GlobalModelProfileEntity entity,
                                        List<UserEnabledModelEntity> selections) {
        Optional<UserEnabledModelEntity> selection = selections.stream()
                .filter(candidate -> candidate.getModelProfile().getId().equals(entity.getId()))
                .findFirst();
        ModelAccessStatus accessStatus = resolveAccessStatus(user, selection);
        boolean enabledForUser = isAdmin(user)
                ? entity.isEnabled()
                : selection.map(candidate -> candidate.isEnabled() && candidate.getAccessStatus() == ModelAccessStatus.APPROVED).orElse(false);
        return new UserModelProfileView(
                entity.getId(),
                entity.getProfileName(),
                entity.getProviderType(),
                entity.getBaseUrl(),
                entity.getModelId(),
                entity.isSupportsText(),
                entity.isSupportsVision(),
                entity.isSupportsAudio(),
                entity.isPreferredForGeneralChat(),
                entity.isPreferredForTools(),
                entity.isPreferredForSkills(),
                enabledForUser,
                selection.map(UserEnabledModelEntity::isDefault).orElse(false),
                entity.getApiKey() != null && !entity.getApiKey().isBlank(),
                maskApiKey(entity.getApiKey()),
                accessStatus.name(),
                selection.map(UserEnabledModelEntity::getReviewComment).orElse(null)
        );
    }

    private ModelAccessStatus resolveAccessStatus(UserAccountEntity user, Optional<UserEnabledModelEntity> selection) {
        if (isAdmin(user)) {
            return ModelAccessStatus.APPROVED;
        }
        return selection.map(UserEnabledModelEntity::getAccessStatus).orElse(ModelAccessStatus.NOT_REQUESTED);
    }

    private AdminModelAccessRequestView toRequestView(UserEnabledModelEntity relation) {
        return new AdminModelAccessRequestView(
                relation.getId(),
                relation.getUser().getId(),
                relation.getUser().getUsername(),
                relation.getUser().getDisplayName(),
                relation.getModelProfile().getId(),
                relation.getModelProfile().getProfileName(),
                relation.getAccessStatus(),
                relation.isEnabled(),
                relation.isDefault(),
                relation.getReviewComment(),
                relation.getRequestedAt(),
                relation.getReviewedAt(),
                relation.getReviewedByUser() == null ? null : relation.getReviewedByUser().getDisplayName()
        );
    }

    private ModelRuntimeProfile toRuntimeProfile(GlobalModelProfileEntity entity) {
        return new ModelRuntimeProfile(
                entity.getId(),
                entity.getProfileName(),
                entity.getProviderType(),
                entity.getBaseUrl(),
                apiKeyCryptoService.decryptFromStorage(entity.getApiKey()),
                entity.getModelId(),
                entity.isSupportsText(),
                entity.isSupportsVision(),
                entity.isSupportsAudio(),
                entity.isPreferredForGeneralChat(),
                entity.isPreferredForTools(),
                entity.isPreferredForSkills(),
                entity.isEnabled()
        );
    }

    private AgentModelSelection toSelection(GlobalModelProfileEntity entity) {
        return new AgentModelSelection(
                entity.getId(),
                entity.getProfileName(),
                entity.getProviderType(),
                entity.getBaseUrl(),
                entity.getModelId(),
                entity.isSupportsVision(),
                entity.isSupportsAudio()
        );
    }

    private String normalizeProvider(String providerType) {
        String value = providerType == null || providerType.isBlank()
                ? "openai-compatible"
                : providerType.trim().toLowerCase(Locale.ROOT);
        return value;
    }

    private String maskApiKey(String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            return "";
        }
        if (apiKey.startsWith("enc:v1:")) {
            apiKey = apiKeyCryptoService.decryptFromStorage(apiKey);
        }
        if (apiKey.length() <= 8) {
            return "********";
        }
        return apiKey.substring(0, 3) + "****" + apiKey.substring(apiKey.length() - 4);
    }

    private String resolveIncomingApiKey(UserModelProfileUpsertRequest request) {
        if (request.apiKeyEncrypted() != null && !request.apiKeyEncrypted().isBlank()) {
            return apiKeyCryptoService.decryptTransportValue(request.apiKeyEncrypted());
        }
        if (request.apiKey() != null && !request.apiKey().isBlank()) {
            return request.apiKey();
        }
        return null;
    }

    private boolean isAdmin(UserAccountEntity user) {
        return user != null && user.getRole() != null && user.getRole().isAdmin();
    }
}
