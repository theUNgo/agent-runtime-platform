package com.example.agentruntime.web;

import com.example.agentruntime.auth.CurrentUserService;
import com.example.agentruntime.conversation.ConversationHistoryService;
import com.example.agentruntime.conversation.ConversationMessageView;
import com.example.agentruntime.conversation.ConversationSummary;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 当前用户的会话历史接口。
 */
@RestController
@RequestMapping("/api/conversations")
public class ConversationController {

    private final CurrentUserService currentUserService;
    private final ConversationHistoryService conversationHistoryService;

    public ConversationController(CurrentUserService currentUserService,
                                  ConversationHistoryService conversationHistoryService) {
        this.currentUserService = currentUserService;
        this.conversationHistoryService = conversationHistoryService;
    }

    @GetMapping
    public List<ConversationSummary> list() {
        var user = currentUserService.requireUser();
        return conversationHistoryService.listConversations(user.id());
    }

    @GetMapping("/{conversationId}/messages")
    public List<ConversationMessageView> messages(@PathVariable("conversationId") String conversationId) {
        var user = currentUserService.requireUser();
        return conversationHistoryService.listMessages(user.id(), conversationId);
    }
}
