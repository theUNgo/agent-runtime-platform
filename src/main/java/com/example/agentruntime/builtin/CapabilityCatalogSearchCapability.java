package com.example.agentruntime.builtin;

import com.example.agentruntime.capability.AgentCapability;
import com.example.agentruntime.capability.CapabilityContext;
import com.example.agentruntime.capability.CapabilityDescriptor;
import com.example.agentruntime.capability.CapabilityMetadata;
import com.example.agentruntime.capability.CapabilityResult;
import com.example.agentruntime.capability.CapabilityType;
import com.example.agentruntime.capability.RiskLevel;
import com.example.agentruntime.document.CapabilityDocumentService;
import com.example.agentruntime.i18n.MessageService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.List;

/**
 * 能力目录搜索内置能力。
 * 供模型先读取能力概述列表，再按需调用文档读取能力获取详情。
 */
public class CapabilityCatalogSearchCapability implements AgentCapability {

    private final CapabilityDocumentService capabilityDocumentService;
    private final ObjectMapper objectMapper;
    private final MessageService messageService;

    public CapabilityCatalogSearchCapability(CapabilityDocumentService capabilityDocumentService,
                                             ObjectMapper objectMapper,
                                             MessageService messageService) {
        this.capabilityDocumentService = capabilityDocumentService;
        this.objectMapper = objectMapper;
        this.messageService = messageService;
    }

    @Override
    public CapabilityDescriptor descriptor() {
        return new CapabilityDescriptor(
                "builtin:capability.catalog.search",
                messageService.get("capability.builtin.catalogSearch.name"),
                CapabilityType.BUILTIN,
                new CapabilityMetadata(
                        "builtin",
                        "1",
                        messageService.get("capability.builtin.catalogSearch.description"),
                        RiskLevel.LOW,
                        List.of("catalog", "discovery", "search", "docs")),
                null,
                null
        );
    }

    @Override
    public CapabilityResult execute(CapabilityContext context, JsonNode input) {
        String query = input == null ? context.userMessage() : input.path("query").asText(context.userMessage());
        ArrayNode items = objectMapper.createArrayNode();
        capabilityDocumentService.search(query).forEach(item -> {
            ObjectNode node = items.addObject();
            node.put("docId", item.docId());
            node.put("capabilityId", item.capabilityId());
            node.put("name", item.name());
            node.put("capabilityType", item.capabilityType());
            node.put("sourceType", item.sourceType());
            node.put("summary", item.summary());
            ArrayNode tags = node.putArray("tags");
            item.tags().forEach(tags::add);
        });

        ObjectNode result = objectMapper.createObjectNode();
        result.put("query", query == null ? "" : query);
        result.put("count", items.size());
        result.set("items", items);
        return CapabilityResult.success(messageService.get("capability.builtin.catalogSearch.success"), result);
    }
}
