package com.example.agentruntime.model;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * 统一的模型对话请求。
 * systemPrompt 和 examples 用于兼容工作台试跑、few-shot 和更复杂的
 * 联调场景；input 保留原始 JSON，便于兼容图文、思维链开关、温度等供应商兼容字段。
 * backgroundContext 用于承接历史消息压缩后的背景摘要，避免长会话时把整段原文直接塞给模型。
 */
public record ModelChatRequest(
        String userMessage,
        String systemPrompt,
        JsonNode examples,
        JsonNode input,
        JsonNode capabilityResult,
        String backgroundContext
) {
}
