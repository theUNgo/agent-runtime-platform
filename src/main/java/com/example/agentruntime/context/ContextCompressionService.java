package com.example.agentruntime.context;

import com.example.agentruntime.conversation.ConversationMessageView;
import com.example.agentruntime.i18n.MessageService;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * 上下文压缩服务。
 * 第一版先聚焦历史消息压缩：保留最近若干条原文，并把更早的消息收敛成结构化背景摘要。
 */
@Service
public class ContextCompressionService {

    private static final int RECENT_MESSAGE_LIMIT = 6;
    private static final DateTimeFormatter TIMELINE_FORMATTER = DateTimeFormatter.ISO_INSTANT;

    private final ContextBudgetService contextBudgetService;
    private final MessageService messageService;

    public ContextCompressionService(ContextBudgetService contextBudgetService,
                                     MessageService messageService) {
        this.contextBudgetService = contextBudgetService;
        this.messageService = messageService;
    }

    /**
     * 为当前一轮模型调用构建背景摘要。
     * 这里会把历史消息拆成“最近原文 + 更早摘要”，并按预算结果决定是否真的做压缩。
     */
    public CompressedContextSnapshot compressConversationHistory(List<ConversationMessageView> messages, String currentUserMessage) {
        List<ConversationMessageView> historyMessages = historyBeforeCurrent(messages, currentUserMessage);
        List<ContextSegment> originalSegments = buildOriginalSegments(historyMessages);
        ContextBudgetReport budget = contextBudgetService.evaluate(originalSegments);

        if (!budget.compressionRequired()) {
            return new CompressedContextSnapshot(
                    budget,
                    originalSegments,
                    originalSegments,
                    List.of(messageService.get("context.compression.skipped")),
                    joinFullHistory(historyMessages)
            );
        }

        List<ConversationMessageView> recentMessages = recentMessages(historyMessages);
        List<ConversationMessageView> olderMessages = olderMessages(historyMessages);
        List<ContextSegment> finalSegments = new ArrayList<>();
        List<String> compressionActions = new ArrayList<>();

        if (!olderMessages.isEmpty()) {
            String olderSummary = summarizeMessages(olderMessages, "较早历史摘要");
            finalSegments.add(new ContextSegment(
                    "history-summary",
                    "conversation_history_summary",
                    "conversation",
                    olderSummary,
                    contextBudgetService.estimateTokens(olderSummary),
                    2,
                    false
            ));
            compressionActions.add(messageService.get("context.compression.historyCompressed"));
        }

        for (int index = 0; index < recentMessages.size(); index++) {
            ConversationMessageView message = recentMessages.get(index);
            String content = formatMessage(message);
            finalSegments.add(new ContextSegment(
                    "history-recent-" + index,
                    "conversation_history_recent",
                    "conversation",
                    content,
                    contextBudgetService.estimateTokens(content),
                    1,
                    false
            ));
        }

        String backgroundSummary = buildBackgroundSummary(olderMessages, recentMessages);
        return new CompressedContextSnapshot(
                budget,
                originalSegments,
                finalSegments,
                compressionActions.isEmpty() ? List.of(messageService.get("context.compression.recentOnly")) : compressionActions,
                backgroundSummary
        );
    }

    private List<ConversationMessageView> historyBeforeCurrent(List<ConversationMessageView> messages, String currentUserMessage) {
        if (messages == null || messages.isEmpty()) {
            return List.of();
        }
        List<ConversationMessageView> result = new ArrayList<>(messages);
        ConversationMessageView last = result.getLast();
        if ("USER".equalsIgnoreCase(last.role())
                && currentUserMessage != null
                && currentUserMessage.equals(last.content())) {
            result.removeLast();
        }
        return result;
    }

    private List<ContextSegment> buildOriginalSegments(List<ConversationMessageView> historyMessages) {
        List<ContextSegment> segments = new ArrayList<>();
        for (int index = 0; index < historyMessages.size(); index++) {
            ConversationMessageView message = historyMessages.get(index);
            String content = formatMessage(message, false);
            segments.add(new ContextSegment(
                    "history-" + index,
                    "conversation_history",
                    "conversation",
                    content,
                    contextBudgetService.estimateTokens(content),
                    2,
                    true
            ));
        }
        return segments;
    }

    private List<ConversationMessageView> recentMessages(List<ConversationMessageView> messages) {
        if (messages.size() <= RECENT_MESSAGE_LIMIT) {
            return messages;
        }
        return messages.subList(messages.size() - RECENT_MESSAGE_LIMIT, messages.size());
    }

    private List<ConversationMessageView> olderMessages(List<ConversationMessageView> messages) {
        if (messages.size() <= RECENT_MESSAGE_LIMIT) {
            return List.of();
        }
        return messages.subList(0, messages.size() - RECENT_MESSAGE_LIMIT);
    }

    private String buildBackgroundSummary(List<ConversationMessageView> olderMessages, List<ConversationMessageView> recentMessages) {
        List<String> sections = new ArrayList<>();
        if (!olderMessages.isEmpty()) {
            sections.add(summarizeMessages(olderMessages, "较早历史摘要"));
        }
        if (!recentMessages.isEmpty()) {
            sections.add("最近关键消息：\n" + joinFullHistory(recentMessages));
        }
        return String.join("\n\n", sections).trim();
    }

    private String summarizeMessages(List<ConversationMessageView> messages, String title) {
        if (messages.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder(title).append("：\n");
        for (ConversationMessageView message : messages) {
            builder.append("- ")
                    .append(roleLabel(message.role()))
                    .append(" @ ")
                    .append(TIMELINE_FORMATTER.format(message.createdAt()))
                    .append("：")
                    .append(compact(message.content()))
                    .append('\n');
        }
        return builder.toString().trim();
    }

    private String joinFullHistory(List<ConversationMessageView> messages) {
        StringBuilder builder = new StringBuilder();
        for (ConversationMessageView message : messages) {
            if (!builder.isEmpty()) {
                builder.append('\n');
            }
            builder.append(formatMessage(message, true));
        }
        return builder.toString();
    }

    private String formatMessage(ConversationMessageView message) {
        return formatMessage(message, true);
    }

    private String formatMessage(ConversationMessageView message, boolean compactContent) {
        return roleLabel(message.role())
                + " @ "
                + TIMELINE_FORMATTER.format(message.createdAt())
                + "："
                + (compactContent ? compact(message.content()) : normalize(message.content()));
    }

    private String compact(String content) {
        String normalized = normalize(content);
        return normalized.length() <= 240 ? normalized : normalized.substring(0, 240) + "...";
    }

    private String normalize(String content) {
        if (content == null || content.isBlank()) {
            return "-";
        }
        return content.replaceAll("\\s+", " ").trim();
    }

    private String roleLabel(String role) {
        if ("USER".equalsIgnoreCase(role)) {
            return "用户";
        }
        if ("ASSISTANT".equalsIgnoreCase(role)) {
            return "助手";
        }
        return role == null || role.isBlank() ? "未知角色" : role;
    }
}
