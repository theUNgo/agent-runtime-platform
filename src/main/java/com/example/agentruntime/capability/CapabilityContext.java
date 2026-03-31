package com.example.agentruntime.capability;

import com.example.agentruntime.agent.AgentModelSelection;

import java.time.Instant;

public record CapabilityContext(
        String conversationId,
        String userMessage,
        String workspaceRoot,
        AgentModelSelection modelSelection,
        Instant startedAt
) {

    public CapabilityContext(String conversationId, String userMessage, String workspaceRoot) {
        this(conversationId, userMessage, workspaceRoot, null, Instant.now());
    }

    public CapabilityContext(String conversationId, String userMessage, String workspaceRoot, AgentModelSelection modelSelection) {
        this(conversationId, userMessage, workspaceRoot, modelSelection, Instant.now());
    }
}
