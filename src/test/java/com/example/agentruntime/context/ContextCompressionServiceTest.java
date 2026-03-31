package com.example.agentruntime.context;

import com.example.agentruntime.conversation.ConversationMessageView;
import com.example.agentruntime.i18n.MessageService;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.StaticMessageSource;

import java.time.Instant;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContextCompressionServiceTest {

    @Test
    void shouldKeepFullHistoryWhenBudgetIsSmall() {
        ContextCompressionService service = new ContextCompressionService(new ContextBudgetService(), messageService());

        CompressedContextSnapshot snapshot = service.compressConversationHistory(
                List.of(
                        new ConversationMessageView(1L, "USER", "你好", null, Instant.parse("2026-03-31T05:00:00Z")),
                        new ConversationMessageView(2L, "ASSISTANT", "你好，我可以帮你处理任务。", null, Instant.parse("2026-03-31T05:00:05Z")),
                        new ConversationMessageView(3L, "USER", "继续帮我总结这个项目", null, Instant.parse("2026-03-31T05:00:10Z"))
                ),
                "继续帮我总结这个项目"
        );

        assertFalse(snapshot.budget().compressionRequired());
        assertTrue(snapshot.backgroundSummary().contains("你好"));
        assertTrue(snapshot.compressionActions().getFirst().contains("skip compression"));
    }

    @Test
    void shouldCompressOlderHistoryWhenConversationIsLong() {
        ContextCompressionService service = new ContextCompressionService(new ContextBudgetService(), messageService());

        List<ConversationMessageView> messages = List.of(
                new ConversationMessageView(1L, "USER", longText("用户需求", 1400), null, Instant.parse("2026-03-31T05:00:00Z")),
                new ConversationMessageView(2L, "ASSISTANT", longText("助手回复", 1400), null, Instant.parse("2026-03-31T05:01:00Z")),
                new ConversationMessageView(3L, "USER", longText("继续推进", 1400), null, Instant.parse("2026-03-31T05:02:00Z")),
                new ConversationMessageView(4L, "ASSISTANT", longText("阶段总结", 1400), null, Instant.parse("2026-03-31T05:03:00Z")),
                new ConversationMessageView(5L, "USER", longText("补充信息", 1400), null, Instant.parse("2026-03-31T05:04:00Z")),
                new ConversationMessageView(6L, "ASSISTANT", longText("确认继续", 1400), null, Instant.parse("2026-03-31T05:05:00Z")),
                new ConversationMessageView(7L, "USER", longText("新的问题", 1400), null, Instant.parse("2026-03-31T05:06:00Z")),
                new ConversationMessageView(8L, "ASSISTANT", longText("新的回答", 1400), null, Instant.parse("2026-03-31T05:07:00Z")),
                new ConversationMessageView(9L, "USER", "请继续给出最终结论", null, Instant.parse("2026-03-31T05:08:00Z"))
        );

        CompressedContextSnapshot snapshot = service.compressConversationHistory(messages, "请继续给出最终结论");

        assertTrue(snapshot.budget().compressionRequired());
        assertTrue(snapshot.backgroundSummary().contains("较早历史摘要"));
        assertTrue(snapshot.compressionActions().contains("compressed older history"));
        assertTrue(snapshot.finalSegments().size() < snapshot.originalSegments().size());
    }

    private MessageService messageService() {
        StaticMessageSource source = new StaticMessageSource();
        source.addMessage("context.compression.skipped", Locale.ENGLISH, "skip compression");
        source.addMessage("context.compression.skipped", Locale.SIMPLIFIED_CHINESE, "skip compression");
        source.addMessage("context.compression.historyCompressed", Locale.ENGLISH, "compressed older history");
        source.addMessage("context.compression.historyCompressed", Locale.SIMPLIFIED_CHINESE, "compressed older history");
        source.addMessage("context.compression.recentOnly", Locale.ENGLISH, "keep recent history only");
        source.addMessage("context.compression.recentOnly", Locale.SIMPLIFIED_CHINESE, "keep recent history only");
        return new MessageService(source);
    }

    private String longText(String prefix, int count) {
        return prefix + " ".repeat(1) + "x".repeat(count);
    }
}
