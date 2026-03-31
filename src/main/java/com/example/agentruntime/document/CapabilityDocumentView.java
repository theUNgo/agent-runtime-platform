package com.example.agentruntime.document;

import java.util.List;

/**
 * 能力文档详情视图。
 * detail 文档允许比 summary 更长，但仍然只暴露固定白名单文档，不允许任意读文件。
 */
public record CapabilityDocumentView(
        String docId,
        String capabilityId,
        String name,
        String capabilityType,
        String docType,
        String content,
        List<String> tags
) {

    public CapabilityDocumentView {
        tags = tags == null ? List.of() : List.copyOf(tags);
    }
}
