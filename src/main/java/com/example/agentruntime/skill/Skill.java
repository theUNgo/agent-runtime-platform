package com.example.agentruntime.skill;

import com.example.agentruntime.capability.AgentCapability;

public interface Skill extends AgentCapability {

    SkillManifest manifest();
}
