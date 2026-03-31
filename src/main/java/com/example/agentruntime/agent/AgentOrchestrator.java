package com.example.agentruntime.agent;

import com.example.agentruntime.AgentRuntimeProperties;
import com.example.agentruntime.audit.CapabilityAuditService;
import com.example.agentruntime.auth.AuthenticatedUser;
import com.example.agentruntime.auth.CurrentUserService;
import com.example.agentruntime.capability.AgentCapability;
import com.example.agentruntime.capability.CapabilityContext;
import com.example.agentruntime.capability.CapabilityDescriptor;
import com.example.agentruntime.capability.CapabilityRegistry;
import com.example.agentruntime.capability.CapabilityResult;
import com.example.agentruntime.conversation.ConversationHistoryService;
import com.example.agentruntime.context.CapabilityResultCompression;
import com.example.agentruntime.context.CapabilityResultCompressionService;
import com.example.agentruntime.context.CompressedContextSnapshot;
import com.example.agentruntime.context.ContextCompressionService;
import com.example.agentruntime.context.ResourceResultCompressionService;
import com.example.agentruntime.context.SkillResultCompressionService;
import com.example.agentruntime.document.CapabilityDocumentPlanningService;
import com.example.agentruntime.i18n.MessageService;
import com.example.agentruntime.installation.UserInstallationService;
import com.example.agentruntime.mcp.UserScopedMcpCapabilityService;
import com.example.agentruntime.model.ModelChatResponse;
import com.example.agentruntime.model.ModelException;
import com.example.agentruntime.model.ModelInvocationService;
import com.example.agentruntime.model.UserModelProfileService;
import com.example.agentruntime.persistence.entity.AgentConversationEntity;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
import java.util.Map;

/**
 * Agent 请求编排入口。
 * 当前版本只执行一步，后续会在这里扩展为多步推理和工具调用循环。
 */
@Service
public class AgentOrchestrator {

    private final CapabilityRegistry capabilityRegistry;
    private final SimplePlanner planner;
    private final AgentRuntimeProperties properties;
    private final MessageService messageService;
    private final CurrentUserService currentUserService;
    private final ConversationHistoryService conversationHistoryService;
    private final UserInstallationService userInstallationService;
    private final CapabilityAuditService capabilityAuditService;
    private final UserScopedMcpCapabilityService userScopedMcpCapabilityService;
    private final UserModelProfileService userModelProfileService;
    private final ModelInvocationService modelInvocationService;
    private final ContextCompressionService contextCompressionService;
    private final CapabilityResultCompressionService capabilityResultCompressionService;
    private final ResourceResultCompressionService resourceResultCompressionService;
    private final SkillResultCompressionService skillResultCompressionService;
    private final CapabilityDocumentPlanningService capabilityDocumentPlanningService;
    private final ObjectMapper objectMapper;

    public AgentOrchestrator(CapabilityRegistry capabilityRegistry,
                             SimplePlanner planner,
                             AgentRuntimeProperties properties,
                             MessageService messageService,
                             CurrentUserService currentUserService,
                             ConversationHistoryService conversationHistoryService,
                             UserInstallationService userInstallationService,
                             CapabilityAuditService capabilityAuditService,
                             UserScopedMcpCapabilityService userScopedMcpCapabilityService,
                             UserModelProfileService userModelProfileService,
                             ModelInvocationService modelInvocationService,
                             ContextCompressionService contextCompressionService,
                             CapabilityResultCompressionService capabilityResultCompressionService,
                             ResourceResultCompressionService resourceResultCompressionService,
                             SkillResultCompressionService skillResultCompressionService,
                             CapabilityDocumentPlanningService capabilityDocumentPlanningService,
                             ObjectMapper objectMapper) {
        this.capabilityRegistry = capabilityRegistry;
        this.planner = planner;
        this.properties = properties;
        this.messageService = messageService;
        this.currentUserService = currentUserService;
        this.conversationHistoryService = conversationHistoryService;
        this.userInstallationService = userInstallationService;
        this.capabilityAuditService = capabilityAuditService;
        this.userScopedMcpCapabilityService = userScopedMcpCapabilityService;
        this.userModelProfileService = userModelProfileService;
        this.modelInvocationService = modelInvocationService;
        this.contextCompressionService = contextCompressionService;
        this.capabilityResultCompressionService = capabilityResultCompressionService;
        this.resourceResultCompressionService = resourceResultCompressionService;
        this.skillResultCompressionService = skillResultCompressionService;
        this.capabilityDocumentPlanningService = capabilityDocumentPlanningService;
        this.objectMapper = objectMapper;
    }

