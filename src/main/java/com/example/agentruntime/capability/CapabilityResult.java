package com.example.agentruntime.capability;

import com.fasterxml.jackson.databind.JsonNode;

public record CapabilityResult(
        boolean success,
        String message,
        JsonNode output
) {

    public static CapabilityResult success(String message, JsonNode output) {
        return new CapabilityResult(true, message, output);
    }

    public static CapabilityResult failure(String message) {
        return new CapabilityResult(false, message, null);
    }
}
