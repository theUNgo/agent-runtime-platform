package com.example.agentruntime.agent;

import com.example.agentruntime.capability.CapabilityDescriptor;
import com.example.agentruntime.capability.CapabilityResult;

public record ExecutionStep(
        CapabilityDescriptor capability,
        CapabilityResult result
) {
}
