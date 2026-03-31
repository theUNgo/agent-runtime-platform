package com.example.agentruntime.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;

/**
 * 管理员维护的全局模型档案。
 * 这里保存真正的模型连接信息与密钥，普通用户只选择启用哪些全局模型，
 * 不再为每个人重复维护一套同样的配置。
 */
@Entity
@Table(name = "global_model_profile",
        uniqueConstraints = @UniqueConstraint(name = "uk_global_model_profile_name", columnNames = {"profile_name"}))
public class GlobalModelProfileEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "profile_name", nullable = false, length = 120)
    private String profileName;

    @Column(name = "provider_type", nullable = false, length = 64)
    private String providerType;

    @Column(name = "base_url", nullable = false, length = 500)
    private String baseUrl;

    @Column(name = "api_key", length = 1000)
    private String apiKey;

    @Column(name = "model_id", nullable = false, length = 200)
    private String modelId;

    @Column(name = "supports_text", nullable = false)
    private boolean supportsText = true;

    @Column(name = "supports_vision", nullable = false)
    private boolean supportsVision;

    @Column(name = "supports_audio", nullable = false)
    private boolean supportsAudio;

    @Column(name = "preferred_for_general_chat", nullable = false)
    private boolean preferredForGeneralChat = true;

    @Column(name = "preferred_for_tools", nullable = false)
    private boolean preferredForTools = true;

    @Column(name = "preferred_for_skills", nullable = false)
    private boolean preferredForSkills = true;

    @Column(nullable = false)
    private boolean enabled = true;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "managed_by_user_id")
    private UserAccountEntity managedByUser;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public String getProfileName() {
        return profileName;
    }

    public void setProfileName(String profileName) {
        this.profileName = profileName;
    }

    public String getProviderType() {
        return providerType;
    }

    public void setProviderType(String providerType) {
        this.providerType = providerType;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getModelId() {
        return modelId;
    }

    public void setModelId(String modelId) {
        this.modelId = modelId;
    }

    public boolean isSupportsText() {
        return supportsText;
    }

    public void setSupportsText(boolean supportsText) {
        this.supportsText = supportsText;
    }

    public boolean isSupportsVision() {
        return supportsVision;
    }

    public void setSupportsVision(boolean supportsVision) {
        this.supportsVision = supportsVision;
    }

    public boolean isSupportsAudio() {
        return supportsAudio;
    }

    public void setSupportsAudio(boolean supportsAudio) {
        this.supportsAudio = supportsAudio;
    }

    public boolean isPreferredForGeneralChat() {
        return preferredForGeneralChat;
    }

    public void setPreferredForGeneralChat(boolean preferredForGeneralChat) {
        this.preferredForGeneralChat = preferredForGeneralChat;
    }

    public boolean isPreferredForTools() {
        return preferredForTools;
    }

    public void setPreferredForTools(boolean preferredForTools) {
        this.preferredForTools = preferredForTools;
    }

    public boolean isPreferredForSkills() {
        return preferredForSkills;
    }

    public void setPreferredForSkills(boolean preferredForSkills) {
        this.preferredForSkills = preferredForSkills;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public UserAccountEntity getManagedByUser() {
        return managedByUser;
    }

    public void setManagedByUser(UserAccountEntity managedByUser) {
        this.managedByUser = managedByUser;
    }
}
