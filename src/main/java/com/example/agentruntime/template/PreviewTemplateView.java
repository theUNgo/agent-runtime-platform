package com.example.agentruntime.template;

import java.time.Instant;
import java.util.List;

/**
 * 返回给前端的用户自定义试跑模板视图。
 */
public record PreviewTemplateView(
        String templateKey,
        String label,
        String description,
        String category,
        String message,
        String systemPrompt,
        String examplesJson,
        String outputGuide,
        String payloadJson,
        boolean thinkingEnabled,
        List<String> notes,
        Instant createdAt,
        Instant updatedAt
) {
}