    /**
     * 执行一次最小化的 Agent 处理流程。
     */
    public AgentResponse execute(AgentRequest request) {
        AuthenticatedUser user = currentUserService.requireUser();
        AgentConversationEntity conversation = conversationHistoryService.ensureConversation(
                user.id(),
                request.conversationId(),
                request.message(),
                request.modelProfileId()
        );
        Long requestedModelProfileId = request.modelProfileId();
        Long rememberedModelProfileId = conversation.getModelProfileId();
        Long effectiveModelProfileId = requestedModelProfileId != null ? requestedModelProfileId : rememberedModelProfileId;
        AgentModelSelection modelSelection = resolveModelSelection(user, conversation, requestedModelProfileId, effectiveModelProfileId);
        String conversationId = conversation.getConversationId();
        conversationHistoryService.appendMessage(conversation, "USER", request.message(), writeJson(request.input()));
        CompressedContextSnapshot contextSnapshot = contextCompressionService.compressConversationHistory(
                conversationHistoryService.listMessages(user.id(), conversationId),
                request.message()
        );
        String backgroundContext = contextSnapshot.backgroundSummary();

        List<CapabilityDescriptor> candidates = mergeCandidates(
                filterForUser(user, capabilityRegistry.search(request.message())),
                userScopedMcpCapabilityService.discoverCapabilities(user).stream()
                        .map(AgentCapability::descriptor)
                        .toList()
        );

        if (capabilityDocumentPlanningService.isDocumentLookupIntent(request.message())) {
            AgentResponse documentResponse = executeDocumentLookupFlow(
                    user,
                    request,
                    conversation,
                    conversationId,
                    requestedModelProfileId,
                    modelSelection,
                    backgroundContext,
                    contextSnapshot,
                    candidates
            );
            if (documentResponse != null) {
                return documentResponse;
            }
        }

        CapabilityDescriptor selected = planner.choose(request.message(), candidates);

        if (selected == null) {
            if (modelSelection != null) {
                ModelChatResponse modelResponse = requestedModelProfileId == null
                        ? modelInvocationService.chatWithActiveModel(user, request.message(), request.input(), null, backgroundContext)
                        : modelInvocationService.chatWithProfile(user, requestedModelProfileId, request.message(), request.input(), null, backgroundContext);
                conversationHistoryService.appendMessage(conversation, "ASSISTANT", modelResponse.content(), writeJson(modelResponse.rawResponse()));
                return new AgentResponse(
                        conversationId,
                        request.message(),
                        modelResponse.content(),
                        List.of(),
                        modelSelection
                );
            }

            conversationHistoryService.appendMessage(conversation, "ASSISTANT", messageService.get("agent.noCapability"), null);
            return new AgentResponse(
                    conversationId,
                    request.message(),
                    messageService.get("agent.noCapability"),
                    List.of(),
                    modelSelection
            );
        }

        AgentCapability capability = capabilityRegistry.get(selected.id())
                .or(() -> userScopedMcpCapabilityService.resolve(user, selected.id()).map(AgentCapability.class::cast))
                .orElseThrow(() -> new IllegalStateException(messageService.get("agent.capabilityMissing", selected.id())));

        CapabilityContext context = new CapabilityContext(
                conversationId,
                request.message(),
                properties.workspaceRoot(),
                modelSelection
        );
        long capabilityStartedAt = System.nanoTime();
        try {
            CapabilityResult result = capability.execute(context, request.input());
            return finalizeResponse(
                    user,
                    conversation,
                    conversationId,
                    request,
                    requestedModelProfileId,
                    modelSelection,
                    backgroundContext,
                    contextSnapshot,
                    selected,
                    request.input(),
                    result,
                    List.of(new ExecutionStep(selected, result)),
                    capabilityStartedAt
            );
        } catch (RuntimeException exception) {
            Long totalDurationMs = nanosToMillis(System.nanoTime() - capabilityStartedAt);
            capabilityAuditService.logFailure(
                    user,
                    conversationId,
                    selected,
                    request.input(),
                    exception,
                    totalDurationMs,
                    Map.of(
                            "assistantMessageSource", "error",
                            "failureCategory", classifyFailure(exception),
                            "phaseDurations", Map.of(
                                    "totalDurationMs", totalDurationMs
                            )
                    )
            );
            conversationHistoryService.appendMessage(conversation, "ASSISTANT", exception.getMessage(), null);
            throw exception;
        }
    }

