package com.example.agentruntime.agent;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;

/**
 * Agent 执行请求。
 * modelProfileId 允许前端为当前这一次对话临时指定模型档案，
 * 避免用户必须先切换全局默认模型才能开始试聊。
 */
public record AgentRequest(
        String conversationId,
        @NotBlank String message,
        JsonNode input,
        Long modelProfileId
) {
}
