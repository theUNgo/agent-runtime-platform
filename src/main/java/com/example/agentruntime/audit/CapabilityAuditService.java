package com.example.agentruntime.audit;

import com.example.agentruntime.auth.AuthenticatedUser;
import com.example.agentruntime.capability.CapabilityDescriptor;
import com.example.agentruntime.capability.CapabilityResult;
import com.example.agentruntime.persistence.entity.CapabilityInvocationEntity;
import com.example.agentruntime.persistence.entity.UserAccountEntity;
import com.example.agentruntime.persistence.repository.CapabilityInvocationRepository;
import com.example.agentruntime.persistence.repository.UserAccountRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 记录能力调用轨迹。
 */
@Service
public class CapabilityAuditService {

    private final CapabilityInvocationRepository invocationRepository;
    private final UserAccountRepository userAccountRepository;
    private final ObjectMapper objectMapper;

    public CapabilityAuditService(CapabilityInvocationRepository invocationRepository,
                                  UserAccountRepository userAccountRepository,
                                  ObjectMapper objectMapper) {
        this.invocationRepository = invocationRepository;
        this.userAccountRepository = userAccountRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public void logSuccess(AuthenticatedUser user,
                           String conversationId,
                           CapabilityDescriptor descriptor,
                           JsonNode request,
                           CapabilityResult result,
                           Long durationMs,
                           Object trace) {
        CapabilityInvocationEntity entity = baseEntity(user, conversationId, descriptor, request);
        entity.setStatus("SUCCESS");
        entity.setResultJson(writeJson(result.output()));
        entity.setDurationMs(durationMs);
        entity.setTraceJson(writeJson(trace));
        invocationRepository.save(entity);
    }

    /**
     * 记录失败调用。
     * 失败分支也保留耗时与扩展轨迹，方便前端统一画执行时间线。
     */
    @Transactional
    public void logFailure(AuthenticatedUser user,
                           String conversationId,
                           CapabilityDescriptor descriptor,
                           JsonNode request,
                           Exception exception,
                           Long durationMs,
                           Object trace) {
        CapabilityInvocationEntity entity = baseEntity(user, conversationId, descriptor, request);
        entity.setStatus("FAILED");
        entity.setErrorMessage(exception.getMessage());
        entity.setDurationMs(durationMs);
        entity.setTraceJson(writeJson(trace));
        invocationRepository.save(entity);
    }

    private CapabilityInvocationEntity baseEntity(AuthenticatedUser user,
                                                  String conversationId,
                                                  CapabilityDescriptor descriptor,
                                                  JsonNode request) {
        CapabilityInvocationEntity entity = new CapabilityInvocationEntity();
        if (user != null) {
            UserAccountEntity userEntity = userAccountRepository.findById(user.id()).orElse(null);
            entity.setUser(userEntity);
        }
        entity.setConversationId(conversationId);
        entity.setCapabilityId(descriptor.id());
        entity.setCapabilityName(descriptor.name());
        entity.setCapabilityType(descriptor.type().name());
        entity.setProvider(descriptor.metadata() == null ? null : descriptor.metadata().provider());
        entity.setRequestJson(writeJson(request));
        return entity;
    }

    private String writeJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception exception) {
            return "{\"error\":\"serialization_failed\"}";
        }
    }
}
