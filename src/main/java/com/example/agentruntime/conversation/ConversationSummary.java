package com.example.agentruntime.conversation;

import java.time.Instant;

public record ConversationSummary(
        String conversationId,
        String title,
        Instant updatedAt,
        Long modelProfileId
) {
}
