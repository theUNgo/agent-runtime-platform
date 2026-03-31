package com.example.agentruntime.capability;

import com.fasterxml.jackson.databind.JsonNode;

public record CapabilityDescriptor(
        String id,
        String name,
        CapabilityType type,
        CapabilityMetadata metadata,
        JsonNode inputSchema,
        JsonNode outputSchema
) {
}
