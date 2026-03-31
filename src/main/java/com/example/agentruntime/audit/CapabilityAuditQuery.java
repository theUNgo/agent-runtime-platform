package com.example.agentruntime.audit;

/**
 * 审计查询条件。
 */
public record CapabilityAuditQuery(
        String conversationId,
        String status,
        String capabilityType,
        String keyword,
        Integer limit
) {
}