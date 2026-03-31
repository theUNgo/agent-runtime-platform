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
 * 用户模型档案。
 * 用于保存用户自己的模型地址、模型名、鉴权信息和能力偏好。
 */
@Entity
@Table(name = "user_model_profile",
        uniqueConstraints = @UniqueConstraint(name = "uk_user_model_profile_name", columnNames = {"user_id", "profile_name"}))
public class UserModelProfileEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private UserAccountEntity user;

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

    @Column(name = "is_default", nullable = false)
    private boolean isDefault;

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

    public UserAccountEntity getUser() {
        return user;
    }

    public void setUser(UserAccountEntity user) {
        this.user = user;
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

    public boolean isDefault() {
        return isDefault;
    }

    public void setDefault(boolean aDefault) {
        isDefault = aDefault;
    }
}
