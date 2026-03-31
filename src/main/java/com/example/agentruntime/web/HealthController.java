package com.example.agentruntime.web;

import com.example.agentruntime.i18n.MessageService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.Map;

/**
 * 健康检查接口。
 */
@RestController
public class HealthController {

    private final MessageService messageService;

    public HealthController(MessageService messageService) {
        this.messageService = messageService;
    }

    @GetMapping("/health")
    public Map<String, String> health() {
        return Collections.singletonMap("status", messageService.get("health.status"));
    }
}
