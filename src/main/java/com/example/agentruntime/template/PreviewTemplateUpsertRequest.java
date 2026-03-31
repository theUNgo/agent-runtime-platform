package com.example.agentruntime.template;

import java.util.List;

/**
 * 自定义试跑模板写入请求。
 * 前端会把当前试跑面板的核心内容整体提交到这里做持久化。
 */
public record PreviewTemplateUpsertRequest(
        String templateKey,
        String label,
        String description,
        String message,
        String systemPrompt,
        String examplesJson,
        String outputGuide,
        String payloadJson,
        boolean thinkingEnabled,
        List<String> notes
) {
}
