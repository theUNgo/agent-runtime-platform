package com.example.agentruntime.template;

import com.example.agentruntime.i18n.MessageService;
import com.example.agentruntime.persistence.entity.UserAccountEntity;
import com.example.agentruntime.persistence.entity.UserPreviewTemplateEntity;
import com.example.agentruntime.persistence.repository.UserAccountRepository;
import com.example.agentruntime.persistence.repository.UserPreviewTemplateRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 用户自定义试跑模板服务。
 * 当前用于沉淀个人调好的模型试跑模板，后续也可以继续扩展成团队模板中心。
 */
@Service
public class UserPreviewTemplateService {

    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {
    };

    private final UserPreviewTemplateRepository previewTemplateRepository;
    private final UserAccountRepository userAccountRepository;
    private final ObjectMapper objectMapper;
    private final MessageService messageService;

    public UserPreviewTemplateService(UserPreviewTemplateRepository previewTemplateRepository,
                                      UserAccountRepository userAccountRepository,
                                      ObjectMapper objectMapper,
                                      MessageService messageService) {
        this.previewTemplateRepository = previewTemplateRepository;
        this.userAccountRepository = userAccountRepository;
        this.objectMapper = objectMapper;
        this.messageService = messageService;
    }

    @Transactional(readOnly = true)
    public List<PreviewTemplateView> listForUser(Long userId) {
        UserAccountEntity user = userAccountRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException(messageService.get("auth.error.userNotFound")));
        return previewTemplateRepository.findByUserOrderByUpdatedAtDesc(user).stream()
                .map(this::toView)
                .toList();
    }

    @Transactional
    public PreviewTemplateView save(Long userId, PreviewTemplateUpsertRequest request) {
        UserAccountEntity user = userAccountRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException(messageService.get("auth.error.userNotFound")));
        validateRequest(request);

        UserPreviewTemplateEntity entity = previewTemplateRepository.findByUserAndTemplateKey(user, request.templateKey().trim())
                .orElseGet(UserPreviewTemplateEntity::new);
        entity.setUser(user);
        entity.setTemplateKey(request.templateKey().trim());
        entity.setLabel(request.label().trim());
        entity.setDescription(blankToDefault(request.description(), messageService.get("template.preview.description.default")));
        entity.setMessageText(blankToNull(request.message()));
        entity.setSystemPrompt(blankToNull(request.systemPrompt()));
        entity.setExamplesJson(blankToNull(request.examplesJson()));
        entity.setOutputGuide(blankToNull(request.outputGuide()));
        entity.setPayloadJson(blankToNull(request.payloadJson()));
        entity.setThinkingEnabled(request.thinkingEnabled());
        entity.setNotesJson(writeNotes(request.notes()));

        return toView(previewTemplateRepository.save(entity));
    }

    @Transactional
    public void delete(Long userId, String templateKey) {
        UserAccountEntity user = userAccountRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException(messageService.get("auth.error.userNotFound")));
        UserPreviewTemplateEntity entity = previewTemplateRepository.findByUserAndTemplateKey(user, templateKey)
                .orElseThrow(() -> new IllegalArgumentException(messageService.get("template.preview.notFound", templateKey)));
        previewTemplateRepository.delete(entity);
    }

    private void validateRequest(PreviewTemplateUpsertRequest request) {
        if (request == null || request.templateKey() == null || request.templateKey().isBlank()) {
            throw new IllegalArgumentException(messageService.get("template.preview.keyRequired"));
        }
        if (request.label() == null || request.label().isBlank()) {
            throw new IllegalArgumentException(messageService.get("template.preview.labelRequired"));
        }
    }

    private PreviewTemplateView toView(UserPreviewTemplateEntity entity) {
        return new PreviewTemplateView(
                entity.getTemplateKey(),
                entity.getLabel(),
                entity.getDescription(),
                messageService.get("template.preview.category.custom"),
                entity.getMessageText(),
                entity.getSystemPrompt(),
                entity.getExamplesJson(),
                entity.getOutputGuide(),
                entity.getPayloadJson(),
                entity.isThinkingEnabled(),
                readNotes(entity.getNotesJson()),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    private String writeNotes(List<String> notes) {
        if (notes == null || notes.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(notes);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(messageService.get("template.preview.notesSerializeFailed"));
        }
    }

    private List<String> readNotes(String notesJson) {
        if (notesJson == null || notesJson.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(notesJson, STRING_LIST);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(messageService.get("template.preview.notesDeserializeFailed"));
        }
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String blankToDefault(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value.trim();
    }
}
