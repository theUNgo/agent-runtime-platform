package com.example.agentruntime.web;

import com.example.agentruntime.agent.AgentOrchestrator;
import com.example.agentruntime.agent.AgentRequest;
import com.example.agentruntime.agent.AgentResponse;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

/**
 * Agent 对外执行入口。
 */
@Validated
@RestController
@RequestMapping("/api/agent")
public class AgentController {

    private final AgentOrchestrator agentOrchestrator;

    public AgentController(AgentOrchestrator agentOrchestrator) {
        this.agentOrchestrator = agentOrchestrator;
    }

    @PostMapping("/execute")
    public AgentResponse execute(@Valid @RequestBody AgentRequest request) {
        return agentOrchestrator.execute(request);
    }
}
