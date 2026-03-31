package com.example.agentruntime.skill;

import com.example.agentruntime.capability.CapabilityContext;
import com.example.agentruntime.capability.CapabilityDescriptor;
import com.example.agentruntime.capability.CapabilityMetadata;
import com.example.agentruntime.capability.CapabilityResult;
import com.example.agentruntime.capability.CapabilityType;
import com.example.agentruntime.i18n.MessageService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * 最简单的 Prompt 类型 Skill。
 * 当前只做模板式输出，后续可以扩展为执行脚本或工作流。
 */
public class PromptSkill implements Skill {

    private final SkillManifest manifest;
    private final ObjectMapper objectMapper;
    private final MessageService messageService;

    public PromptSkill(SkillManifest manifest, ObjectMapper objectMapper, MessageService messageService) {
        this.manifest = manifest;
        this.objectMapper = objectMapper;
        this.messageService = messageService;
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
        result.put("prompt", manifest.prompt() == null ? "" : manifest.prompt());
        result.put("userMessage", context.userMessage());
        result.set("input", input == null ? objectMapper.createObjectNode() : input);
        return CapabilityResult.success(messageService.get("capability.skill.prompt.success"), result);
    }
}
