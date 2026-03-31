package com.example.agentruntime.agent;

import com.example.agentruntime.capability.CapabilityDescriptor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;

/**
 * 简单规划器。
 * 这里先通过关键字和能力前缀进行启发式匹配，便于后续替换为 LLM 驱动的规划器。
 */
@Component
public class SimplePlanner {

    public CapabilityDescriptor choose(String message, List<CapabilityDescriptor> candidates) {
        if (candidates.isEmpty()) {
            return null;
        }

        String normalized = message == null ? "" : message.toLowerCase(Locale.ROOT);
        for (CapabilityDescriptor candidate : candidates) {
            String id = candidate.id().toLowerCase(Locale.ROOT);
            // 当用户明确提到“文档/说明/概述”时，优先把请求路由到能力文档动态加载链路。
            if ((normalized.contains("文档") || normalized.contains("说明") || normalized.contains("detail"))
                    && id.equals("builtin:capability.doc.read")) {
                return candidate;
            }
            if ((normalized.contains("搜索") || normalized.contains("查找") || normalized.contains("概述"))
                    && id.equals("builtin:capability.catalog.search")) {
                return candidate;
            }
            if (normalized.contains("skill") && id.startsWith("skill:")) {
                return candidate;
            }
            if ((normalized.contains("file") || normalized.contains("terminal"))
                    && id.startsWith("mcp:")) {
                return candidate;
            }
        }
        return candidates.get(0);
    }
}
