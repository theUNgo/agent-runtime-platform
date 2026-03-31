package com.example.agentruntime.capability;

import java.util.List;

public record CapabilityMetadata(
        String provider,
        String version,
        String description,
        RiskLevel riskLevel,
        List<String> tags
) {

    public CapabilityMetadata {
        riskLevel = riskLevel == null ? RiskLevel.LOW : riskLevel;
        tags = tags == null ? List.of() : List.copyOf(tags);
    }
}
