package com.example.agentruntime.context;

import java.util.List;

/**
 * 压缩后的上下文快照。
 * 除了最终要喂给模型的背景摘要，还会保留预算报告和压缩动作，方便审计与后续可视化。
 */
public record CompressedContextSnapshot(
        ContextBudgetReport budget,
        List<ContextSegment> originalSegments,
        List<ContextSegment> finalSegments,
        List<String> compressionActions,
        String backgroundSummary
) {
}
