package com.example.agentruntime.context;

/**
 * 上下文片段模型。
 * 用统一结构描述系统提示、当前消息、历史背景等不同上下文块，便于后续做预算评估和压缩策略。
 */
public record ContextSegment(
        String id,
        String type,
        String source,
        String content,
        int estimatedTokens,
        int priority,
        boolean compressible
) {
}
