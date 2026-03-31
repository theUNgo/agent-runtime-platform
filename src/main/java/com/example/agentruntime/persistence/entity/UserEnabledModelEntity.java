package com.example.agentruntime.persistence.entity;

import com.example.agentruntime.model.ModelAccessStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
 * 用户对全局模型的访问关系。
 * 这里不仅保存“是否启用”和“是否默认”，还会记录审批状态，
 * 用于支撑“用户申请模型使用权限，管理员审批后才能真正使用”的管控模式。
 */
@Entity
@Table(name = "user_enabled_model",
        uniqueConstraints = @UniqueConstraint(name = "uk_user_enabled_model", columnNames = {"user_id", "model_profile_id"}))
public class UserEnabledModelEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private UserAccountEntity user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "model_profile_id", nullable = false)
    private GlobalModelProfileEntity modelProfile;

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(name = "is_default", nullable = false)
    private boolean isDefault;

    @Enumerated(EnumType.STRING)
    @Column(name = "access_status", nullable = false, length = 32)
    private ModelAccessStatus accessStatus = ModelAccessStatus.APPROVED;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewed_by_user_id")
    private UserAccountEntity reviewedByUser;

    @Column(name = "review_comment", length = 500)
    private String reviewComment;

    @Column(name = "requested_at")
    private Instant requestedAt;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

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

    public GlobalModelProfileEntity getModelProfile() {
        return modelProfile;
    }

    public void setModelProfile(GlobalModelProfileEntity modelProfile) {
        this.modelProfile = modelProfile;
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

    public ModelAccessStatus getAccessStatus() {
        return accessStatus;
    }

    public void setAccessStatus(ModelAccessStatus accessStatus) {
        this.accessStatus = accessStatus == null ? ModelAccessStatus.NOT_REQUESTED : accessStatus;
    }

    public UserAccountEntity getReviewedByUser() {
        return reviewedByUser;
    }

    public void setReviewedByUser(UserAccountEntity reviewedByUser) {
        this.reviewedByUser = reviewedByUser;
    }

    public String getReviewComment() {
        return reviewComment;
    }

    public void setReviewComment(String reviewComment) {
        this.reviewComment = reviewComment;
    }

    public Instant getRequestedAt() {
        return requestedAt;
    }

    public void setRequestedAt(Instant requestedAt) {
        this.requestedAt = requestedAt;
    }

    public Instant getReviewedAt() {
        return reviewedAt;
    }

    public void setReviewedAt(Instant reviewedAt) {
        this.reviewedAt = reviewedAt;
    }
}
