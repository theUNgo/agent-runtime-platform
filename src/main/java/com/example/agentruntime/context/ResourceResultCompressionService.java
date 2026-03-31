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
import java.util.List;

/**
 * 资源结果增强压缩服务。
 * 这层专门处理 MCP resources/read 这类标准资源响应，把 contents 数组拆成更细的资源段摘要，
 * 避免模型在阅读长文档资源时一次吞入完整正文。
 */
@Service
public class ResourceResultCompressionService {

    private static final int RESOURCE_TEXT_COMPRESS_THRESHOLD = 1_500;
    private static final int RESOURCE_PREVIEW_CHAR_LIMIT = 220;

    private final ContextBudgetService contextBudgetService;
    private final MessageService messageService;
    private final ObjectMapper objectMapper;

    public ResourceResultCompressionService(ContextBudgetService contextBudgetService,
                                            MessageService messageService,
                                            ObjectMapper objectMapper) {
        this.contextBudgetService = contextBudgetService;
        this.messageService = messageService;
        this.objectMapper = objectMapper;
    }

    /**
     * 如果结果是标准资源响应，并且资源正文较长，则生成资源段级别的结构化摘要；
     * 否则直接沿用上一层能力结果压缩结果。
     */
    public CapabilityResultCompression compressIfNeeded(CompressedContextSnapshot contextSnapshot,
                                                        CapabilityDescriptor descriptor,
                                                        CapabilityResult result,
                                                        CapabilityResultCompression baseCompression) {
        JsonNode output = result.output();
        if (!isResourceReadOutput(output)) {
            return baseCompression;
        }

        int maxTextLength = maxTextLength(output);
        int resourceTokens = contextBudgetService.estimateTokens(output.toString());
        if (maxTextLength < RESOURCE_TEXT_COMPRESS_THRESHOLD && !baseCompression.compressed()) {
            return baseCompression;
        }

        ObjectNode compressedNode = objectMapper.createObjectNode();
        compressedNode.put("success", result.success());
        compressedNode.put("message", result.message() == null ? "" : result.message());
        compressedNode.put("summary", buildResourceSummary(descriptor, output));
        compressedNode.put("capabilityId", descriptor.id());
        compressedNode.put("capabilityName", descriptor.name());
        compressedNode.put("provider", descriptor.metadata().provider());

        ArrayNode resourceSummaries = compressedNode.putArray("resourceContentsSummary");
        for (JsonNode item : output.path("contents")) {
            ObjectNode summary = resourceSummaries.addObject();
            summary.put("uri", item.path("uri").asText(""));
            summary.put("mimeType", item.path("mimeType").asText(""));
            String text = item.path("text").asText("");
            summary.put("textLength", text.length());
            summary.put("preview", compact(text, RESOURCE_PREVIEW_CHAR_LIMIT));
        }
        compressedNode.put("resourceCount", output.path("contents").size());

        String summaryText = compressedNode.path("summary").asText("");
        String backgroundSummary = appendBackgroundSummary(baseCompression.backgroundSummary(), summaryText);
        int finalTokens = contextBudgetService.estimateTokens(compressedNode.toString());

        List<String> actions = new ArrayList<>(baseCompression.compressionActions());
        actions.add(messageService.get("context.compression.resourceContentCompressed"));
        return new CapabilityResultCompression(
                compressedNode,
                backgroundSummary,
                actions,
                Math.max(baseCompression.rawTokens(), resourceTokens),
                finalTokens,
                true
        );
    }

    /**
     * 判断是否为标准 MCP resources/read 返回值。
     */
    private boolean isResourceReadOutput(JsonNode output) {
        return output != null
                && output.isObject()
                && output.has("contents")
                && output.get("contents").isArray();
    }

    /**
     * 计算资源响应中最长的一段正文，作为是否需要细粒度压缩的依据。
     */
    private int maxTextLength(JsonNode output) {
        int maxLength = 0;
        for (JsonNode item : output.path("contents")) {
            int textLength = item.path("text").asText("").length();
            if (textLength > maxLength) {
                maxLength = textLength;
            }
        }
        return maxLength;
    }

    /**
     * 生成资源响应摘要。
     * 这里保留 URI、资源段数、MIME 和少量预览，方便模型继续理解上下文。
     */
    private String buildResourceSummary(CapabilityDescriptor descriptor, JsonNode output) {
        List<String> lines = new ArrayList<>();
        lines.add("最近资源读取摘要：");
        lines.add("- 能力：" + descriptor.name());
        lines.add("- 提供方：" + descriptor.metadata().provider());
        lines.add("- 资源段数：" + output.path("contents").size());

        int index = 1;
        for (JsonNode item : output.path("contents")) {
            String text = item.path("text").asText("");
            lines.add("- 资源段 " + index
                    + "：uri=" + item.path("uri").asText("")
                    + "；mimeType=" + item.path("mimeType").asText("")
                    + "；textLength=" + text.length()
                    + "；preview=" + compact(text, RESOURCE_PREVIEW_CHAR_LIMIT));
            index++;
        }
        return String.join("\n", lines);
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
