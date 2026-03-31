package com.example.agentruntime.skill;

import com.example.agentruntime.capability.RiskLevel;

import java.util.List;

public record SkillManifest(
        String id,
        String name,
        String version,
        String description,
        String type,
        String entry,
        String prompt,
        RiskLevel riskLevel,
        List<String> tags,
        List<SkillWorkflowStep> steps
) {

    public SkillManifest {
        type = type == null || type.isBlank() ? "prompt" : type;
        entry = entry == null || entry.isBlank() ? null : entry;
        riskLevel = riskLevel == null ? RiskLevel.LOW : riskLevel;
        tags = tags == null ? List.of() : List.copyOf(tags);
        steps = steps == null ? List.of() : List.copyOf(steps);
    }
}
