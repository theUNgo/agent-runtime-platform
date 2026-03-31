package com.example.agentruntime.skill;

import com.example.agentruntime.AgentRuntimeProperties;
import com.example.agentruntime.capability.AgentCapability;
import com.example.agentruntime.capability.CapabilityDescriptor;
import com.example.agentruntime.capability.CapabilityProvider;
import com.example.agentruntime.capability.RiskLevel;
import com.example.agentruntime.i18n.MessageService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * 本地 Skill 提供者。
 * 负责扫描 skills 目录下的 skill.yaml，并将其转换成系统内部可执行能力。
 */
@Component
public class LocalSkillProvider implements CapabilityProvider {

    private final AgentRuntimeProperties properties;
    private final ObjectMapper objectMapper;
    private final MessageService messageService;
    private final WorkflowSkillExecutionSupport workflowSkillExecutionSupport;
    private final Map<String, Skill> skills = new LinkedHashMap<>();

    /**
     * 创建本地 Skill 提供者。
     * 统一显式注入 workflow 执行支持服务，避免 Spring 在双构造器场景下出现装配歧义。
     */
    @Autowired
    public LocalSkillProvider(AgentRuntimeProperties properties,
                              ObjectMapper objectMapper,
                              MessageService messageService,
                              WorkflowSkillExecutionSupport workflowSkillExecutionSupport) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.messageService = messageService;
        this.workflowSkillExecutionSupport = workflowSkillExecutionSupport;
    }

    @Override
    public String providerId() {
        return "local-skill-provider";
    }

    @Override
    public synchronized List<CapabilityDescriptor> discover() {
        skills.clear();
        Path root = Path.of(properties.skillsDir());
        if (!Files.exists(root)) {
            return List.of();
        }

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(root)) {
            for (Path skillDir : stream) {
                if (!Files.isDirectory(skillDir)) {
                    continue;
                }
                Path manifestPath = skillDir.resolve("skill.yaml");
                if (!Files.exists(manifestPath)) {
                    continue;
                }
                Skill skill = loadSkill(manifestPath);
                skills.put(skill.descriptor().id(), skill);
            }
        } catch (IOException exception) {
            throw new IllegalStateException(messageService.get("mcp.error.scanSkills", root), exception);
        }
        return skills.values().stream()
                .map(Skill::descriptor)
                .toList();
    }

    @Override
    public synchronized Optional<AgentCapability> resolve(String capabilityId) {
        return Optional.ofNullable(skills.get(capabilityId)).map(AgentCapability.class::cast);
    }

    private Skill loadSkill(Path manifestPath) throws IOException {
        Yaml yaml = new Yaml();
        try (InputStream inputStream = Files.newInputStream(manifestPath)) {
            @SuppressWarnings("unchecked")
            Map<String, Object> manifestMap = yaml.loadAs(inputStream, Map.class);
            Path skillDir = manifestPath.getParent();
            SkillManifest manifest = toManifest(manifestMap, skillDir, yaml);
            if (manifest == null || manifest.id() == null || manifest.id().isBlank()) {
                throw new IllegalStateException(messageService.get("mcp.error.skillMissingId", manifestPath));
            }
            if ("workflow".equalsIgnoreCase(manifest.type())) {
                return new ExecutableWorkflowSkill(manifest, objectMapper, messageService, workflowSkillExecutionSupport);
            }
            return new PromptSkill(manifest, objectMapper, messageService);
        }
    }

    private SkillManifest toManifest(Map<String, Object> manifestMap, Path skillDir, Yaml yaml) throws IOException {
        if (manifestMap == null) {
            return null;
        }

        Object riskRaw = manifestMap.get("riskLevel");
        RiskLevel riskLevel = riskRaw == null
                ? null
                : RiskLevel.valueOf(String.valueOf(riskRaw).toUpperCase(Locale.ROOT));

        @SuppressWarnings("unchecked")
        List<String> tags = manifestMap.get("tags") instanceof List<?>
                ? ((List<?>) manifestMap.get("tags")).stream().map(String::valueOf).toList()
                : List.of();

        String type = stringValue(manifestMap.get("type"));
        String entry = stringValue(manifestMap.get("entry"));
        List<SkillWorkflowStep> steps = parseWorkflowSteps(manifestMap.get("steps"));
        if ("workflow".equalsIgnoreCase(type) && steps.isEmpty() && entry != null && skillDir != null) {
            steps = loadWorkflowSteps(skillDir.resolve(entry), yaml);
        }

        return new SkillManifest(
                stringValue(manifestMap.get("id")),
                stringValue(manifestMap.get("name")),
                stringValue(manifestMap.get("version")),
                stringValue(manifestMap.get("description")),
                type,
                entry,
                stringValue(manifestMap.get("prompt")),
                riskLevel,
                tags,
                steps
        );
    }

    /**
     * 解析 workflow 步骤列表。
     * 当前支持在 skill.yaml 中内联 steps，或通过 entry 指向外部 workflow 文件。
     * branch / group 以及显式汇总节点配置也在这里一起读入，
     * 保证运行时可以直接做分支归类和范围汇总。
     */
    private List<SkillWorkflowStep> parseWorkflowSteps(Object rawSteps) {
        if (!(rawSteps instanceof List<?> rawList)) {
            return List.of();
        }
        List<SkillWorkflowStep> steps = new java.util.ArrayList<>();
        for (Object rawItem : rawList) {
            if (!(rawItem instanceof Map<?, ?> rawMap)) {
                continue;
            }
            steps.add(new SkillWorkflowStep(
                    stringValue(rawMap.get("id")),
                    stringValue(rawMap.get("title")),
                    stringValue(rawMap.get("instruction")),
                    stringValue(rawMap.get("outputKey")),
                    stringValue(rawMap.get("when")),
                    stringValue(rawMap.get("capabilityId")),
                    rawMap.get("capabilityInput"),
                    stringValue(rawMap.get("branch")),
                    stringValue(rawMap.get("group")),
                    stringValue(rawMap.get("summaryFromBranch")),
                    stringValue(rawMap.get("summaryFromGroup")),
                    booleanValue(rawMap.get("continueOnFailure"))
            ));
        }
        return List.copyOf(steps);
    }

    /**
     * 从 workflow 文件中读取步骤定义。
     * 第一版约定文件根节点下包含 steps 数组。
     */
    private List<SkillWorkflowStep> loadWorkflowSteps(Path workflowPath, Yaml yaml) throws IOException {
        if (!Files.exists(workflowPath)) {
            return List.of();
        }
        try (InputStream inputStream = Files.newInputStream(workflowPath)) {
            @SuppressWarnings("unchecked")
            Map<String, Object> workflowMap = yaml.loadAs(inputStream, Map.class);
            if (workflowMap == null) {
                return List.of();
            }
            return parseWorkflowSteps(workflowMap.get("steps"));
        }
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    /**
     * 解析 workflow 布尔类型配置。
     * 兼容 YAML 里的布尔值和字符串写法，避免清单作者因为大小写差异导致解析失败。
     */
    private Boolean booleanValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Boolean bool) {
            return bool;
        }
        return Boolean.parseBoolean(String.valueOf(value));
    }
}
