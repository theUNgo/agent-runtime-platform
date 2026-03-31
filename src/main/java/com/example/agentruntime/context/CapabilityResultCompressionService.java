package com.example.agentruntime.context;

import com.example.agentruntime.capability.CapabilityDescriptor;
import com.example.agentruntime.capability.CapabilityResult;
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
 * 能力结果压缩服务。
 * 这一层专门处理 MCP / Skill 的长结果正文，避免模型在工具链很长时
 * 反复读取完整 JSON 或大段文本输出。
 */
@Service
public class CapabilityResultCompressionService {

    private static final int INLINE_RESULT_TOKEN_LIMIT = 1_200;
    private static final int PREVIEW_CHAR_LIMIT = 400;
    private static final int MESSAGE_PREVIEW_CHAR_LIMIT = 160;

    private final ContextBudgetService contextBudgetService;
    private final MessageService messageService;
    private final ObjectMapper objectMapper;

    public CapabilityResultCompressionService(ContextBudgetService contextBudgetService,
                                              MessageService messageService,
                                              ObjectMapper objectMapper) {
        this.contextBudgetService = contextBudgetService;
        this.messageService = messageService;
        this.objectMapper = objectMapper;
    }

    /**
     * 根据当前会话背景预算决定是否压缩能力结果。
     * 如果结果正文较大，或与当前背景合并后触发预算阈值，就改为返回结构化摘要。
     */
    public CapabilityResultCompression compress(CompressedContextSnapshot contextSnapshot,
                                                CapabilityDescriptor descriptor,
                                                CapabilityResult result) {
        JsonNode rawResultNode = objectMapper.valueToTree(result);
        String rawResultText = buildRawResultText(descriptor, result);
        int rawTokens = contextBudgetService.estimateTokens(rawResultText);
        ContextBudgetReport combinedBudget = evaluateCombinedBudget(contextSnapshot, descriptor, rawResultText);
        boolean shouldCompress = rawTokens >= INLINE_RESULT_TOKEN_LIMIT || combinedBudget.compressionRequired();

        if (!shouldCompress) {
            return new CapabilityResultCompression(
                    rawResultNode,
                    contextSnapshot.backgroundSummary(),
                    List.of(messageService.get("context.compression.capabilityKept")),
                    rawTokens,
                    rawTokens,
                    false
            );
        }

        String summary = summarizeResult(descriptor, result);
        ObjectNode compressedNode = objectMapper.createObjectNode();
        compressedNode.put("success", result.success());
        compressedNode.put("message", normalize(result.message()));
        compressedNode.put("summary", summary);
        compressedNode.put("provider", descriptor.metadata().provider());
        compressedNode.put("capabilityId", descriptor.id());
        compressedNode.put("capabilityName", descriptor.name());
        appendOutputMetadata(compressedNode, result.output());

        int finalTokens = contextBudgetService.estimateTokens(compressedNode.toString());
        return new CapabilityResultCompression(
                compressedNode,
                appendBackgroundSummary(contextSnapshot.backgroundSummary(), summary),
                List.of(messageService.get("context.compression.capabilityCompressed")),
                rawTokens,
                finalTokens,
                true
        );
    }

    /**
     * 评估“会话背景 + 当前能力结果”合并后的预算，判断这次是否需要触发结果压缩。
     */
    private ContextBudgetReport evaluateCombinedBudget(CompressedContextSnapshot contextSnapshot,
                                                       CapabilityDescriptor descriptor,
                                                       String rawResultText) {
        List<ContextSegment> combinedSegments = new ArrayList<>(contextSnapshot.finalSegments());
        combinedSegments.add(new ContextSegment(
                "capability-result-" + descriptor.id(),
                "capability_result",
                descriptor.id(),
                rawResultText,
                contextBudgetService.estimateTokens(rawResultText),
                2,
                true
        ));
        return contextBudgetService.evaluate(combinedSegments);
    }

    /**
     * 构建能力结果原文，用于统一估算预算。
     */
    private String buildRawResultText(CapabilityDescriptor descriptor, CapabilityResult result) {
        StringBuilder builder = new StringBuilder();
        builder.append("能力：").append(descriptor.name()).append('\n');
        builder.append("能力类型：").append(descriptor.type().name()).append('\n');
        builder.append("提供方：").append(descriptor.metadata().provider()).append('\n');
        builder.append("执行状态：").append(result.success() ? "成功" : "失败").append('\n');
        if (result.message() != null && !result.message().isBlank()) {
            builder.append("结果消息：").append(normalize(result.message())).append('\n');
        }
        if (result.output() != null && !result.output().isNull()) {
            builder.append("结果正文：\n").append(result.output().toPrettyString());
        }
        return builder.toString().trim();
    }

    /**
     * 生成给模型阅读的短摘要，只保留高价值信息：
     * 能力身份、执行状态、顶层结构和少量输出预览。
     */
    private String summarizeResult(CapabilityDescriptor descriptor, CapabilityResult result) {
        List<String> lines = new ArrayList<>();
        lines.add("最近能力执行摘要：");
        lines.add("- 能力：" + descriptor.name());
        lines.add("- 类型：" + descriptor.type().name());
        lines.add("- 提供方：" + descriptor.metadata().provider());
        lines.add("- 状态：" + (result.success() ? "成功" : "失败"));
        if (result.message() != null && !result.message().isBlank()) {
            lines.add("- 结果消息：" + compact(result.message(), MESSAGE_PREVIEW_CHAR_LIMIT));
        }
        if (result.output() != null && !result.output().isNull()) {
            lines.add("- 输出结构：" + describeNode(result.output()));
            lines.add("- 输出预览：" + compact(result.output().toString(), PREVIEW_CHAR_LIMIT));
        }
        return String.join("\n", lines);
    }

    private void appendOutputMetadata(ObjectNode target, JsonNode output) {
        if (output == null || output.isNull()) {
            target.put("outputType", "null");
            return;
        }

        target.put("outputType", describeNode(output));
        target.put("preview", compact(output.toString(), PREVIEW_CHAR_LIMIT));

        if (output.isObject()) {
            ArrayNode fieldNames = target.putArray("topLevelFields");
            Iterator<String> iterator = output.fieldNames();
            int count = 0;
            while (iterator.hasNext() && count < 12) {
                fieldNames.add(iterator.next());
                count++;
            }
            target.put("fieldCount", output.size());
        } else if (output.isArray()) {
            target.put("itemCount", output.size());
        }
    }

    private String appendBackgroundSummary(String baseSummary, String summary) {
        if (baseSummary == null || baseSummary.isBlank()) {
            return summary;
        }
        return baseSummary + "\n\n" + summary;
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

    private String compact(String content, int limit) {
        String normalized = normalize(content);
        return normalized.length() <= limit ? normalized : normalized.substring(0, limit) + "...";
    }

    private String normalize(String content) {
        if (content == null || content.isBlank()) {
            return "-";
        }
        return content.replaceAll("\\s+", " ").trim();
    }
}
