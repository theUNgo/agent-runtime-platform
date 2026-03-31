package com.example.agentruntime.model;

import com.example.agentruntime.auth.AuthenticatedUser;
import com.example.agentruntime.i18n.MessageService;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 统一调度模型供应商，并向 Agent 层暴露稳定的推理能力。
 */
@Service
public class ModelInvocationService {

    private final UserModelProfileService userModelProfileService;
    private final List<ModelProviderClient> providerClients;
    private final MessageService messageService;

    public ModelInvocationService(UserModelProfileService userModelProfileService,
                                  List<ModelProviderClient> providerClients,
                                  MessageService messageService) {
        this.userModelProfileService = userModelProfileService;
        this.providerClients = providerClients;
        this.messageService = messageService;
    }

    /**
     * 使用当前用户的默认模型档案执行一次对话推理。
     * 这是 Agent 主链路里最常用的入口。
     */
    public ModelChatResponse chatWithActiveModel(AuthenticatedUser user,
                                                 String userMessage,
                                                 JsonNode input,
                                                 JsonNode capabilityResult,
                                                 String backgroundContext) {
        ModelRuntimeProfile profile = userModelProfileService.activeRuntimeProfile(user.id())
                .orElseThrow(() -> new ModelException(
                        ModelErrorCode.MODEL_CONFIGURATION_ERROR,
                        messageService.get("model.error.activeProfileMissing")
        ));
        return chat(profile, userMessage, null, input, capabilityResult, defaultSystemPrompt(capabilityResult != null), backgroundContext);
    }

    /**
     * 使用指定模型档案执行一次对话推理。
     * 该入口主要用于“试跑通过后直接开始对话”的临时模型覆盖场景。
     */
    public ModelChatResponse chatWithProfile(AuthenticatedUser user,
                                             Long profileId,
                                             String userMessage,
                                             JsonNode input,
                                             JsonNode capabilityResult,
                                             String backgroundContext) {
        ModelRuntimeProfile profile = userModelProfileService.runtimeProfile(user.id(), profileId)
                .orElseThrow(() -> new IllegalArgumentException(messageService.get("model.error.profileNotFound", profileId)));
        return chat(profile, userMessage, null, input, capabilityResult, defaultSystemPrompt(capabilityResult != null), backgroundContext);
    }

    /**
     * 对指定模型档案做轻量探测，验证地址、密钥和模型 ID 是否可用。
     */
    public ModelValidationReport validateProfile(AuthenticatedUser user, Long profileId) {
        ModelRuntimeProfile profile = userModelProfileService.runtimeProfile(user.id(), profileId)
                .orElseThrow(() -> new IllegalArgumentException(messageService.get("model.error.profileNotFound", profileId)));
        ModelChatResponse response = chat(
                profile,
                "Please reply with the single word pong.",
                null,
                null,
                null,
                "You are validating that the model endpoint is reachable. Reply briefly.",
                null
        );
        return new ModelValidationReport(
                profile.profileId(),
                profile.profileName(),
                profile.providerType(),
                profile.modelId(),
                true,
                messageService.get("model.validation.success"),
                response.content()
        );
    }

    /**
     * 对指定模型档案执行一次即时试跑。
     * 该入口主要服务于前端工作台，方便用户在保存配置后立刻验证文本或图文能力。
     */
    public ModelPreviewResponse previewProfile(AuthenticatedUser user, Long profileId, ModelPreviewRequest request) {
        if (request == null || request.message() == null || request.message().isBlank()) {
            throw new IllegalArgumentException(messageService.get("model.error.previewMessageRequired"));
        }

        ModelRuntimeProfile profile = userModelProfileService.runtimeProfile(user.id(), profileId)
                .orElseThrow(() -> new IllegalArgumentException(messageService.get("model.error.profileNotFound", profileId)));

        ModelChatResponse response = chat(
                profile,
                request.message().trim(),
                normalizeExamples(request.examples()),
                request.input(),
                null,
                request.systemPrompt() != null && !request.systemPrompt().isBlank()
                        ? request.systemPrompt().trim()
                        : messageService.get("model.preview.systemPrompt"),
                null
        );

        return new ModelPreviewResponse(
                profile.profileId(),
                profile.profileName(),
                profile.providerType(),
                profile.modelId(),
                request.message().trim(),
                response.content(),
                response.finishReason(),
                response.rawResponse()
        );
    }

    /**
     * 根据 providerType 选择具体供应商适配器执行推理。
     */
    public ModelChatResponse chat(ModelRuntimeProfile profile,
                                  String userMessage,
                                  JsonNode examples,
                                  JsonNode input,
                                  JsonNode capabilityResult,
                                  String systemPrompt,
                                  String backgroundContext) {
        ModelProviderClient client = resolveClient(profile.providerType());
        return client.chat(profile, new ModelChatRequest(userMessage, systemPrompt, examples, input, capabilityResult, backgroundContext));
    }

    /**
     * 试跑阶段允许前端传 few-shot 示例，但这里先做最基本的结构校验，
     * 避免把明显非法的 JSON 直接透传到供应商网关。
     */
    private JsonNode normalizeExamples(JsonNode examples) {
        if (examples == null || examples.isNull()) {
            return null;
        }
        if (!examples.isArray()) {
            throw new IllegalArgumentException(messageService.get("model.error.previewExamplesArray"));
        }
        for (JsonNode item : examples) {
            if (item == null || !item.isObject()
                    || item.path("role").asText("").isBlank()
                    || item.path("content").isMissingNode()
                    || item.path("content").isNull()) {
                throw new IllegalArgumentException(messageService.get("model.error.previewExampleInvalid"));
            }
        }
        return examples;
    }

    private ModelProviderClient resolveClient(String providerType) {
        return providerClients.stream()
                .filter(client -> client.supports(providerType))
                .findFirst()
                .orElseThrow(() -> new ModelException(
                        ModelErrorCode.MODEL_PROVIDER_UNSUPPORTED,
                        messageService.get("model.error.providerUnsupported", providerType)
                ));
    }

    private String defaultSystemPrompt(boolean hasCapabilityResult) {
        return hasCapabilityResult
                ? "你是一个智能体结果整理助手，需要把工具和 Skill 的执行结果转成清晰、准确、易读的最终回复。"
                : "你是一个帮助用户完成当前任务的智能助手。";
    }
}
