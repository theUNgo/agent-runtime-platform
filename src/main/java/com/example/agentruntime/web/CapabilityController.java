package com.example.agentruntime.web;

import com.example.agentruntime.auth.CurrentUserService;
import com.example.agentruntime.capability.CapabilityDescriptor;
import com.example.agentruntime.capability.CapabilityRegistry;
import com.example.agentruntime.mcp.UserScopedMcpCapabilityService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.stream.Stream;

/**
 * 能力查询接口。
 * 供前端或调试工具查看当前系统已经装载了哪些能力。
 */
@RestController
@RequestMapping("/api/capabilities")
public class CapabilityController {

    private final CapabilityRegistry capabilityRegistry;
    private final CurrentUserService currentUserService;
    private final UserScopedMcpCapabilityService userScopedMcpCapabilityService;

    public CapabilityController(CapabilityRegistry capabilityRegistry,
                                CurrentUserService currentUserService,
                                UserScopedMcpCapabilityService userScopedMcpCapabilityService) {
        this.capabilityRegistry = capabilityRegistry;
        this.currentUserService = currentUserService;
        this.userScopedMcpCapabilityService = userScopedMcpCapabilityService;
    }

    @GetMapping
    public List<CapabilityDescriptor> list(@RequestParam(value = "q", required = false) String query) {
        List<CapabilityDescriptor> base = capabilityRegistry.search(query);
        List<CapabilityDescriptor> userScoped = currentUserService.currentUser()
                .map(user -> userScopedMcpCapabilityService.discoverCapabilities(user).stream()
                        .map(capability -> capability.descriptor())
                        .toList())
                .orElse(List.of());
        return Stream.concat(base.stream(), userScoped.stream())
                .collect(java.util.stream.Collectors.toMap(
                        CapabilityDescriptor::id,
                        descriptor -> descriptor,
                        (first, ignored) -> first,
                        java.util.LinkedHashMap::new
                ))
                .values()
                .stream()
                .filter(descriptor -> query == null || query.isBlank() || searchableText(descriptor).contains(query.toLowerCase(java.util.Locale.ROOT)))
                .sorted(java.util.Comparator.comparing(CapabilityDescriptor::id))
                .toList();
    }

    @PostMapping("/refresh")
    public List<CapabilityDescriptor> refresh() {
        capabilityRegistry.refresh();
        return list(null);
    }

    private String searchableText(CapabilityDescriptor descriptor) {
        String description = descriptor.metadata() == null || descriptor.metadata().description() == null
                ? ""
                : descriptor.metadata().description();
        String tags = descriptor.metadata() == null ? "" : String.join(" ", descriptor.metadata().tags());
        return String.join(" ", descriptor.id(), descriptor.name(), description, tags).toLowerCase(java.util.Locale.ROOT);
    }
}
