package com.example.agentruntime.builtin;

import com.example.agentruntime.capability.AgentCapability;
import com.example.agentruntime.capability.CapabilityDescriptor;
import com.example.agentruntime.capability.CapabilityProvider;
import com.example.agentruntime.document.CapabilityDocumentService;
import com.example.agentruntime.i18n.MessageService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
public class BuiltinCapabilityProvider implements CapabilityProvider {

    private final Map<String, AgentCapability> capabilities = new LinkedHashMap<String, AgentCapability>();

    public BuiltinCapabilityProvider(ObjectMapper objectMapper,
                                     MessageService messageService,
                                     CapabilityDocumentService capabilityDocumentService) {
        AgentCapability echo = new BuiltinEchoCapability(objectMapper, messageService);
        AgentCapability catalogSearch = new CapabilityCatalogSearchCapability(capabilityDocumentService, objectMapper, messageService);
        AgentCapability docRead = new CapabilityDocumentReadCapability(capabilityDocumentService, objectMapper, messageService);
        capabilities.put(echo.descriptor().id(), echo);
        capabilities.put(catalogSearch.descriptor().id(), catalogSearch);
        capabilities.put(docRead.descriptor().id(), docRead);
    }

    @Override
    public String providerId() {
        return "builtin-provider";
    }

    @Override
    public List<CapabilityDescriptor> discover() {
        return capabilities.values().stream()
                .map(AgentCapability::descriptor)
                .toList();
    }

    @Override
    public Optional<AgentCapability> resolve(String capabilityId) {
        return Optional.ofNullable(capabilities.get(capabilityId));
    }
}
