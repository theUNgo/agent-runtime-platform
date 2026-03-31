package com.example.agentruntime.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * 能力调用审计表。
 */
@Entity
@Table(name = "capability_invocation")
public class CapabilityInvocationEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private UserAccountEntity user;

    @Column(name = "conversation_id", length = 64)
    private String conversationId;

    @Column(name = "capability_id", nullable = false, length = 255)
    private String capabilityId;

    @Column(name = "capability_name", nullable = false, length = 255)
    private String capabilityName;

    @Column(name = "capability_type", nullable = false, length = 64)
    private String capabilityType;

    @Column(length = 120)
    private String provider;

    @Column(nullable = false, length = 32)
    private String status;

    @Lob
    @Column(name = "request_json", columnDefinition = "LONGTEXT")
    private String requestJson;

    @Lob
    @Column(name = "result_json", columnDefinition = "LONGTEXT")
    private String resultJson;

    @Lob
    @Column(name = "trace_json", columnDefinition = "LONGTEXT")
    private String traceJson;

    @Lob
    @Column(name = "error_message", columnDefinition = "LONGTEXT")
    private String errorMessage;

    @Column(name = "duration_ms")
    private Long durationMs;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }

    public void setUser(UserAccountEntity user) {
        this.user = user;
    }

    public void setConversationId(String conversationId) {
        this.conversationId = conversationId;
    }

    public void setCapabilityId(String capabilityId) {
        this.capabilityId = capabilityId;
    }

    public void setCapabilityName(String capabilityName) {
        this.capabilityName = capabilityName;
    }

    public void setCapabilityType(String capabilityType) {
        this.capabilityType = capabilityType;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public void setRequestJson(String requestJson) {
        this.requestJson = requestJson;
    }

    public void setResultJson(String resultJson) {
        this.resultJson = resultJson;
    }

    public void setTraceJson(String traceJson) {
        this.traceJson = traceJson;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public void setDurationMs(Long durationMs) {
        this.durationMs = durationMs;
    }

    public Long getId() {
        return id;
    }

    public UserAccountEntity getUser() {
        return user;
    }

    public String getConversationId() {
        return conversationId;
    }

    public String getCapabilityId() {
        return capabilityId;
    }

    public String getCapabilityName() {
        return capabilityName;
    }

    public String getCapabilityType() {
        return capabilityType;
    }

    public String getProvider() {
        return provider;
    }

    public String getStatus() {
        return status;
    }

    public String getRequestJson() {
        return requestJson;
    }

    public String getResultJson() {
        return resultJson;
    }

    public String getTraceJson() {
        return traceJson;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public Long getDurationMs() {
        return durationMs;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
