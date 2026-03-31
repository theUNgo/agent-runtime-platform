package com.example.agentruntime.conversation;

import java.time.Instant;

public record ConversationMessageView(
        Long id,
        String role,
        String content,
        String payloadJson,
        Instant createdAt
) {
}
