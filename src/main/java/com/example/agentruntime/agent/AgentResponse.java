package com.example.agentruntime.agent;

import java.util.List;

public record AgentResponse(
        String conversationId,
        String userMessage,
        String decision,
        List<ExecutionStep> steps,
        AgentModelSelection modelSelection
) {
}
