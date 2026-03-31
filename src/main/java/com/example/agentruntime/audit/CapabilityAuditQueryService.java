package com.example.agentruntime.audit;

import com.example.agentruntime.i18n.MessageService;
import com.example.agentruntime.persistence.entity.UserAccountEntity;
import com.example.agentruntime.persistence.repository.CapabilityInvocationRepository;
import com.example.agentruntime.persistence.repository.UserAccountRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

/**
 * 能力调用审计查询服务。
 * 负责把数据库中的调用记录转换成前端可直接展示的视图，并支持基础筛选。
 */
@Service
public class CapabilityAuditQueryService {

    private final CapabilityInvocationRepository invocationRepository;
    private final UserAccountRepository userAccountRepository;
    private final MessageService messageService;

    public CapabilityAuditQueryService(CapabilityInvocationRepository invocationRepository,
                                       UserAccountRepository userAccountRepository,
                                       MessageService messageService) {
        this.invocationRepository = invocationRepository;
        this.userAccountRepository = userAccountRepository;
        this.messageService = messageService;
    }

    @Transactional(readOnly = true)
    public List<CapabilityInvocationView> listForUser(Long userId, CapabilityAuditQuery query) {
        UserAccountEntity user = userAccountRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException(messageService.get("auth.error.userNotFound")));

        List<CapabilityInvocationView> views = baseViews(user, query == null ? null : query.conversationId());
        if (query == null) {
            return views;
        }

        String normalizedStatus = normalize(query.status());
        String normalizedType = normalize(query.capabilityType());
        String normalizedKeyword = normalize(query.keyword());

        var stream = views.stream()
                .filter(view -> normalizedStatus.isBlank() || normalizedStatus.equals(normalize(view.status())))
                .filter(view -> normalizedType.isBlank() || normalizedType.equals(normalize(view.capabilityType())))
                .filter(view -> normalizedKeyword.isBlank() || searchableText(view).contains(normalizedKeyword));

        if (query.limit() != null && query.limit() > 0) {
            stream = stream.limit(query.limit());
        }
        return stream.toList();
    }

    private List<CapabilityInvocationView> baseViews(UserAccountEntity user, String conversationId) {
        if (conversationId != null && !conversationId.isBlank()) {
            return invocationRepository.findByUserAndConversationIdOrderByIdDesc(user, conversationId).stream()
                    .map(entity -> new CapabilityInvocationView(
                            entity.getId(),
                            entity.getConversationId(),
                            entity.getCapabilityId(),
                            entity.getCapabilityName(),
                            entity.getCapabilityType(),
                            entity.getProvider(),
                            entity.getStatus(),
                            entity.getRequestJson(),
                            entity.getResultJson(),
                            entity.getTraceJson(),
                            entity.getErrorMessage(),
                            entity.getDurationMs(),
                            entity.getCreatedAt()
                    ))
                    .toList();
        }
        return invocationRepository.findByUserOrderByIdDesc(user).stream()
                .map(entity -> new CapabilityInvocationView(
                        entity.getId(),
                        entity.getConversationId(),
                        entity.getCapabilityId(),
                        entity.getCapabilityName(),
                        entity.getCapabilityType(),
                        entity.getProvider(),
                        entity.getStatus(),
                        entity.getRequestJson(),
                        entity.getResultJson(),
                        entity.getTraceJson(),
                        entity.getErrorMessage(),
                        entity.getDurationMs(),
                        entity.getCreatedAt()
                ))
                .toList();
    }

    private String searchableText(CapabilityInvocationView view) {
        return String.join(" ",
                blankToEmpty(view.capabilityId()),
                blankToEmpty(view.capabilityName()),
                blankToEmpty(view.capabilityType()),
                blankToEmpty(view.provider()),
                blankToEmpty(view.status()),
                blankToEmpty(view.errorMessage()))
                .toLowerCase(Locale.ROOT);
    }

    private String normalize(String value) {
        return blankToEmpty(value).toLowerCase(Locale.ROOT);
    }

    private String blankToEmpty(String value) {
        return value == null ? "" : value.trim();
    }
}
