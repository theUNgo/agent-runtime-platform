package com.example.agentruntime.context;

import com.example.agentruntime.capability.CapabilityDescriptor;
import com.example.agentruntime.capability.CapabilityResult;
import com.example.agentruntime.capability.CapabilityType;
import com.example.agentruntime.i18n.MessageService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Skill 结果压缩服务。
 * 这一层专门处理 Skill 返回的 prompt、input 和中间结果字段，
 * 避免模型在长 prompt skill 或后续 workflow skill 场景里重复读取大段模板正文。
 */
@Service
public class SkillResultCompressionService {

    private static final int SKILL_PROMPT_COMPRESS_THRESHOLD = 800;
    private static final int PROMPT_PREVIEW_CHAR_LIMIT = 260;
    private static final int INPUT_PREVIEW_CHAR_LIMIT = 220;

    private final ContextBudgetService contextBudgetService;
    private final MessageService messageService;
    private final ObjectMapper objectMapper;

    public SkillResultCompressionService(ContextBudgetService contextBudgetService,
                                         MessageService messageService,
                                         ObjectMapper objectMapper) {
        this.contextBudgetService = contextBudgetService;
        this.messageService = messageService;
        this.objectMapper = objectMapper;
    }

    /**
     * 如果结果识别为 Skill 输出，并且 prompt 或输入上下文较大，就生成更轻量的 Skill 摘要。
     */
    public CapabilityResultCompression compressIfNeeded(CompressedContextSnapshot contextSnapshot,
                                                        CapabilityDescriptor descriptor,
                                                        CapabilityResult result,
                                                        CapabilityResultCompression baseCompression) {
        JsonNode output = result.output();
        if (!isSkillOutput(descriptor, output)) {
            return baseCompression;
        }

        String prompt = output.path("prompt").asText("");
        int promptTokens = contextBudgetService.estimateTokens(prompt);
        int inputTokens = contextBudgetService.estimateTokens(output.path("input").toString());
        if (promptTokens < SKILL_PROMPT_COMPRESS_THRESHOLD && inputTokens < SKILL_PROMPT_COMPRESS_THRESHOLD && !baseCompression.compressed()) {
            return baseCompression;
        }

        ObjectNode compressedNode = objectMapper.createObjectNode();
        compressedNode.put("success", result.success());
        compressedNode.put("message", result.message() == null ? "" : result.message());
        compressedNode.put("summary", buildSkillSummary(descriptor, output));
        compressedNode.put("capabilityId", descriptor.id());
        compressedNode.put("capabilityName", descriptor.name());
        compressedNode.put("provider", descriptor.metadata().provider());
        compressedNode.put("skillId", output.path("skillId").asText(""));
        compressedNode.put("skillType", output.path("skillType").asText(""));
        compressedNode.put("promptPreview", compact(prompt, PROMPT_PREVIEW_CHAR_LIMIT));
        if ("workflow".equalsIgnoreCase(output.path("skillType").asText(""))) {
            appendWorkflowMetadata(compressedNode, output);
        }

        JsonNode input = output.path("input");
        if (!input.isMissingNode() && !input.isNull()) {
            compressedNode.put("inputPreview", compact(input.toString(), INPUT_PREVIEW_CHAR_LIMIT));
            if (input.isObject()) {
                ArrayNode inputKeys = compressedNode.putArray("inputKeys");
                Iterator<String> iterator = input.fieldNames();
                int count = 0;
                while (iterator.hasNext() && count < 12) {
                    inputKeys.add(iterator.next());
                    count++;
                }
            }
        }

        String summaryText = compressedNode.path("summary").asText("");
        List<String> actions = new ArrayList<>(baseCompression.compressionActions());
        actions.add(messageService.get("context.compression.skillResultCompressed"));
        return new CapabilityResultCompression(
                compressedNode,
                appendBackgroundSummary(baseCompression.backgroundSummary(), summaryText),
                actions,
                Math.max(baseCompression.rawTokens(), promptTokens + inputTokens),
                contextBudgetService.estimateTokens(compressedNode.toString()),
                true
        );
    }

    /**
     * 当前先按统一特征识别 Skill 输出：
     * 能力类型是 SKILL，且结果里出现 skillId / skillType / prompt 等字段。
     */
    private boolean isSkillOutput(CapabilityDescriptor descriptor, JsonNode output) {
        return descriptor.type() == CapabilityType.SKILL
                && output != null
                && output.isObject()
                && output.has("skillId")
                && output.has("skillType");
    }

