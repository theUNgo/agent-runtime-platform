package com.example.agentruntime.builtin;

import com.example.agentruntime.capability.AgentCapability;
import com.example.agentruntime.capability.CapabilityContext;
import com.example.agentruntime.capability.CapabilityDescriptor;
import com.example.agentruntime.capability.CapabilityMetadata;
import com.example.agentruntime.capability.CapabilityResult;
import com.example.agentruntime.capability.CapabilityType;
import com.example.agentruntime.capability.RiskLevel;
import com.example.agentruntime.document.CapabilityDocumentService;
import com.example.agentruntime.document.CapabilityDocumentView;
import com.example.agentruntime.i18n.MessageService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.List;

/**
 * 能力说明文档读取内置能力。
 * 允许模型按 docId + docType 动态读取指定能力的摘要或详情文档。
 */
public class CapabilityDocumentReadCapability implements AgentCapability {

    private final CapabilityDocumentService capabilityDocumentService;
    private final ObjectMapper objectMapper;
    private final MessageService messageService;

    public CapabilityDocumentReadCapability(CapabilityDocumentService capabilityDocumentService,
                                            ObjectMapper objectMapper,
                                            MessageService messageService) {
        this.capabilityDocumentService = capabilityDocumentService;
        this.objectMapper = objectMapper;
        this.messageService = messageService;
    }

    @Override
    public CapabilityDescriptor descriptor() {
        return new CapabilityDescriptor(
                "builtin:capability.doc.read",
                messageService.get("capability.builtin.docRead.name"),
                CapabilityType.BUILTIN,
                new CapabilityMetadata(
                        "builtin",
                        "1",
                        messageService.get("capability.builtin.docRead.description"),
                        RiskLevel.LOW,
                        List.of("catalog", "docs", "detail", "dynamic-load")),
                null,
                null
        );
    }

    @Override
    public CapabilityResult execute(CapabilityContext context, JsonNode input) {
        String docId = input == null ? "" : input.path("docId").asText("");
        String docType = input == null ? "summary" : input.path("docType").asText("summary");
        CapabilityDocumentView document = capabilityDocumentService.read(docId, docType);

        ObjectNode result = objectMapper.createObjectNode();
        result.put("docId", document.docId());
        result.put("capabilityId", document.capabilityId());
        result.put("name", document.name());
        result.put("capabilityType", document.capabilityType());
        result.put("docType", document.docType());
        result.put("content", document.content());
        ArrayNode tags = result.putArray("tags");
        document.tags().forEach(tags::add);
        return CapabilityResult.success(messageService.get("capability.builtin.docRead.success"), result);
    }
}
