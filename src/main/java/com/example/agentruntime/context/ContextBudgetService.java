package com.example.agentruntime.context;

import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 上下文预算评估服务。
 * 第一版使用轻量估算规则近似 token 数，先解决“什么时候需要压缩”的判断问题。
 */
@Service
public class ContextBudgetService {

    private static final int DEFAULT_MAX_TOKENS = 16_000;
    private static final int BACKGROUND_RATIO_GUARD_TOKENS = 2_000;

    /**
     * 评估上下文占用情况。
     * 如果总占用率过高，或者背景信息在总上下文中的占比过高，就建议触发压缩。
     */
    public ContextBudgetReport evaluate(List<ContextSegment> segments) {
        int usedTokens = segments.stream()
                .mapToInt(ContextSegment::estimatedTokens)
                .sum();
        int backgroundTokens = segments.stream()
                .filter(segment -> segment.compressible() || "conversation_history".equals(segment.type()))
                .mapToInt(ContextSegment::estimatedTokens)
                .sum();
        double usageRatio = DEFAULT_MAX_TOKENS == 0 ? 0D : (double) usedTokens / DEFAULT_MAX_TOKENS;
        double backgroundRatio = usedTokens == 0 ? 0D : (double) backgroundTokens / usedTokens;
        boolean compressionRequired = usageRatio >= 0.55D
                || (usedTokens >= BACKGROUND_RATIO_GUARD_TOKENS && backgroundRatio >= 0.60D);
        return new ContextBudgetReport(
                DEFAULT_MAX_TOKENS,
                usedTokens,
                backgroundTokens,
                usageRatio,
                backgroundRatio,
                compressionRequired
        );
    }

    /**
     * 使用统一估算规则计算文本 token 数。
     * 第一版先按“约 4 个字符 ~= 1 个 token”近似，后续可以再切换成更精确的 tokenizer。
     */
    public int estimateTokens(String content) {
        if (content == null || content.isBlank()) {
            return 0;
        }
        return Math.max(1, (int) Math.ceil(content.trim().length() / 4.0D));
    }
}
