package com.example.agentruntime.document;

import com.example.agentruntime.capability.CapabilityDescriptor;
import com.example.agentruntime.capability.CapabilityResult;
import com.example.agentruntime.i18n.MessageService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 能力文档规划服务。
 * 负责识别“先搜摘要、再读详情”的文档型请求，并给编排器提供最小多步规划辅助。
 */
@Service
public class CapabilityDocumentPlanningService {

    private final ObjectMapper objectMapper;
    private final MessageService messageService;

    public CapabilityDocumentPlanningService(ObjectMapper objectMapper,
                                             MessageService messageService) {
        this.objectMapper = objectMapper;
        this.messageService = messageService;
    }

    /**
     * 判断当前消息是否更像“查能力说明文档”，而不是直接执行某个 Skill/MCP。
     */
    public boolean isDocumentLookupIntent(String message) {
        String normalized = normalize(message);
        boolean mentionsCapability = normalized.contains("skill")
                || normalized.contains("mcp")
                || normalized.contains("能力")
                || normalized.contains("工具");
        boolean mentionsDocs = normalized.contains("说明")
                || normalized.contains("文档")
                || normalized.contains("详情")
                || normalized.contains("detail")
                || normalized.contains("概述")
                || normalized.contains("怎么用")
                || normalized.contains("使用方式");
        return mentionsCapability && mentionsDocs;
    }

    public String desiredDocType(String message) {
        String normalized = normalize(message);
        if (normalized.contains("详情") || normalized.contains("detail") || normalized.contains("怎么用") || normalized.contains("使用方式")) {
            return "detail";
        }
        return "summary";
    }

    /**
     * 构造目录搜索输入。
     * 这里直接把用户原始问题作为 query，后续如果 planner 更复杂，再拆成关键词提取。
     */
    public JsonNode buildSearchInput(String message) {
        ObjectNode input = objectMapper.createObjectNode();
        input.put("query", message == null ? "" : message.trim());
        return input;
    }

    /**
     * 根据目录搜索结果选择最可能的能力文档。
     * 第一版用轻量规则打分：优先匹配名称和标识，再考虑摘要命中。
     */
    public String chooseDocId(JsonNode searchOutput, String message) {
        if (searchOutput == null || !searchOutput.path("items").isArray() || searchOutput.path("items").isEmpty()) {
            return null;
        }

        String normalized = normalize(message);
        String bestDocId = null;
        int bestScore = Integer.MIN_VALUE;
        for (JsonNode item : searchOutput.path("items")) {
            String docId = item.path("docId").asText("");
            String name = normalize(item.path("name").asText(""));
            String capabilityId = normalize(item.path("capabilityId").asText(""));
            String summary = normalize(item.path("summary").asText(""));

            int score = 0;
            if (!name.isBlank() && normalized.contains(name)) {
                score += 6;
            }
            if (!capabilityId.isBlank() && normalized.contains(capabilityId)) {
                score += 5;
            }
            for (String token : tokenize(normalized)) {
                if (!token.isBlank() && name.contains(token)) {
                    score += 2;
                }
                if (!token.isBlank() && capabilityId.contains(token)) {
                    score += 2;
                }
                if (!token.isBlank() && summary.contains(token)) {
                    score += 1;
                }
            }

            if (score > bestScore) {
                bestScore = score;
                bestDocId = docId;
            }
        }

        if (bestScore <= 0 && searchOutput.path("items").size() > 1) {
            return null;
        }
        return bestDocId == null || bestDocId.isBlank() ? null : bestDocId;
    }

    /**
     * 在没有模型整理时，为内置文档能力生成可直接展示给用户的文本。
     */
    public String renderCapabilityOnlyResponse(CapabilityDescriptor descriptor, CapabilityResult result) {
        if (descriptor == null || result == null || result.output() == null) {
            return result == null ? "" : result.message();
        }

        if ("builtin:capability.catalog.search".equals(descriptor.id())) {
            return renderSearchResult(result.output());
        }
        if ("builtin:capability.doc.read".equals(descriptor.id())) {
            return renderDocumentResult(result.output());
        }
        return result.message();
    }

    private String renderSearchResult(JsonNode output) {
        if (!output.path("items").isArray() || output.path("items").isEmpty()) {
            return messageService.get("capability.doc.render.noResults");
        }

        StringBuilder builder = new StringBuilder(messageService.get("capability.doc.render.searchHeader")).append('\n');
        for (JsonNode item : output.path("items")) {
            builder.append("- ")
                    .append(item.path("name").asText(""))
                    .append(" [")
                    .append(item.path("docId").asText(""))
                    .append("]")
                    .append("：")
                    .append(item.path("summary").asText(""))
                    .append('\n');
        }
        return builder.toString().trim();
    }

    private String renderDocumentResult(JsonNode output) {
        String content = output.path("content").asText("");
        if (content.isBlank()) {
            return messageService.get("capability.doc.render.noResults");
        }
        return messageService.get("capability.doc.render.detailHeader") + "\n" + content;
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private List<String> tokenize(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        String[] parts = value.split("[\\s,，。:：/\\\\()\\[\\]{}]+");
        List<String> tokens = new ArrayList<>();
        for (String part : parts) {
            if (!part.isBlank()) {
                tokens.add(part);
            }
        }
        return tokens;
    }
}
