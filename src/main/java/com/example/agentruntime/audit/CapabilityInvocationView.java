package com.example.agentruntime.audit;

import java.time.Instant;

/**
 * 能力调用审计视图。
 */
public record CapabilityInvocationView(
        Long id,
        String conversationId,
        String capabilityId,
        String capabilityName,
        String capabilityType,
        String provider,
        String status,
        String requestJson,
        String resultJson,
        String traceJson,
        String errorMessage,
        Long durationMs,
        Instant createdAt
) {
}
