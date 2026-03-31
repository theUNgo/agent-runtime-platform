package com.example.agentruntime.skill;

import com.example.agentruntime.AgentRuntimeProperties;
import com.example.agentruntime.auth.UserRole;
import com.example.agentruntime.capability.CapabilityContext;
import com.example.agentruntime.capability.CapabilityDescriptor;
import com.example.agentruntime.capability.CapabilityResult;
import com.example.agentruntime.capability.CapabilityRegistry;
import com.example.agentruntime.i18n.MessageService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.context.support.StaticMessageSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalSkillProviderTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @TempDir
    Path tempDir;

    @Test
    void shouldDiscoverWorkflowSkillFromEntryFile() throws Exception {
        Path skillDir = tempDir.resolve("triage-skill");
        Files.createDirectories(skillDir.resolve("workflows"));
        Files.writeString(skillDir.resolve("skill.yaml"), """
                id: triage-skill
                name: Triage Skill
                version: 0.1.0
                description: Demo workflow skill
                type: workflow
                entry: workflows/flow.yaml
                riskLevel: LOW
                tags:
                  - triage
                  - workflow
                """);
        Files.writeString(skillDir.resolve("workflows").resolve("flow.yaml"), """
                steps:
                  - id: analyze
                    title: 分析问题
                    instruction: |
                      先分析用户消息 {{userMessage}}。
                    outputKey: analysis
                    branch: triage
                    group: intake
                  - id: inspect
                    title: 检查输入
                    instruction: |
                      结合输入 {{input.diff}} 给出下一步建议。
                    outputKey: nextStep
                    branch: triage
                    group: intake
                """);

        LocalSkillProvider provider = new LocalSkillProvider(
                properties(tempDir),
                objectMapper,
                messageService(),
                workflowExecutionSupport()
        );

        List<CapabilityDescriptor> capabilities = provider.discover();
        assertEquals(1, capabilities.size());
        assertEquals("skill:triage-skill", capabilities.getFirst().id());

        Skill skill = (Skill) provider.resolve("skill:triage-skill").orElseThrow();
        CapabilityResult result = skill.execute(
                new CapabilityContext("conversation-1", "请分析这个问题", ".", null),
                objectMapper.createObjectNode().put("diff", "demo diff")
        );

        assertTrue(result.output().path("steps").isArray());
        assertEquals(2, result.output().path("steps").size());
        assertTrue(result.output().path("workflowSummary").asText().contains("分析问题"));
        assertTrue(result.output().path("steps").get(1).path("renderedInstruction").asText().contains("demo diff"));
        assertEquals("triage", result.output().path("workflowBranches").get(0).path("branch").asText());
        assertEquals("intake", result.output().path("workflowBranches").get(0).path("groups").get(0).path("group").asText());
    }

    private AgentRuntimeProperties properties(Path skillsDir) {
        return new AgentRuntimeProperties(
                skillsDir.toString(),
                ".",
                new AgentRuntimeProperties.AuthProperties(true, Duration.ofDays(30), "admin", "test-bootstrap-password", "Platform Admin", UserRole.ADMIN),
                new AgentRuntimeProperties.McpProperties(List.of()),
                new AgentRuntimeProperties.SecurityProperties(
                        new AgentRuntimeProperties.CryptoProperties("ChangeThisDevelopmentCryptoSecret-32CharsMin")
                )
        );
    }

    private MessageService messageService() {
        StaticMessageSource source = new StaticMessageSource();
        source.addMessage("mcp.error.scanSkills", Locale.ENGLISH, "scan skills failed: {0}");
        source.addMessage("mcp.error.skillMissingId", Locale.ENGLISH, "skill manifest missing id: {0}");
        source.addMessage("capability.skill.prompt.success", Locale.ENGLISH, "prompt skill executed");
        source.addMessage("capability.skill.workflow.success", Locale.ENGLISH, "workflow skill executed");
        source.addMessage("skill.workflow.error.executionFailed", Locale.ENGLISH, "workflow execution failed");
        source.addMessage("mcp.error.scanSkills", Locale.SIMPLIFIED_CHINESE, "scan skills failed: {0}");
        source.addMessage("mcp.error.skillMissingId", Locale.SIMPLIFIED_CHINESE, "skill manifest missing id: {0}");
        source.addMessage("capability.skill.prompt.success", Locale.SIMPLIFIED_CHINESE, "prompt skill executed");
        source.addMessage("capability.skill.workflow.success", Locale.SIMPLIFIED_CHINESE, "workflow skill executed");
        source.addMessage("skill.workflow.error.executionFailed", Locale.SIMPLIFIED_CHINESE, "workflow execution failed");
        return new MessageService(source);
    }

    /**
     * 为本测试创建一个空的能力注册中心。
     * 当前用例只验证 workflow 文件装载与模板渲染，不需要真正绑定外部能力。
     */
    private WorkflowSkillExecutionSupport workflowExecutionSupport() {
        CapabilityRegistry registry = new CapabilityRegistry() {
            @Override
            public void refresh() {
            }

            @Override
            public List<CapabilityDescriptor> listAll() {
                return List.of();
            }

            @Override
            public List<CapabilityDescriptor> search(String query) {
                return List.of();
            }

            @Override
            public java.util.Optional<com.example.agentruntime.capability.AgentCapability> get(String capabilityId) {
                return java.util.Optional.empty();
            }
        };
        StaticListableBeanFactory beanFactory = new StaticListableBeanFactory();
        beanFactory.addBean("capabilityRegistry", registry);
        return new WorkflowSkillExecutionSupport(beanFactory.getBeanProvider(CapabilityRegistry.class), objectMapper, messageService());
    }
}
