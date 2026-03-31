package com.example.agentruntime.document;

import com.example.agentruntime.capability.CapabilityDescriptor;
import com.example.agentruntime.capability.CapabilityMetadata;
import com.example.agentruntime.capability.CapabilityResult;
import com.example.agentruntime.capability.CapabilityType;
import com.example.agentruntime.capability.RiskLevel;
import com.example.agentruntime.i18n.MessageService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.StaticMessageSource;

import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CapabilityDocumentPlanningServiceTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Test
    void shouldRecognizeDocumentLookupIntent() {
        CapabilityDocumentPlanningService service = new CapabilityDocumentPlanningService(OBJECT_MAPPER, messageService());

        assertTrue(service.isDocumentLookupIntent("请告诉我 code review skill 的详情说明"));
        assertEquals("detail", service.desiredDocType("请告诉我 code review skill 的详情说明"));
    }

    @Test
    void shouldChooseBestDocIdFromSearchResults() {
        CapabilityDocumentPlanningService service = new CapabilityDocumentPlanningService(OBJECT_MAPPER, messageService());
        ObjectNode output = OBJECT_MAPPER.createObjectNode();
        ArrayNode items = output.putArray("items");
        items.addObject()
                .put("docId", "catalog-skill:research-brief")
                .put("capabilityId", "catalog-skill:research-brief")
                .put("name", "Research Brief")
                .put("summary", "用于生成调研简报");
        items.addObject()
                .put("docId", "skill:code-review")
                .put("capabilityId", "skill:code-review")
                .put("name", "Code Review")
                .put("summary", "用于分析代码变更并整理评审意见");

        assertEquals("skill:code-review", service.chooseDocId(output, "请读取 code review skill 的详情文档"));
    }

    @Test
    void shouldRenderCapabilityOnlyDocumentResponse() {
        CapabilityDocumentPlanningService service = new CapabilityDocumentPlanningService(OBJECT_MAPPER, messageService());
        CapabilityDescriptor descriptor = new CapabilityDescriptor(
                "builtin:capability.doc.read",
                "doc",
                CapabilityType.BUILTIN,
                new CapabilityMetadata("builtin", "1", "doc", RiskLevel.LOW, List.of()),
                null,
                null
        );
        ObjectNode output = OBJECT_MAPPER.createObjectNode();
        output.put("content", "# Detail\n\n这里是文档内容。");

        String rendered = service.renderCapabilityOnlyResponse(descriptor, CapabilityResult.success("ok", output));

        assertTrue(rendered.contains("文档内容"));
    }

    private MessageService messageService() {
        StaticMessageSource source = new StaticMessageSource();
        addMessage(source, "capability.doc.render.noResults", "no results");
        addMessage(source, "capability.doc.render.searchHeader", "search header");
        addMessage(source, "capability.doc.render.detailHeader", "detail header");
        return new MessageService(source);
    }

    private void addMessage(StaticMessageSource source, String code, String message) {
        source.addMessage(code, Locale.ENGLISH, message);
        source.addMessage(code, Locale.SIMPLIFIED_CHINESE, message);
    }
}
