package com.example.agentruntime.document;

import java.util.List;

/**
 * 能力文档摘要视图。
 * 用于给模型和前端先看“概述索引”，再决定是否继续读取详情文档。
 */
public record CapabilityDocumentSummary(
        String docId,
        String capabilityId,
        String name,
        String capabilityType,
        String sourceType,
        String summary,
        List<String> tags
) {

    public CapabilityDocumentSummary {
        tags = tags == null ? List.of() : List.copyOf(tags);
    }
}
