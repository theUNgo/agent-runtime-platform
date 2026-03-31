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
 * 用户自定义试跑模板。
 * 这里保存用户自己沉淀下来的模型联调模板，便于跨浏览器和跨设备复用。
 */
@Entity
@Table(name = "user_preview_template",
        uniqueConstraints = @UniqueConstraint(name = "uk_user_preview_template_key", columnNames = {"user_id", "template_key"}))
public class UserPreviewTemplateEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private UserAccountEntity user;

    @Column(name = "template_key", nullable = false, length = 120)
    private String templateKey;

    @Column(nullable = false, length = 120)
    private String label;

    @Column(nullable = false, length = 255)
    private String description;

    @Column(name = "message_text", length = 4000)
    private String messageText;

    @Column(name = "system_prompt", length = 4000)
    private String systemPrompt;

    @Column(name = "examples_json", length = 12000)
    private String examplesJson;

    @Column(name = "output_guide", length = 4000)
    private String outputGuide;

    @Column(name = "payload_json", length = 12000)
    private String payloadJson;

    @Column(name = "thinking_enabled", nullable = false)
    private boolean thinkingEnabled;

    @Column(name = "notes_json", length = 4000)
    private String notesJson;

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

    public String getTemplateKey() {
        return templateKey;
    }

    public void setTemplateKey(String templateKey) {
        this.templateKey = templateKey;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getMessageText() {
        return messageText;
    }

    public void setMessageText(String messageText) {
        this.messageText = messageText;
    }

    public String getSystemPrompt() {
        return systemPrompt;
    }

    public void setSystemPrompt(String systemPrompt) {
        this.systemPrompt = systemPrompt;
    }

    public String getExamplesJson() {
        return examplesJson;
    }

    public void setExamplesJson(String examplesJson) {
        this.examplesJson = examplesJson;
    }

    public String getOutputGuide() {
        return outputGuide;
    }

    public void setOutputGuide(String outputGuide) {
        this.outputGuide = outputGuide;
    }

    public String getPayloadJson() {
        return payloadJson;
    }

    public void setPayloadJson(String payloadJson) {
        this.payloadJson = payloadJson;
    }

    public boolean isThinkingEnabled() {
        return thinkingEnabled;
    }

    public void setThinkingEnabled(boolean thinkingEnabled) {
        this.thinkingEnabled = thinkingEnabled;
    }

    public String getNotesJson() {
        return notesJson;
    }

    public void setNotesJson(String notesJson) {
        this.notesJson = notesJson;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
