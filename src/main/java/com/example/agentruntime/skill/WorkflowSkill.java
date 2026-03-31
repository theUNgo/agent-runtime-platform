package com.example.agentruntime.skill;

import com.example.agentruntime.capability.CapabilityContext;
import com.example.agentruntime.capability.CapabilityDescriptor;
import com.example.agentruntime.capability.CapabilityMetadata;
import com.example.agentruntime.capability.CapabilityResult;
import com.example.agentruntime.capability.CapabilityType;
import com.example.agentruntime.i18n.MessageService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.List;

/**
 * 最小可运行的 Workflow Skill。
 * 当前版本不直接执行脚本或外部工具，而是把 workflow 步骤渲染成结构化中间结果，
 * 方便后续 planner、文档读取和上下文压缩逐步接入更完整的 Skill 运行时。
 */
public class WorkflowSkill implements Skill {

    private final SkillManifest manifest;
    private final ObjectMapper objectMapper;
    private final MessageService messageService;
    private final WorkflowSkillExecutionSupport executionSupport;

    public WorkflowSkill(SkillManifest manifest,
                         ObjectMapper objectMapper,
                         MessageService messageService,
                         WorkflowSkillExecutionSupport executionSupport) {
        this.manifest = manifest;
        this.objectMapper = objectMapper;
        this.messageService = messageService;
        this.executionSupport = executionSupport;
    }

    @Override
    public SkillManifest manifest() {
        return manifest;
    }

    @Override
    public CapabilityDescriptor descriptor() {
        return new CapabilityDescriptor(
                "skill:" + manifest.id(),
                manifest.name(),
                CapabilityType.SKILL,
                new CapabilityMetadata(
                        "local-skill",
                        manifest.version(),
                        manifest.description(),
                        manifest.riskLevel(),
                        manifest.tags()),
                null,
                null
        );
    }

    @Override
    public CapabilityResult execute(CapabilityContext context, JsonNode input) {
        ObjectNode result = objectMapper.createObjectNode();
        result.put("skillId", manifest.id());
        result.put("skillType", manifest.type());
        result.put("workflowEntry", manifest.entry() == null ? "" : manifest.entry());
        result.put("userMessage", context.userMessage());
        result.set("input", input == null ? objectMapper.createObjectNode() : input);

        ArrayNode steps = result.putArray("steps");
        List<String> renderedSummaries = new ArrayList<>();
        for (SkillWorkflowStep step : manifest.steps()) {
            String renderedInstruction = renderInstruction(step.instruction(), context, input);
            ObjectNode stepNode = steps.addObject();
            stepNode.put("id", step.id());
            stepNode.put("title", step.title());
            stepNode.put("instruction", step.instruction());
            stepNode.put("renderedInstruction", renderedInstruction);
            stepNode.put("outputKey", step.outputKey());
            renderedSummaries.add(step.title() + "：" + renderedInstruction);
        }

        result.put("workflowSummary", String.join("\n", renderedSummaries));
        return CapabilityResult.success(messageService.get("capability.skill.workflow.success"), result);
    }

    /**
     * 对 workflow 指令做最小变量渲染。
     * 当前支持用户消息、工作区和 input 顶层字段，足够覆盖第一版 workflow 说明和中间结果产出。
     */
    private String renderInstruction(String template, CapabilityContext context, JsonNode input) {
        String rendered = template == null ? "" : template;
        rendered = rendered.replace("{{userMessage}}", safe(context.userMessage()));
        rendered = rendered.replace("{{workspaceRoot}}", safe(context.workspaceRoot()));
        rendered = rendered.replace("{{input}}", input == null || input.isNull() ? "{}" : input.toString());

        if (input != null && input.isObject()) {
            java.util.Iterator<String> iterator = input.fieldNames();
            while (iterator.hasNext()) {
                String fieldName = iterator.next();
                String placeholder = "{{input." + fieldName + "}}";
                rendered = rendered.replace(placeholder, safe(input.path(fieldName).asText(input.path(fieldName).toString())));
            }
        }
        return rendered;
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
