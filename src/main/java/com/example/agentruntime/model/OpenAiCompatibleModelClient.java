package com.example.agentruntime.model;

import com.example.agentruntime.i18n.MessageService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Iterator;
import java.util.Locale;
import java.util.Map;

/**
 * 基于 OpenAI-compatible Chat Completions 协议的模型客户端。
 * 智谱、OpenAI 兼容网关以及很多私有代理都可以先走这条链路。
 */
@Component
public class OpenAiCompatibleModelClient implements ModelProviderClient {

    private final RestClient.Builder restClientBuilder;
    private final ObjectMapper objectMapper;
    private final MessageService messageService;

    public OpenAiCompatibleModelClient(RestClient.Builder restClientBuilder,
                                       ObjectMapper objectMapper,
                                       MessageService messageService) {
        this.restClientBuilder = restClientBuilder;
        this.objectMapper = objectMapper;
        this.messageService = messageService;
    }

    @Override
    public boolean supports(String providerType) {
        if (providerType == null || providerType.isBlank()) {
            return true;
        }
        String normalized = providerType.trim().toLowerCase(Locale.ROOT);
        return normalized.equals("openai-compatible")
                || normalized.equals("custom")
                || normalized.equals("azure-openai");
    }

    @Override
    public ModelChatResponse chat(ModelRuntimeProfile profile, ModelChatRequest request) {
        // 先做运行时参数校验，避免把无效请求直接打到外部模型服务。
        validateProfile(profile);
        String endpoint = resolveEndpoint(profile.baseUrl());
        ObjectNode payload = buildPayload(profile, request);

        try {
            RestClient restClient = restClientBuilder.build();
            JsonNode response = restClient.post()
                    .uri(endpoint)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + profile.apiKey())
                    .body(payload)
                    .retrieve()
                    .body(JsonNode.class);
            return parseResponse(profile, response);
        } catch (ModelException exception) {
            throw exception;
        } catch (ResourceAccessException exception) {
            throw new ModelException(ModelErrorCode.MODEL_TIMEOUT,
                    messageService.get("model.error.timeout"), exception);
        } catch (RestClientException exception) {
            throw new ModelException(ModelErrorCode.MODEL_REQUEST_FAILED,
                    messageService.get("model.error.requestFailed"), exception);
        }
    }

    private void validateProfile(ModelRuntimeProfile profile) {
        if (profile == null || profile.baseUrl() == null || profile.baseUrl().isBlank()) {
            throw new ModelException(ModelErrorCode.MODEL_CONFIGURATION_ERROR,
                    messageService.get("model.error.baseUrlRequired"));
        }
        if (profile.apiKey() == null || profile.apiKey().isBlank()) {
            throw new ModelException(ModelErrorCode.MODEL_CONFIGURATION_ERROR,
                    messageService.get("model.error.apiKeyRequired"));
        }
        if (profile.modelId() == null || profile.modelId().isBlank()) {
            throw new ModelException(ModelErrorCode.MODEL_CONFIGURATION_ERROR,
                    messageService.get("model.error.modelIdRequired"));
        }
    }

    private String resolveEndpoint(String baseUrl) {
        String normalized = baseUrl.trim();
        if (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        if (normalized.endsWith("/chat/completions")) {
            return normalized;
        }
        return normalized + "/chat/completions";
    }

    private ObjectNode buildPayload(ModelRuntimeProfile profile, ModelChatRequest request) {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("model", profile.modelId());
        ArrayNode messages = payload.putArray("messages");

        if (request.systemPrompt() != null && !request.systemPrompt().isBlank()) {
            ObjectNode system = messages.addObject();
            system.put("role", "system");
            system.put("content", request.systemPrompt());
        }

        appendExampleMessages(request.examples(), messages);

        ObjectNode user = messages.addObject();
        user.put("role", "user");
        user.set("content", buildUserContent(request));

        copyOptional(request.input(), payload, "thinking");
        copyOptional(request.input(), payload, "temperature");
        copyOptional(request.input(), payload, "top_p");
        copyOptional(request.input(), payload, "max_tokens");
        copyAliasedOptional(request.input(), payload, "maxTokens", "max_tokens");
        copyAliasedOptional(request.input(), payload, "responseFormat", "response_format");
        mergeExtraBody(request.input(), payload, "extraBody");
        return payload;
    }

    private JsonNode buildUserContent(ModelChatRequest request) {
        ArrayNode content = objectMapper.createArrayNode();

        // 同时兼容 imageUrls / images 两种前端输入命名，便于后续扩展多模态表单。
        appendImageItems(request.input(), content, "images");
        appendImageItems(request.input(), content, "imageUrls");
        appendAudioItems(request.input(), content, "audioUrls");

        StringBuilder text = new StringBuilder();
        if (request.userMessage() != null && !request.userMessage().isBlank()) {
            text.append(request.userMessage().trim());
        }
        if (request.backgroundContext() != null && !request.backgroundContext().isBlank()) {
            if (!text.isEmpty()) {
                text.append("\n\n");
            }
            text.append("背景上下文摘要：\n")
                    .append(request.backgroundContext().trim());
        }
        if (request.capabilityResult() != null && !request.capabilityResult().isNull()) {
            if (!text.isEmpty()) {
                text.append("\n\n");
            }
            text.append("工具或 Skill 执行结果上下文：\n")
                    .append(request.capabilityResult().toPrettyString());
        }

        if (!text.isEmpty()) {
            content.addObject()
                    .put("type", "text")
                    .put("text", text.toString());
        }

        if (content.isEmpty()) {
            return objectMapper.getNodeFactory().textNode(request.userMessage() == null ? "" : request.userMessage());
        }
        return content;
    }

    private void appendImageItems(JsonNode input, ArrayNode content, String fieldName) {
        if (input == null || !input.has(fieldName) || !input.get(fieldName).isArray()) {
            return;
        }
        for (JsonNode item : input.get(fieldName)) {
            if (item == null || item.isNull()) {
                continue;
            }
            String url = item.isTextual() ? item.asText() : item.path("url").asText("");
            if (url == null || url.isBlank()) {
                continue;
            }
            content.addObject()
                    .put("type", "image_url")
                    .putObject("image_url")
                    .put("url", url);
        }
    }

    private void appendAudioItems(JsonNode input, ArrayNode content, String fieldName) {
        if (input == null || !input.has(fieldName) || !input.get(fieldName).isArray()) {
            return;
        }
        for (JsonNode item : input.get(fieldName)) {
            if (item == null || item.isNull()) {
                continue;
            }

            if (item.isObject() && item.has("data")) {
                String data = item.path("data").asText("");
                String format = item.path("format").asText("");
                if (!data.isBlank() && !format.isBlank()) {
                    content.addObject()
                            .put("type", "input_audio")
                            .putObject("input_audio")
                            .put("data", data)
                            .put("format", format);
                }
                continue;
            }

            String value = item.isTextual() ? item.asText() : item.path("url").asText("");
            if (value == null || value.isBlank()) {
                continue;
            }

            AudioInput audioInput = parseAudioInput(value);
            ObjectNode audioNode = content.addObject()
                    .put("type", "input_audio")
                    .putObject("input_audio");
            if (audioInput.data() != null) {
                audioNode.put("data", audioInput.data());
                audioNode.put("format", audioInput.format());
            } else {
                audioNode.put("url", audioInput.url());
            }
        }
    }

    /**
     * 追加 few-shot 示例消息。
     * 这里沿用 OpenAI-compatible 的 messages 结构，便于前端直接透传 role/content。
     */
    private void appendExampleMessages(JsonNode examples, ArrayNode messages) {
        if (examples == null || !examples.isArray()) {
            return;
        }
        for (JsonNode item : examples) {
            if (item == null || !item.isObject()) {
                continue;
            }
            String role = item.path("role").asText("");
            JsonNode content = item.get("content");
            if (role.isBlank() || content == null || content.isNull()) {
                continue;
            }
            ObjectNode message = messages.addObject();
            message.put("role", role);
            message.set("content", content);
        }
    }

    private void copyOptional(JsonNode source, ObjectNode target, String fieldName) {
        if (source != null && source.has(fieldName) && !source.get(fieldName).isNull()) {
            target.set(fieldName, source.get(fieldName));
        }
    }

    private void copyAliasedOptional(JsonNode source, ObjectNode target, String sourceField, String targetField) {
        if (source != null && source.has(sourceField) && !source.get(sourceField).isNull()) {
            target.set(targetField, source.get(sourceField));
        }
    }

    /**
     * 合并额外的供应商扩展参数。
     * 该能力主要服务于试跑和联调场景，让前端可以按 JSON 透传一些
     * OpenAI-compatible 网关的自定义字段，而不需要每次都改后端字段模型。
     */
    private void mergeExtraBody(JsonNode source, ObjectNode target, String fieldName) {
        if (source == null || !source.has(fieldName) || source.get(fieldName) == null || !source.get(fieldName).isObject()) {
            return;
        }
        Iterator<Map.Entry<String, JsonNode>> iterator = source.get(fieldName).fields();
        while (iterator.hasNext()) {
            Map.Entry<String, JsonNode> entry = iterator.next();
            if (entry.getKey() == null || entry.getKey().isBlank()) {
                continue;
            }
            // 已有标准字段优先，避免扩展参数覆盖主链路关键负载。
            if (!target.has(entry.getKey())) {
                target.set(entry.getKey(), entry.getValue());
            }
        }
    }

    /**
     * 兼容音频 URL 与 data URL 两种输入形式。
     * 当前端直接上传本地音频文件时，会先转成 data URL，再由这里拆分为 data + format。
     */
    private AudioInput parseAudioInput(String value) {
        if (value == null || value.isBlank()) {
            return new AudioInput(null, null, null);
        }
        if (!value.startsWith("data:")) {
            return new AudioInput(value, null, null);
        }

        int commaIndex = value.indexOf(',');
        if (commaIndex < 0) {
            return new AudioInput(value, null, null);
        }

        String header = value.substring(5, commaIndex);
        String data = value.substring(commaIndex + 1);
        String mimeType = header.split(";")[0];
        String format = mimeType.contains("/") ? mimeType.substring(mimeType.indexOf('/') + 1) : mimeType;
        return new AudioInput(null, data, format);
    }

    /**
     * 音频输入标准化结果。
     * url 与 data 二选一，format 仅在 data 模式下生效。
     */
    private record AudioInput(String url, String data, String format) {
    }

    private ModelChatResponse parseResponse(ModelRuntimeProfile profile, JsonNode response) {
        // 按 OpenAI-compatible choices[0].message 结构读取第一条答案。
        if (response == null || !response.has("choices") || !response.get("choices").isArray() || response.get("choices").isEmpty()) {
            throw new ModelException(ModelErrorCode.MODEL_INVALID_RESPONSE,
                    messageService.get("model.error.invalidResponse"));
        }

        JsonNode choice = response.get("choices").get(0);
        String finishReason = choice.path("finish_reason").asText("");
        JsonNode message = choice.path("message");
        String content = extractMessageContent(message.path("content"));

        if (content == null || content.isBlank()) {
            content = messageService.get("model.error.emptyResponseFallback", profile.modelId());
        }

        return new ModelChatResponse(
                content,
                finishReason,
                profile.providerType(),
                profile.modelId(),
                response
        );
    }

    private String extractMessageContent(JsonNode content) {
        if (content == null || content.isNull()) {
            return "";
        }
        if (content.isTextual()) {
            return content.asText();
        }
        if (content.isArray()) {
            StringBuilder builder = new StringBuilder();
            for (JsonNode item : content) {
                String text = item.path("text").asText("");
                if (!text.isBlank()) {
                    if (!builder.isEmpty()) {
                        builder.append('\n');
                    }
                    builder.append(text);
                }
            }
            return builder.toString();
        }
        if (content.isObject()) {
            if (content.has("text")) {
                return content.path("text").asText("");
            }
            StringBuilder builder = new StringBuilder();
            Iterator<Map.Entry<String, JsonNode>> iterator = content.fields();
            while (iterator.hasNext()) {
                Map.Entry<String, JsonNode> entry = iterator.next();
                if (!builder.isEmpty()) {
                    builder.append('\n');
                }
                builder.append(entry.getKey()).append(": ").append(entry.getValue().asText());
            }
            return builder.toString();
        }
        return content.toString();
    }
}
