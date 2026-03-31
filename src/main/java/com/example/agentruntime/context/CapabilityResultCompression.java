package com.example.agentruntime.context;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;

/**
 * 能力结果压缩结果。
 * 用于在不改变原始审计和会话落库内容的前提下，
 * 单独给模型提供更轻量的结果上下文和背景摘要。
 */
public record CapabilityResultCompression(
        JsonNode resultForModel,
        String backgroundSummary,
        List<String> compressionActions,
        int rawTokens,
        int finalTokens,
        boolean compressed
) {
}