    /**
     * 生成 Skill 执行摘要，帮助模型理解“这个 Skill 做了什么、输入关注点是什么”。
     */
    private String buildSkillSummary(CapabilityDescriptor descriptor, JsonNode output) {
        List<String> lines = new ArrayList<>();
        lines.add("最近 Skill 执行摘要：");
        lines.add("- Skill：" + descriptor.name());
        lines.add("- Skill ID：" + output.path("skillId").asText(""));
        lines.add("- Skill 类型：" + output.path("skillType").asText(""));
        lines.add("- 提供方：" + descriptor.metadata().provider());
        if ("workflow".equalsIgnoreCase(output.path("skillType").asText(""))) {
            appendWorkflowSummary(lines, output);
        } else {
            lines.add("- Prompt 预览：" + compact(output.path("prompt").asText(""), PROMPT_PREVIEW_CHAR_LIMIT));
        }
        if (output.hasNonNull("userMessage")) {
            lines.add("- 用户消息：" + compact(output.path("userMessage").asText(""), INPUT_PREVIEW_CHAR_LIMIT));
        }
        JsonNode input = output.path("input");
        if (!input.isMissingNode() && !input.isNull()) {
            lines.add("- 输入结构：" + describeNode(input));
            lines.add("- 输入预览：" + compact(input.toString(), INPUT_PREVIEW_CHAR_LIMIT));
        }
        return String.join("\n", lines);
    }

    /**
     * 将 workflow 的关键信息挂入压缩结果。
     * 这样模型优先读取结构化分支信息，而不是重新展开完整步骤数组。
     */
    private void appendWorkflowMetadata(ObjectNode compressedNode, JsonNode output) {
        JsonNode workflowStats = output.path("workflowStats");
        if (!workflowStats.isMissingNode() && !workflowStats.isNull()) {
            compressedNode.set("workflowStats", workflowStats.deepCopy());
        }
        if (output.has("workflowBranches")) {
            compressedNode.set("workflowBranches", output.path("workflowBranches").deepCopy());
        }
        if (output.has("workflowBranchState")) {
            compressedNode.set("workflowBranchState", output.path("workflowBranchState").deepCopy());
        }
        if (output.has("workflowGroupState")) {
            compressedNode.set("workflowGroupState", output.path("workflowGroupState").deepCopy());
        }
        if (output.has("workflowBranchSummary")) {
            compressedNode.put("workflowBranchSummary", compact(output.path("workflowBranchSummary").asText(""), PROMPT_PREVIEW_CHAR_LIMIT));
        }
        if (output.has("workflowSummary")) {
            compressedNode.put("workflowSummaryPreview", compact(output.path("workflowSummary").asText(""), PROMPT_PREVIEW_CHAR_LIMIT));
        }
        if (output.has("haltedAtStepId")) {
            compressedNode.put("haltedAtStepId", output.path("haltedAtStepId").asText(""));
        }
    }

    /**
     * 为 workflow skill 生成更适合分支场景的摘要。
     * 当前优先展示统计信息、分支数量和摘要预览，减少长步骤明细对上下文的占用。
     */
    private void appendWorkflowSummary(List<String> lines, JsonNode output) {
        JsonNode workflowStats = output.path("workflowStats");
        lines.add("- Workflow 摘要：" + compact(output.path("workflowBranchSummary").asText(""), PROMPT_PREVIEW_CHAR_LIMIT));
        if (!workflowStats.isMissingNode() && workflowStats.isObject()) {
            lines.add("- 总步骤数：" + workflowStats.path("totalSteps").asInt());
            lines.add("- 已执行步骤数：" + workflowStats.path("executedSteps").asInt());
            lines.add("- 已跳过步骤数：" + workflowStats.path("skippedSteps").asInt());
            lines.add("- 失败步骤数：" + workflowStats.path("failedSteps").asInt());
            lines.add("- 能力步骤数：" + workflowStats.path("capabilitySteps").asInt());
            lines.add("- 失败后继续步骤数：" + workflowStats.path("continuedFailureSteps").asInt());
            lines.add("- 分支数：" + workflowStats.path("branchCount").asInt());
            lines.add("- 分组数：" + workflowStats.path("groupCount").asInt());
        }
        if (output.hasNonNull("haltedAtStepId") && !output.path("haltedAtStepId").asText("").isBlank()) {
            lines.add("- 中断步骤：" + output.path("haltedAtStepId").asText(""));
        }
    }

    private String describeNode(JsonNode node) {
        if (node == null || node.isNull()) {
            return "null";
        }
        if (node.isObject()) {
            return "object(" + node.size() + " fields)";
        }
        if (node.isArray()) {
            return "array(" + node.size() + " items)";
        }
        if (node.isTextual()) {
            return "text";
        }
        if (node.isNumber()) {
            return "number";
        }
        if (node.isBoolean()) {
            return "boolean";
        }
        return node.getNodeType().name().toLowerCase();
    }

    private String appendBackgroundSummary(String baseSummary, String summary) {
        if (baseSummary == null || baseSummary.isBlank()) {
            return summary;
        }
        return baseSummary + "\n\n" + summary;
    }

    private String compact(String content, int limit) {
        if (content == null || content.isBlank()) {
            return "-";
        }
        String normalized = content.replaceAll("\\s+", " ").trim();
        return normalized.length() <= limit ? normalized : normalized.substring(0, limit) + "...";
    }
}