    /**
     * 解析当前这次执行应使用的模型。
     * 优先级依次为：请求显式指定 > 会话记忆 > 用户默认模型。
     */
    private AgentModelSelection resolveModelSelection(AuthenticatedUser user,
                                                      AgentConversationEntity conversation,
                                                      Long requestedModelProfileId,
                                                      Long effectiveModelProfileId) {
        if (effectiveModelProfileId == null) {
            return userModelProfileService.activeSelection(user.id()).orElse(null);
        }

        return userModelProfileService.selection(user.id(), effectiveModelProfileId)
                .orElseGet(() -> {
                    if (requestedModelProfileId != null) {
                        throw new IllegalArgumentException(messageService.get("model.error.profileNotFound", requestedModelProfileId));
                    }
                    conversationHistoryService.rememberModelProfile(conversation, null);
                    return userModelProfileService.activeSelection(user.id()).orElse(null);
                });
    }

    private List<CapabilityDescriptor> filterForUser(AuthenticatedUser user, List<CapabilityDescriptor> candidates) {
        Set<String> installedSkillIds = userInstallationService.installedSkillIds(user.id());
        Set<String> installedMcpServers = userInstallationService.installedMcpServerIds(user.id());
        boolean restrictSkills = userInstallationService.hasInstalledSkillSelection(user.id());
        boolean restrictMcp = userInstallationService.hasInstalledMcpSelection(user.id());

        return candidates.stream()
                .filter(candidate -> switch (candidate.type()) {
                    case BUILTIN -> true;
                    case SKILL -> !restrictSkills || installedSkillIds.contains(stripPrefix(candidate.id(), "skill:"));
                    case MCP_TOOL -> !restrictMcp || installedMcpServers.contains(candidate.metadata().provider());
                })
                .toList();
    }

    private List<CapabilityDescriptor> mergeCandidates(List<CapabilityDescriptor> left, List<CapabilityDescriptor> right) {
        return java.util.stream.Stream.concat(left.stream(), right.stream())
                .collect(java.util.stream.Collectors.toMap(
                        CapabilityDescriptor::id,
                        descriptor -> descriptor,
                        (first, ignored) -> first,
                        java.util.LinkedHashMap::new
                ))
                .values()
                .stream()
                .toList();
    }

