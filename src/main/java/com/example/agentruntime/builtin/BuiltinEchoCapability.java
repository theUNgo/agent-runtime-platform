package com.example.agentruntime.builtin;

import com.example.agentruntime.capability.AgentCapability;
import com.example.agentruntime.capability.CapabilityContext;
import com.example.agentruntime.capability.CapabilityDescriptor;
import com.example.agentruntime.capability.CapabilityMetadata;
import com.example.agentruntime.capability.CapabilityResult;
import com.example.agentruntime.capability.CapabilityType;
import com.example.agentruntime.capability.RiskLevel;
import com.example.agentruntime.i18n.MessageService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.List;

/**
 * 内置回声能力。
 * 在系统没有找到更强的工具或技能时，用于提供一个最基础的可执行能力。
 */
public class BuiltinEchoCapability implements AgentCapability {

    private final ObjectMapper objectMapper;
    private final MessageService messageService;

    public BuiltinEchoCapability(ObjectMapper objectMapper, MessageService messageService) {
        this.objectMapper = objectMapper;
        this.messageService = messageService;
    }

    @Override
    public CapabilityDescriptor descriptor() {
        return new CapabilityDescriptor(
                "builtin:echo",
                messageService.get("capability.builtin.echo.name"),
                CapabilityType.BUILTIN,
                new CapabilityMetadata(
                        "builtin",
                        "1",
                        messageService.get("capability.builtin.echo.description"),
                        RiskLevel.LOW,
                        List.of("fallback", "default")),
                null,
                null
        );
    }

    @Override
    public CapabilityResult execute(CapabilityContext context, JsonNode input) {
        ObjectNode result = objectMapper.createObjectNode();
        result.put("message", context.userMessage());
        result.set("input", input == null ? objectMapper.createObjectNode() : input);
        return CapabilityResult.success(messageService.get("capability.builtin.echo.success"), result);
    }
}
