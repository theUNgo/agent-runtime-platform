package com.example.agentruntime.capability;

import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * 基于内存的能力注册中心实现。
 * 适合当前骨架阶段，后续如果能力很多可替换成带索引或缓存的实现。
 */
@Component
public class InMemoryCapabilityRegistry implements CapabilityRegistry {

    private final List<CapabilityProvider> providers;
    private final Map<String, CapabilityDescriptor> descriptors = new LinkedHashMap<String, CapabilityDescriptor>();
    private final Map<String, CapabilityProvider> owners = new LinkedHashMap<String, CapabilityProvider>();

    public InMemoryCapabilityRegistry(List<CapabilityProvider> providers) {
        this.providers = providers;
        refresh();
    }

    @Override
    public synchronized void refresh() {
        descriptors.clear();
        owners.clear();

        for (CapabilityProvider provider : providers) {
            List<CapabilityDescriptor> discovered = provider.discover();
            for (CapabilityDescriptor descriptor : discovered) {
                descriptors.put(descriptor.id(), descriptor);
                owners.put(descriptor.id(), provider);
            }
        }
    }

    @Override
    public synchronized List<CapabilityDescriptor> listAll() {
        return descriptors.values().stream()
                .sorted(Comparator.comparing(CapabilityDescriptor::id))
                .toList();
    }

    @Override
    public synchronized List<CapabilityDescriptor> search(String query) {
        if (query == null || query.trim().isEmpty()) {
            return listAll();
        }

        String normalized = query.toLowerCase(Locale.ROOT);
        return descriptors.values().stream()
                .filter(descriptor -> searchableText(descriptor).contains(normalized))
                .sorted(Comparator.comparing(CapabilityDescriptor::id))
                .toList();
    }

    @Override
    public synchronized Optional<AgentCapability> get(String capabilityId) {
        CapabilityProvider provider = owners.get(capabilityId);
        if (provider == null) {
            return Optional.empty();
        }
        return provider.resolve(capabilityId);
    }

    private String searchableText(CapabilityDescriptor descriptor) {
        CapabilityMetadata metadata = descriptor.metadata();
        return String.join(" ",
                descriptor.id(),
                descriptor.name(),
                metadata != null && metadata.description() != null ? metadata.description() : "",
                metadata != null ? String.join(" ", metadata.tags()) : "")
                .toLowerCase(Locale.ROOT);
    }
}
