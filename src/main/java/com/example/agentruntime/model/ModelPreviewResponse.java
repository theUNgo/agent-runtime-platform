package com.example.agentruntime.model;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * 模型试跑结果。
 * 该结构用于前端直接展示模型回复、结束原因以及原始响应片段。
 */
public record ModelPreviewResponse(
        Long profileId,
        String profileName,
        String providerType,
        String modelId,
        String requestMessage,
        String content,
        String finishReason,
        JsonNode rawResponse
) {
}
