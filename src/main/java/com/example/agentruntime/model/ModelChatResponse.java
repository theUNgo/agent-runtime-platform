package com.example.agentruntime.model;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * 统一的模型输出结果。
 */
public record ModelChatResponse(
        String content,
        String finishReason,
        String providerType,
        String modelId,
        JsonNode rawResponse
) {
}
