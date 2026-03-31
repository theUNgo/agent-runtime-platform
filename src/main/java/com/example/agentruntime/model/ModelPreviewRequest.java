package com.example.agentruntime.model;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * 模型试跑请求。
 * message 用于描述本次试跑目标，systemPrompt 和 examples 用于做
 * 更贴近真实联调场景的指令约束与 few-shot 预热，input 保留原始 JSON，
 * 便于前端按需透传 imageUrls、thinking、temperature 等扩展字段。
 */
public record ModelPreviewRequest(
        String message,
        String systemPrompt,
        JsonNode examples,
        JsonNode input
) {
}