    private String stripPrefix(String value, String prefix) {
        return value != null && value.startsWith(prefix) ? value.substring(prefix.length()) : value;
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

    /**
     * 构建阶段耗时信息。
     * 这里单独收口 capability / model 两段，便于审计面板直接展示结构化时间线。
     */
    private Map<String, Long> buildPhaseDurations(Long capabilityDurationMs, Long modelDurationMs) {
        Map<String, Long> phaseDurations = new java.util.LinkedHashMap<>();
        phaseDurations.put("capabilityDurationMs", capabilityDurationMs);
        if (modelDurationMs != null) {
            phaseDurations.put("modelDurationMs", modelDurationMs);
        }
        return phaseDurations;
    }

    /**
     * 将总耗时补进轨迹对象，避免前端只能自行从若干阶段字段拼装总时长。
     */
    private Object enrichTraceWithTotalDuration(Object trace, Long totalDurationMs) {
        if (!(trace instanceof Map<?, ?> traceMap)) {
            return trace;
        }
        Map<String, Object> mutableTrace = new java.util.LinkedHashMap<>();
        traceMap.forEach((key, value) -> mutableTrace.put(String.valueOf(key), value));
        Map<String, Object> phaseDurations = new java.util.LinkedHashMap<>();
        Object existingPhaseDurations = mutableTrace.get("phaseDurations");
        if (existingPhaseDurations instanceof Map<?, ?> existingMap) {
            existingMap.forEach((key, value) -> phaseDurations.put(String.valueOf(key), value));
        }
        phaseDurations.put("totalDurationMs", totalDurationMs);
        mutableTrace.put("phaseDurations", phaseDurations);
        return mutableTrace;
    }

    /**
     * 对失败原因做轻量归类。
     * 审计面板会直接展示这个类别，帮助用户更快判断是超时、鉴权还是参数问题。
     */
    private String classifyFailure(RuntimeException exception) {
        String message = exception.getMessage() == null ? "" : exception.getMessage().toLowerCase();
        if (message.contains("timeout") || message.contains("超时")) {
            return "TIMEOUT";
        }
        if (message.contains("auth") || message.contains("token") || message.contains("鉴权")) {
            return "AUTH";
        }
        if (message.contains("not found") || message.contains("未找到")) {
            return "NOT_FOUND";
        }
        if (message.contains("validation") || message.contains("参数") || message.contains("校验")) {
            return "VALIDATION";
        }
        if (message.contains("network") || message.contains("connect") || message.contains("连接")) {
            return "NETWORK";
        }
        return "UNKNOWN";
    }

    private Long nanosToMillis(long nanos) {
        return java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(Math.max(0L, nanos));
    }

    /**
     * 针对“查能力说明文档”的请求执行最小多步规划：
     * 1. 先搜索能力摘要
     * 2. 再按需读取 detail 文档
     */
    private AgentResponse executeDocumentLookupFlow(AuthenticatedUser user,
                                                    AgentRequest request,
                                                    AgentConversationEntity conversation,
                                                    String conversationId,
                                                    Long requestedModelProfileId,
                                                    AgentModelSelection modelSelection,
                                                    String backgroundContext,
                                                    CompressedContextSnapshot contextSnapshot,
                                                    List<CapabilityDescriptor> candidates) {
        CapabilityDescriptor catalogSearchDescriptor = findCapability(candidates, "builtin:capability.catalog.search");
        CapabilityDescriptor docReadDescriptor = findCapability(candidates, "builtin:capability.doc.read");
        if (catalogSearchDescriptor == null || docReadDescriptor == null) {
            return null;
        }

        CapabilityContext context = new CapabilityContext(
                conversationId,
                request.message(),
                properties.workspaceRoot(),
                modelSelection
        );
        JsonNode searchInput = capabilityDocumentPlanningService.buildSearchInput(request.message());
        AgentCapability searchCapability = capabilityRegistry.get(catalogSearchDescriptor.id()).orElse(null);
        AgentCapability docReadCapability = capabilityRegistry.get(docReadDescriptor.id()).orElse(null);
        if (searchCapability == null || docReadCapability == null) {
            return null;
        }

        long searchStartedAt = System.nanoTime();
        CapabilityResult searchResult;
        try {
            searchResult = searchCapability.execute(context, searchInput);
        } catch (RuntimeException exception) {
            Long durationMs = nanosToMillis(System.nanoTime() - searchStartedAt);
            capabilityAuditService.logFailure(
                    user,
                    conversationId,
                    catalogSearchDescriptor,
                    searchInput,
                    exception,
                    durationMs,
                    Map.of(
                            "assistantMessageSource", "intermediate_error",
                            "failureCategory", classifyFailure(exception),
                            "phaseDurations", Map.of(
                                    "totalDurationMs", durationMs
                            ),
                            "contextCompression", buildCompressionTrace(contextSnapshot)
                    )
            );
            throw exception;
        }
        Long searchDurationMs = nanosToMillis(System.nanoTime() - searchStartedAt);
        String desiredDocType = capabilityDocumentPlanningService.desiredDocType(request.message());
        String docId = capabilityDocumentPlanningService.chooseDocId(searchResult.output(), request.message());
        if (docId == null) {
            long finalStartedAt = System.nanoTime();
            return finalizeResponse(
                    user,
                    conversation,
                    conversationId,
                    request,
                    requestedModelProfileId,
                    modelSelection,
                    backgroundContext,
                    contextSnapshot,
                    catalogSearchDescriptor,
                    searchInput,
                    searchResult,
                    List.of(new ExecutionStep(catalogSearchDescriptor, searchResult)),
                    finalStartedAt
            );
        }

        logIntermediateCapabilityStep(
                user,
                conversationId,
                contextSnapshot,
                catalogSearchDescriptor,
                searchInput,
                searchResult,
                searchDurationMs
        );
        ExecutionStep searchStep = new ExecutionStep(catalogSearchDescriptor, searchResult);
        ObjectNode docReadInput = objectMapper.createObjectNode();
        docReadInput.put("docId", docId);
        docReadInput.put("docType", desiredDocType);
        long finalStartedAt = System.nanoTime();
        CapabilityResult docResult;
        try {
            docResult = docReadCapability.execute(context, docReadInput);
        } catch (RuntimeException exception) {
            Long durationMs = nanosToMillis(System.nanoTime() - finalStartedAt);
            capabilityAuditService.logFailure(
                    user,
                    conversationId,
                    docReadDescriptor,
                    docReadInput,
                    exception,
                    durationMs,
                    Map.of(
                            "assistantMessageSource", "error",
                            "failureCategory", classifyFailure(exception),
                            "phaseDurations", Map.of(
                                    "totalDurationMs", durationMs
                            ),
                            "contextCompression", buildCompressionTrace(contextSnapshot)
                    )
            );
            throw exception;
        }
        return finalizeResponse(
                user,
                conversation,
                conversationId,
                request,
                requestedModelProfileId,
                modelSelection,
                backgroundContext,
                contextSnapshot,
                docReadDescriptor,
                docReadInput,
                docResult,
                List.of(searchStep, new ExecutionStep(docReadDescriptor, docResult)),
                finalStartedAt
        );
    }

    /**
     * 执行中间步骤并写入审计。
     * 这类步骤只服务于多步规划链路，因此不直接生成对用户的最终回复。
     */
    private void logIntermediateCapabilityStep(AuthenticatedUser user,
                                               String conversationId,
                                               CompressedContextSnapshot contextSnapshot,
                                               CapabilityDescriptor descriptor,
                                               JsonNode input,
                                               CapabilityResult result,
                                               Long durationMs) {
        capabilityAuditService.logSuccess(
                user,
                conversationId,
                descriptor,
                input,
                result,
                durationMs,
                Map.of(
                        "assistantMessageSource", "intermediate_step",
                        "phaseDurations", Map.of(
                                "capabilityDurationMs", durationMs,
                                "totalDurationMs", durationMs
                        ),
                        "contextCompression", buildCompressionTrace(contextSnapshot)
                )
        );
    }

    /**
     * 收口最终响应的模型整理、审计和消息落库。
     * 单步执行和多步文档规划最终都会走到这里，保持行为一致。
     */
    private AgentResponse finalizeResponse(AuthenticatedUser user,
                                           AgentConversationEntity conversation,
                                           String conversationId,
                                           AgentRequest request,
                                           Long requestedModelProfileId,
                                           AgentModelSelection modelSelection,
                                           String backgroundContext,
                                           CompressedContextSnapshot contextSnapshot,
                                           CapabilityDescriptor selected,
                                           JsonNode executedInput,
                                           CapabilityResult result,
                                           List<ExecutionStep> steps,
                                           long capabilityStartedAt) {
        long capabilityFinishedAt = System.nanoTime();
        String decision = messageService.get("agent.selectedCapability", selected.id());
        JsonNode resultNode = objectMapper.valueToTree(result);
        CapabilityResultCompression resultCompression = capabilityResultCompressionService.compress(
                contextSnapshot,
                selected,
                result
        );
        resultCompression = resourceResultCompressionService.compressIfNeeded(
                contextSnapshot,
                selected,
                result,
                resultCompression
        );
        resultCompression = skillResultCompressionService.compressIfNeeded(
                contextSnapshot,
                selected,
                result,
                resultCompression
        );
        String assistantMessage = capabilityDocumentPlanningService.renderCapabilityOnlyResponse(selected, result);
        if (assistantMessage == null || assistantMessage.isBlank()) {
            assistantMessage = decision;
        }
        String payloadJson = writeJson(resultNode);
        Long capabilityDurationMs = nanosToMillis(capabilityFinishedAt - capabilityStartedAt);
        Long modelDurationMs = null;
        Object auditTrace = Map.of(
                "capabilityOnly", modelSelection == null,
                "modelRequested", modelSelection != null,
                "assistantMessageSource", "capability",
                "phaseDurations", Map.of(
                        "capabilityDurationMs", capabilityDurationMs
                ),
                "contextCompression", buildCompressionTrace(contextSnapshot)
        );

        if (modelSelection != null) {
            long modelStartedAt = System.nanoTime();
            try {
                ModelChatResponse modelResponse = requestedModelProfileId == null
                        ? modelInvocationService.chatWithActiveModel(
                        user,
                        request.message(),
                        request.input(),
                        resultCompression.resultForModel(),
                        resultCompression.backgroundSummary()
                )
                        : modelInvocationService.chatWithProfile(
                        user,
                        requestedModelProfileId,
                        request.message(),
                        request.input(),
                        resultCompression.resultForModel(),
                        resultCompression.backgroundSummary()
                );
                modelDurationMs = nanosToMillis(System.nanoTime() - modelStartedAt);
                assistantMessage = modelResponse.content();
                payloadJson = writeJson(modelResponse.rawResponse());
                auditTrace = Map.of(
                        "capabilityOnly", false,
                        "modelRequested", true,
                        "assistantMessageSource", "model",
                        "phaseDurations", buildPhaseDurations(capabilityDurationMs, modelDurationMs),
                        "model", Map.of(
                                "profileId", modelSelection.profileId(),
                                "profileName", modelSelection.profileName(),
                                "providerType", modelSelection.providerType(),
                                "modelId", modelSelection.modelId(),
                                "finishReason", modelResponse.finishReason()
                        ),
                        "capabilityCompression", buildCapabilityCompressionTrace(resultCompression),
                        "contextCompression", buildCompressionTrace(contextSnapshot)
                );
            } catch (ModelException exception) {
                modelDurationMs = nanosToMillis(System.nanoTime() - modelStartedAt);
                auditTrace = Map.of(
                        "capabilityOnly", false,
                        "modelRequested", true,
                        "assistantMessageSource", "capability_fallback",
                        "phaseDurations", buildPhaseDurations(capabilityDurationMs, modelDurationMs),
                        "model", Map.of(
                                "profileId", modelSelection.profileId(),
                                "profileName", modelSelection.profileName(),
                                "providerType", modelSelection.providerType(),
                                "modelId", modelSelection.modelId(),
                                "error", exception.getMessage()
                        ),
                        "capabilityCompression", buildCapabilityCompressionTrace(resultCompression),
                        "contextCompression", buildCompressionTrace(contextSnapshot)
                );
                payloadJson = writeJson(Map.of(
                        "fallback", "capability_only",
                        "modelError", exception.getMessage(),
                        "capabilityResult", resultNode
                ));
            }
        }

        Long totalDurationMs = nanosToMillis(System.nanoTime() - capabilityStartedAt);
        auditTrace = enrichTraceWithTotalDuration(auditTrace, totalDurationMs);
        capabilityAuditService.logSuccess(user, conversationId, selected, executedInput, result, totalDurationMs, auditTrace);
        conversationHistoryService.appendMessage(conversation, "ASSISTANT", assistantMessage, payloadJson);
        return new AgentResponse(
                conversationId,
                request.message(),
                assistantMessage,
                steps,
                modelSelection
        );
    }

    /**
     * 将能力结果压缩摘要写入审计，方便前端判断本轮是否对 MCP / Skill 长结果做了裁剪。
     */
    private Map<String, Object> buildCapabilityCompressionTrace(CapabilityResultCompression compression) {
        return Map.of(
                "compressed", compression.compressed(),
                "rawTokens", compression.rawTokens(),
                "finalTokens", compression.finalTokens(),
                "actions", compression.compressionActions()
        );
    }

    private CapabilityDescriptor findCapability(List<CapabilityDescriptor> candidates, String capabilityId) {
        return candidates.stream()
                .filter(candidate -> capabilityId.equals(candidate.id()))
                .findFirst()
                .orElse(null);
    }

    /**
     * 将压缩快照转成轻量轨迹结构。
     * 审计中只保留预算与动作摘要，避免把完整原始上下文再次膨胀塞回轨迹里。
     */
    private Map<String, Object> buildCompressionTrace(CompressedContextSnapshot snapshot) {
        return Map.of(
                "usedTokens", snapshot.budget().usedTokens(),
                "backgroundTokens", snapshot.budget().backgroundTokens(),
                "usageRatio", snapshot.budget().usageRatio(),
                "backgroundRatio", snapshot.budget().backgroundRatio(),
                "compressionRequired", snapshot.budget().compressionRequired(),
                "actions", snapshot.compressionActions(),
                "finalSegmentCount", snapshot.finalSegments().size()
        );
    }
}
