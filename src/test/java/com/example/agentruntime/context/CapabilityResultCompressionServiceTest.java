package com.example.agentruntime.context;

import com.example.agentruntime.capability.CapabilityDescriptor;
import com.example.agentruntime.capability.CapabilityMetadata;
import com.example.agentruntime.capability.CapabilityResult;
import com.example.agentruntime.capability.CapabilityType;
import com.example.agentruntime.capability.RiskLevel;
import com.example.agentruntime.i18n.MessageService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.StaticMessageSource;

import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CapabilityResultCompressionServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldKeepSmallCapabilityResultForModel() {
        CapabilityResultCompressionService service = new CapabilityResultCompressionService(
                new ContextBudgetService(),
                messageService(),
                objectMapper
        );

        CapabilityResultCompression compression = service.compress(
                emptySnapshot(),
                descriptor("skill:code-review", "Code Review", CapabilityType.SKILL),
                CapabilityResult.success(
                        "分析完成",
                        objectMapper.createObjectNode()
                                .put("summary", "发现 2 个潜在问题")
                                .put("severity", "medium")
                )
        );

        assertFalse(compression.compressed());
        assertTrue(compression.resultForModel().path("output").has("summary"));
        assertTrue(compression.compressionActions().contains("kept full capability result"));
    }

    @Test
    void shouldCompressLargeCapabilityResultWhenBudgetIsHigh() {
        CapabilityResultCompressionService service = new CapabilityResultCompressionService(
                new ContextBudgetService(),
                messageService(),
                objectMapper
        );

        CapabilityResultCompression compression = service.compress(
                heavySnapshot(),
                descriptor("mcp:filesystem.read", "Filesystem Read", CapabilityType.MCP_TOOL),
                CapabilityResult.success(
                        "读取完成",
                        objectMapper.createObjectNode()
                                .put("content", "x".repeat(8_000))
                                .put("path", "/tmp/demo.txt")
                                .put("encoding", "utf-8")
                )
        );

        assertTrue(compression.compressed());
        assertTrue(compression.backgroundSummary().contains("最近能力执行摘要"));
        assertTrue(compression.resultForModel().has("summary"));
        assertTrue(compression.resultForModel().has("topLevelFields"));
        assertTrue(compression.finalTokens() < compression.rawTokens());
        assertTrue(compression.compressionActions().contains("compressed capability result"));
    }

    @Test
    void shouldCompressLongResourceReadResultIntoResourceSegments() {
        CapabilityResultCompressionService baseService = new CapabilityResultCompressionService(
                new ContextBudgetService(),
                messageService(),
                objectMapper
        );
        ResourceResultCompressionService resourceService = new ResourceResultCompressionService(
                new ContextBudgetService(),
                messageService(),
                objectMapper
        );

        CapabilityResult result = CapabilityResult.success(
                "读取完成",
                resourceOutput()
        );
        CapabilityDescriptor descriptor = descriptor("mcp:demo:resource-read", "Resource Read", CapabilityType.MCP_TOOL);
        CapabilityResultCompression baseCompression = baseService.compress(emptySnapshot(), descriptor, result);
        CapabilityResultCompression finalCompression = resourceService.compressIfNeeded(
                emptySnapshot(),
                descriptor,
                result,
                baseCompression
        );

        assertTrue(finalCompression.compressed());
        assertTrue(finalCompression.resultForModel().has("resourceContentsSummary"));
        assertTrue(finalCompression.resultForModel().path("resourceCount").asInt() == 2);
        assertTrue(finalCompression.backgroundSummary().contains("最近资源读取摘要"));
        assertTrue(finalCompression.compressionActions().contains("compressed resource content"));
    }

    @Test
    void shouldCompressLongSkillPromptResultIntoSkillSummary() {
        CapabilityResultCompressionService baseService = new CapabilityResultCompressionService(
                new ContextBudgetService(),
                messageService(),
                objectMapper
        );
        SkillResultCompressionService skillService = new SkillResultCompressionService(
                new ContextBudgetService(),
                messageService(),
                objectMapper
        );

        CapabilityResult result = CapabilityResult.success(
                "skill executed",
                objectMapper.createObjectNode()
                        .put("skillId", "code-review")
                        .put("skillType", "prompt")
                        .put("prompt", "You are a code review skill. ".repeat(160))
                        .put("userMessage", "请帮我审查这个 PR")
                        .set("input", objectMapper.createObjectNode()
                                .put("diff", "diff-content")
                                .put("repo", "demo"))
        );
        CapabilityDescriptor descriptor = descriptor("skill:code-review", "Code Review", CapabilityType.SKILL);
        CapabilityResultCompression baseCompression = baseService.compress(emptySnapshot(), descriptor, result);
        CapabilityResultCompression finalCompression = skillService.compressIfNeeded(
                emptySnapshot(),
                descriptor,
                result,
                baseCompression
        );

        assertTrue(finalCompression.compressed());
        assertTrue(finalCompression.resultForModel().has("skillId"));
        assertTrue(finalCompression.resultForModel().has("promptPreview"));
        assertTrue(finalCompression.resultForModel().has("inputKeys"));
        assertTrue(finalCompression.backgroundSummary().contains("Skill"));
        assertTrue(finalCompression.compressionActions().contains("compressed skill result"));
    }

    @Test
    void shouldPreferWorkflowStatsWhenCompressingWorkflowSkillResult() {
        CapabilityResultCompressionService baseService = new CapabilityResultCompressionService(
                new ContextBudgetService(),
                messageService(),
                objectMapper
        );
        SkillResultCompressionService skillService = new SkillResultCompressionService(
                new ContextBudgetService(),
                messageService(),
                objectMapper
        );

        var workflowOutput = objectMapper.createObjectNode();
        workflowOutput.put("skillId", "issue-triage");
        workflowOutput.put("skillType", "workflow");
        workflowOutput.put("workflowSummary", "步骤一完成\n步骤二完成\n步骤三失败但继续".repeat(40));
        workflowOutput.put("workflowBranchSummary", "Workflow 分支摘要：\n- 已执行步骤数：3\n- 已跳过步骤数：1\n- 失败步骤数：1\n- 分支 triage：步骤 3，执行 3，失败 1");
        workflowOutput.set("workflowStats", objectMapper.createObjectNode()
                .put("totalSteps", 4)
                .put("executedSteps", 3)
                .put("skippedSteps", 1)
                .put("failedSteps", 1)
                .put("capabilitySteps", 2)
                .put("continuedFailureSteps", 1)
                .put("branchCount", 1)
                .put("groupCount", 2));
        workflowOutput.set("workflowBranches", objectMapper.createArrayNode()
                .add(objectMapper.createObjectNode()
                        .put("branch", "triage")
                        .put("totalSteps", 4)
                        .put("executedSteps", 3)
                        .put("failedSteps", 1)
                        .set("groups", objectMapper.createArrayNode()
                                .add(objectMapper.createObjectNode().put("group", "intake").put("totalSteps", 2))
                                .add(objectMapper.createObjectNode().put("group", "decision").put("totalSteps", 2)))));
        workflowOutput.set("workflowBranchState", objectMapper.createObjectNode()
                .set("triage", objectMapper.createObjectNode()
                        .put("status", "DEGRADED")
                        .put("hasFailure", true)));
        workflowOutput.set("workflowGroupState", objectMapper.createObjectNode()
                .set("intake", objectMapper.createObjectNode()
                        .put("status", "FAILED")
                        .put("hasFailure", true)));
        workflowOutput.set("steps", objectMapper.createArrayNode()
                .add(objectMapper.createObjectNode().put("id", "collect").put("status", "SUCCESS").put("renderedInstruction", "A".repeat(1200)))
                .add(objectMapper.createObjectNode().put("id", "inspect").put("status", "FAILED").put("renderedInstruction", "B".repeat(1200))));

        CapabilityResult result = CapabilityResult.success(
                "workflow executed",
                workflowOutput
        );
        CapabilityDescriptor descriptor = descriptor("skill:issue-triage", "Issue Triage", CapabilityType.SKILL);
        CapabilityResultCompression baseCompression = baseService.compress(heavySnapshot(), descriptor, result);
        CapabilityResultCompression finalCompression = skillService.compressIfNeeded(
                heavySnapshot(),
                descriptor,
                result,
                baseCompression
        );

        assertTrue(finalCompression.compressed());
        assertTrue(finalCompression.resultForModel().has("workflowStats"));
        assertTrue(finalCompression.resultForModel().has("workflowBranchSummary"));
        assertTrue(finalCompression.resultForModel().has("workflowBranches"));
        assertTrue(finalCompression.resultForModel().has("workflowBranchState"));
        assertTrue(finalCompression.resultForModel().has("workflowGroupState"));
        assertTrue(finalCompression.resultForModel().path("summary").asText().contains("分支数"));
    }

    private CapabilityDescriptor descriptor(String id, String name, CapabilityType type) {
        return new CapabilityDescriptor(
                id,
                name,
                type,
                new CapabilityMetadata("demo-provider", "1.0.0", "demo", RiskLevel.LOW, List.of("demo")),
                JsonNodeFactory.instance.objectNode(),
                JsonNodeFactory.instance.objectNode()
        );
    }

    private CompressedContextSnapshot emptySnapshot() {
        return new CompressedContextSnapshot(
                new ContextBudgetReport(16_000, 200, 120, 0.0125D, 0.6D, false),
                List.of(),
                List.of(),
                List.of("skip compression"),
                "最近关键消息：\n用户：请继续"
        );
    }

    private CompressedContextSnapshot heavySnapshot() {
        String heavyContent = "历史上下文".repeat(5_000);
        ContextSegment segment = new ContextSegment(
                "history-1",
                "conversation_history_summary",
                "conversation",
                heavyContent,
                new ContextBudgetService().estimateTokens(heavyContent),
                2,
                true
        );
        return new CompressedContextSnapshot(
                new ContextBudgetReport(16_000, 8_000, 6_000, 0.5D, 0.75D, false),
                List.of(segment),
                List.of(segment),
                List.of("compressed older history"),
                "较早历史摘要：..."
        );
    }

    private com.fasterxml.jackson.databind.node.ObjectNode resourceOutput() {
        return objectMapper.createObjectNode()
                .set("contents", objectMapper.createArrayNode()
                        .add(objectMapper.createObjectNode()
                                .put("uri", "file://docs/very-long.md")
                                .put("mimeType", "text/markdown")
                                .put("text", "A".repeat(3_500)))
                        .add(objectMapper.createObjectNode()
                                .put("uri", "file://docs/appendix.md")
                                .put("mimeType", "text/plain")
                                .put("text", "B".repeat(2_000))));
    }

    private MessageService messageService() {
        StaticMessageSource source = new StaticMessageSource();
        source.addMessage("context.compression.capabilityKept", Locale.ENGLISH, "kept full capability result");
        source.addMessage("context.compression.capabilityKept", Locale.SIMPLIFIED_CHINESE, "kept full capability result");
        source.addMessage("context.compression.capabilityCompressed", Locale.ENGLISH, "compressed capability result");
        source.addMessage("context.compression.capabilityCompressed", Locale.SIMPLIFIED_CHINESE, "compressed capability result");
        source.addMessage("context.compression.resourceContentCompressed", Locale.ENGLISH, "compressed resource content");
        source.addMessage("context.compression.resourceContentCompressed", Locale.SIMPLIFIED_CHINESE, "compressed resource content");
        source.addMessage("context.compression.skillResultCompressed", Locale.ENGLISH, "compressed skill result");
        source.addMessage("context.compression.skillResultCompressed", Locale.SIMPLIFIED_CHINESE, "compressed skill result");
        return new MessageService(source);
    }
}
