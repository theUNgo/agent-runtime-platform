package com.example.agentruntime.skill;

import com.example.agentruntime.builtin.BuiltinEchoCapability;
import com.example.agentruntime.capability.AgentCapability;
import com.example.agentruntime.capability.CapabilityContext;
import com.example.agentruntime.capability.CapabilityDescriptor;
import com.example.agentruntime.capability.CapabilityRegistry;
import com.example.agentruntime.i18n.MessageService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.context.support.StaticMessageSource;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkflowSkillBindingTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final MessageService messageService = messageService();

    @Test
    void shouldExecuteConditionalCapabilityBinding() {
        SkillManifest manifest = new SkillManifest(
                "binding-skill",
                "Binding Skill",
                "0.1.0",
                "Workflow with capability binding",
                "workflow",
                "workflows/demo.yaml",
                null,
                null,
                List.of("workflow", "binding"),
                List.of(
                        new SkillWorkflowStep(
                                "maybe-echo",
                                "条件回声",
                                "准备把输入文本发送给 echo。",
                                "echoResult",
                                "{{input.enableEcho}}",
                                "builtin:echo",
                                java.util.Map.of("text", "{{input.text}}"),
                                "triage",
                                "analysis",
                                null,
                                null,
                                false
                        ),
                        new SkillWorkflowStep(
                                "summarize",
                                "汇总结果",
                                "上一步输出：{{state.echoResult}}，是否成功：{{state.echoResultMeta.success}}",
                                "summary",
                                null,
                                null,
                                null,
                                "triage",
                                "analysis",
                                null,
                                null,
                                false
                        )
                )
        );

        ExecutableWorkflowSkill skill = new ExecutableWorkflowSkill(
                manifest,
                objectMapper,
                messageService,
                workflowExecutionSupport()
        );

        var result = skill.execute(
                new CapabilityContext("conversation-1", "请执行回声测试", ".", null),
                objectMapper.createObjectNode()
                        .put("enableEcho", true)
                        .put("text", "hello workflow")
        );

        assertEquals(2, result.output().path("steps").size());
        assertEquals("SUCCESS", result.output().path("steps").get(0).path("status").asText());
        assertEquals("builtin:echo", result.output().path("steps").get(0).path("capabilityId").asText());
        assertEquals("triage", result.output().path("steps").get(0).path("branch").asText());
        assertEquals("analysis", result.output().path("steps").get(0).path("group").asText());
        assertTrue(result.output().path("steps").get(0).path("capabilityResult").path("output").path("message").asText().contains("请执行回声测试"));
        assertTrue(result.output().path("state").has("echoResult"));
        assertEquals(2, result.output().path("workflowStats").path("totalSteps").asInt());
        assertEquals(2, result.output().path("workflowStats").path("executedSteps").asInt());
        assertEquals(1, result.output().path("workflowStats").path("capabilitySteps").asInt());
        assertEquals(1, result.output().path("workflowStats").path("branchCount").asInt());
        assertEquals(1, result.output().path("workflowStats").path("groupCount").asInt());
        assertEquals(1, result.output().path("workflowBranches").size());
        assertEquals("triage", result.output().path("workflowBranches").get(0).path("branch").asText());
        assertEquals("analysis", result.output().path("workflowBranches").get(0).path("groups").get(0).path("group").asText());
        assertEquals("SUCCESS", result.output().path("workflowBranchState").path("triage").path("status").asText());
        assertEquals("SUCCESS", result.output().path("workflowGroupState").path("analysis").path("status").asText());
        assertTrue(result.output().path("steps").get(1).path("renderedInstruction").asText().contains("true"));
        assertTrue(result.output().path("workflowBranchSummary").asText().contains("分支 triage"));
        assertTrue(result.output().path("workflowSummary").asText().contains("builtin:echo"));
    }

    @Test
    void shouldExposeBranchAndGroupStateForConditionalFallback() {
        SkillManifest manifest = new SkillManifest(
                "binding-skill-failure",
                "Binding Skill Failure",
                "0.1.0",
                "Workflow with failure continuation",
                "workflow",
                "workflows/demo.yaml",
                null,
                null,
                List.of("workflow", "binding"),
                List.of(
                        new SkillWorkflowStep(
                                "missing-capability",
                                "调用缺失能力",
                                "尝试调用一个不存在的能力",
                                "missingResult",
                                null,
                                "builtin:missing",
                                java.util.Map.of("text", "{{input.text}}"),
                                "fallback",
                                "primary",
                                null,
                                null,
                                true
                        ),
                        new SkillWorkflowStep(
                                "after-failure",
                                "失败后继续",
                                "分支状态：{{state.workflowBranchState.fallback.status}}，分组失败：{{state.workflowGroupState.primary.hasFailure}}",
                                "summary",
                                "{{state.workflowBranchState.fallback.hasFailure}}",
                                null,
                                null,
                                "fallback",
                                "secondary",
                                null,
                                null,
                                false
                        )
                )
        );

        ExecutableWorkflowSkill skill = new ExecutableWorkflowSkill(
                manifest,
                objectMapper,
                messageService,
                workflowExecutionSupport()
        );

        var result = skill.execute(
                new CapabilityContext("conversation-1", "测试失败后继续", ".", null),
                objectMapper.createObjectNode().put("text", "hello workflow")
        );

        assertTrue(!result.success());
        assertEquals("FAILED", result.output().path("steps").get(0).path("status").asText());
        assertTrue(result.output().path("steps").get(0).path("continuedAfterFailure").asBoolean());
        assertEquals("SUCCESS", result.output().path("steps").get(1).path("status").asText());
        assertEquals(1, result.output().path("workflowStats").path("failedSteps").asInt());
        assertEquals(1, result.output().path("workflowStats").path("continuedFailureSteps").asInt());
        assertEquals(1, result.output().path("workflowStats").path("branchCount").asInt());
        assertEquals(2, result.output().path("workflowStats").path("groupCount").asInt());
        assertEquals(2, result.output().path("workflowBranches").get(0).path("groups").size());
        assertEquals("DEGRADED", result.output().path("workflowBranchState").path("fallback").path("status").asText());
        assertTrue(result.output().path("workflowGroupState").path("primary").path("hasFailure").asBoolean());
        assertTrue(result.output().path("steps").get(1).path("renderedOutput").asText().contains("DEGRADED"));
        assertTrue(result.output().path("state").path("missingResultMeta").path("message").asText().contains("builtin:missing"));
    }

    @Test
    void shouldSupportExplicitAggregateStepsForBranchAndGroup() {
        SkillManifest manifest = new SkillManifest(
                "binding-skill-aggregate",
                "Binding Skill Aggregate",
                "0.1.0",
                "Workflow with explicit aggregate steps",
                "workflow",
                "workflows/demo.yaml",
                null,
                null,
                List.of("workflow", "aggregate"),
                List.of(
                        new SkillWorkflowStep(
                                "echo-primary",
                                "主分组回声",
                                "调用 echo 生成主分组结果",
                                "echoPrimary",
                                null,
                                "builtin:echo",
                                java.util.Map.of("text", "{{input.text}}"),
                                "triage",
                                "primary",
                                null,
                                null,
                                false
                        ),
                        new SkillWorkflowStep(
                                "summarize-branch",
                                "汇总分支",
                                "汇总 triage 分支的执行情况",
                                "branchSummary",
                                null,
                                null,
                                null,
                                "triage",
                                "decision",
                                "triage",
                                null,
                                false
                        ),
                        new SkillWorkflowStep(
                                "summarize-group",
                                "汇总分组",
                                "汇总 primary 分组的执行情况",
                                "groupSummary",
                                "{{state.branchSummary.scope.status}}",
                                null,
                                null,
                                "triage",
                                "decision",
                                null,
                                "primary",
                                false
                        )
                )
        );

        ExecutableWorkflowSkill skill = new ExecutableWorkflowSkill(
                manifest,
                objectMapper,
                messageService,
                workflowExecutionSupport()
        );

        var result = skill.execute(
                new CapabilityContext("conversation-1", "测试汇总节点", ".", null),
                objectMapper.createObjectNode().put("text", "hello aggregate")
        );

        assertTrue(result.success());
        assertEquals("SUCCESS", result.output().path("steps").get(1).path("status").asText());
        assertEquals("branch", result.output().path("steps").get(1).path("aggregateOutput").path("scopeType").asText());
        assertEquals("triage", result.output().path("steps").get(1).path("aggregateOutput").path("scopeKey").asText());
        assertEquals("group", result.output().path("steps").get(2).path("aggregateOutput").path("scopeType").asText());
        assertEquals("primary", result.output().path("steps").get(2).path("aggregateOutput").path("scopeKey").asText());
        assertEquals("SUCCESS", result.output().path("state").path("branchSummary").path("scope").path("status").asText());
        assertEquals("SUCCESS", result.output().path("state").path("groupSummary").path("scope").path("status").asText());
        assertTrue(result.output().path("steps").get(2).path("renderedWhen").asText().contains("SUCCESS"));
    }

    @Test
    void shouldRouteToDifferentGroupsBasedOnBranchAggregateStatus() {
        SkillManifest manifest = new SkillManifest(
                "binding-skill-route-by-aggregate",
                "Binding Skill Route By Aggregate",
                "0.1.0",
                "Workflow with aggregate-driven conditional routing",
                "workflow",
                "workflows/demo.yaml",
                null,
                null,
                List.of("workflow", "aggregate", "routing"),
                List.of(
                        new SkillWorkflowStep(
                                "missing-capability",
                                "制造分支降级",
                                "调用一个不存在的能力，让 triage 分支进入降级状态",
                                "missingResult",
                                null,
                                "builtin:missing",
                                java.util.Map.of("text", "{{input.text}}"),
                                "triage",
                                "analysis",
                                null,
                                null,
                                true
                        ),
                        new SkillWorkflowStep(
                                "summarize-branch",
                                "汇总分支状态",
                                "汇总 triage 分支的执行结果，供后续分组分流使用",
                                "branchSummary",
                                null,
                                null,
                                null,
                                "triage",
                                "decision",
                                "triage",
                                null,
                                false
                        ),
                        new SkillWorkflowStep(
                                "route-success",
                                "成功分组处理",
                                "如果分支成功则进入 success-path 分组",
                                "successPath",
                                "state.branchSummary.scope.status == SUCCESS",
                                null,
                                null,
                                "triage",
                                "success-path",
                                null,
                                null,
                                false
                        ),
                        new SkillWorkflowStep(
                                "route-degraded",
                                "降级分组处理",
                                "如果分支降级则进入 degraded-path 分组，当前状态：{{state.branchSummary.scope.status}}",
                                "degradedPath",
                                "state.branchSummary.scope.status == DEGRADED",
                                null,
                                null,
                                "triage",
                                "degraded-path",
                                null,
                                null,
                                false
                        )
                )
        );

        ExecutableWorkflowSkill skill = new ExecutableWorkflowSkill(
                manifest,
                objectMapper,
                messageService,
                workflowExecutionSupport()
        );

        var result = skill.execute(
                new CapabilityContext("conversation-1", "测试汇总驱动分流", ".", null),
                objectMapper.createObjectNode().put("text", "hello route")
        );

        assertTrue(!result.success());
        assertEquals("DEGRADED", result.output().path("state").path("branchSummary").path("scope").path("status").asText());
        assertEquals("SKIPPED", result.output().path("steps").get(2).path("status").asText());
        assertEquals("SUCCESS", result.output().path("steps").get(3).path("status").asText());
        assertEquals("success-path", result.output().path("steps").get(2).path("group").asText());
        assertEquals("degraded-path", result.output().path("steps").get(3).path("group").asText());
        assertEquals("SKIPPED", result.output().path("workflowGroupState").path("success-path").path("status").asText());
        assertEquals("SUCCESS", result.output().path("workflowGroupState").path("degraded-path").path("status").asText());
        assertEquals("state.branchSummary.scope.status == SUCCESS", result.output().path("steps").get(2).path("when").asText());
        assertEquals("state.branchSummary.scope.status == DEGRADED", result.output().path("steps").get(3).path("when").asText());
    }

    @Test
    void shouldSupportCompoundWhenExpressionsWithParentheses() {
        SkillManifest manifest = new SkillManifest(
                "binding-skill-compound-when",
                "Binding Skill Compound When",
                "0.1.0",
                "Workflow with compound when expression",
                "workflow",
                "workflows/demo.yaml",
                null,
                null,
                List.of("workflow", "aggregate", "compound"),
                List.of(
                        new SkillWorkflowStep(
                                "missing-capability",
                                "制造分析分组失败",
                                "调用一个不存在的能力，让 analysis 分组失败但继续执行",
                                "missingResult",
                                null,
                                "builtin:missing",
                                java.util.Map.of("text", "{{input.text}}"),
                                "triage",
                                "analysis",
                                null,
                                null,
                                true
                        ),
                        new SkillWorkflowStep(
                                "summarize-branch",
                                "汇总分支状态",
                                "汇总 triage 分支执行结果",
                                "branchSummary",
                                null,
                                null,
                                null,
                                "triage",
                                "decision",
                                "triage",
                                null,
                                false
                        ),
                        new SkillWorkflowStep(
                                "compound-route",
                                "组合条件分流",
                                "命中复杂条件后进入 compound-path 分组",
                                "compoundRoute",
                                "(state.branchSummary.scope.status == DEGRADED && state.workflowGroupState.analysis.hasFailure == true) || state.branchSummary.scope.status == SUCCESS",
                                null,
                                null,
                                "triage",
                                "compound-path",
                                null,
                                null,
                                false
                        ),
                        new SkillWorkflowStep(
                                "other-route",
                                "其他路径分流",
                                "未命中复杂条件时进入 other-path 分组",
                                "otherRoute",
                                "state.branchSummary.scope.status == SUCCESS && state.workflowGroupState.analysis.hasFailure != true",
                                null,
                                null,
                                "triage",
                                "other-path",
                                null,
                                null,
                                false
                        )
                )
        );

        ExecutableWorkflowSkill skill = new ExecutableWorkflowSkill(
                manifest,
                objectMapper,
                messageService,
                workflowExecutionSupport()
        );

        var result = skill.execute(
                new CapabilityContext("conversation-1", "测试组合条件表达式", ".", null),
                objectMapper.createObjectNode().put("text", "hello compound")
        );

        assertTrue(!result.success());
        assertEquals("DEGRADED", result.output().path("state").path("branchSummary").path("scope").path("status").asText());
        assertEquals("SUCCESS", result.output().path("steps").get(2).path("status").asText());
        assertEquals("SKIPPED", result.output().path("steps").get(3).path("status").asText());
        assertEquals("SUCCESS", result.output().path("workflowGroupState").path("compound-path").path("status").asText());
        assertEquals("SKIPPED", result.output().path("workflowGroupState").path("other-path").path("status").asText());
        assertTrue(result.output().path("steps").get(2).path("when").asText().contains("&&"));
        assertTrue(result.output().path("steps").get(2).path("when").asText().contains("||"));
    }

    @Test
    void shouldSupportNegationAndNumericComparisonInWhenExpression() {
        SkillManifest manifest = new SkillManifest(
                "binding-skill-negation-numeric",
                "Binding Skill Negation Numeric",
                "0.1.0",
                "Workflow with negation and numeric comparison",
                "workflow",
                "workflows/demo.yaml",
                null,
                null,
                List.of("workflow", "condition"),
                List.of(
                        new SkillWorkflowStep(
                                "high-score-route",
                                "高分路径",
                                "当评分高于等于 80 且不是关闭状态时进入高分路径",
                                "highScoreRoute",
                                "!(input.inputMeta.closed == true) && input.score >= 80",
                                null,
                                null,
                                "judge",
                                "high-score",
                                null,
                                null,
                                false
                        ),
                        new SkillWorkflowStep(
                                "fallback-route",
                                "兜底路径",
                                "未命中高分路径时进入兜底路径",
                                "fallbackRoute",
                                "input.score < 80 || input.inputMeta.closed == true",
                                null,
                                null,
                                "judge",
                                "fallback",
                                null,
                                null,
                                false
                        )
                )
        );

        ExecutableWorkflowSkill skill = new ExecutableWorkflowSkill(
                manifest,
                objectMapper,
                messageService,
                workflowExecutionSupport()
        );

        var result = skill.execute(
                new CapabilityContext("conversation-1", "测试取反和数值比较", ".", null),
                objectMapper.createObjectNode()
                        .put("score", 92)
                        .set("inputMeta", objectMapper.createObjectNode().put("closed", false))
        );

        assertTrue(result.success());
        assertEquals("SUCCESS", result.output().path("steps").get(0).path("status").asText());
        assertEquals("SKIPPED", result.output().path("steps").get(1).path("status").asText());
        assertEquals("SUCCESS", result.output().path("workflowGroupState").path("high-score").path("status").asText());
        assertEquals("SKIPPED", result.output().path("workflowGroupState").path("fallback").path("status").asText());
        assertTrue(result.output().path("steps").get(0).path("when").asText().contains(">="));
        assertTrue(result.output().path("steps").get(0).path("when").asText().contains("!"));
    }

    @Test
    void shouldSupportCollectionMembershipInWhenExpression() {
        SkillManifest manifest = new SkillManifest(
                "binding-skill-collection-in",
                "Binding Skill Collection In",
                "0.1.0",
                "Workflow with collection membership",
                "workflow",
                "workflows/demo.yaml",
                null,
                null,
                List.of("workflow", "condition", "collection"),
                List.of(
                        new SkillWorkflowStep(
                                "allowed-status-route",
                                "允许状态路径",
                                "状态在允许集合内时进入 allow-path",
                                "allowedStatusRoute",
                                "input.status in [SUCCESS, DEGRADED, '{{input.allowedOverride}}']",
                                null,
                                null,
                                "judge",
                                "allow-path",
                                null,
                                null,
                                false
                        ),
                        new SkillWorkflowStep(
                                "blocked-status-route",
                                "阻断状态路径",
                                "不在允许集合时进入 blocked-path",
                                "blockedStatusRoute",
                                "!(input.status in [SUCCESS, DEGRADED, '{{input.allowedOverride}}'])",
                                null,
                                null,
                                "judge",
                                "blocked-path",
                                null,
                                null,
                                false
                        )
                )
        );

        ExecutableWorkflowSkill skill = new ExecutableWorkflowSkill(
                manifest,
                objectMapper,
                messageService,
                workflowExecutionSupport()
        );

        var result = skill.execute(
                new CapabilityContext("conversation-1", "测试集合判断", ".", null),
                objectMapper.createObjectNode()
                        .put("status", "DEGRADED")
                        .put("allowedOverride", "PENDING")
        );

        assertTrue(result.success());
        assertEquals("SUCCESS", result.output().path("steps").get(0).path("status").asText());
        assertEquals("SKIPPED", result.output().path("steps").get(1).path("status").asText());
        assertEquals("SUCCESS", result.output().path("workflowGroupState").path("allow-path").path("status").asText());
        assertEquals("SKIPPED", result.output().path("workflowGroupState").path("blocked-path").path("status").asText());
        assertTrue(result.output().path("steps").get(0).path("when").asText().contains(" in "));
    }

    private WorkflowSkillExecutionSupport workflowExecutionSupport() {
        AgentCapability echoCapability = new BuiltinEchoCapability(objectMapper, messageService);
        CapabilityRegistry registry = new CapabilityRegistry() {
            @Override
            public void refresh() {
            }

            @Override
            public List<CapabilityDescriptor> listAll() {
                return List.of(echoCapability.descriptor());
            }

            @Override
            public List<CapabilityDescriptor> search(String query) {
                return listAll();
            }

            @Override
            public Optional<AgentCapability> get(String capabilityId) {
                return echoCapability.descriptor().id().equals(capabilityId)
                        ? Optional.of(echoCapability)
                        : Optional.empty();
            }
        };
        StaticListableBeanFactory beanFactory = new StaticListableBeanFactory();
        beanFactory.addBean("capabilityRegistry", registry);
        return new WorkflowSkillExecutionSupport(beanFactory.getBeanProvider(CapabilityRegistry.class), objectMapper, messageService);
    }

    private MessageService messageService() {
        StaticMessageSource source = new StaticMessageSource();
        source.addMessage("capability.skill.workflow.success", Locale.ENGLISH, "workflow skill executed");
        source.addMessage("capability.builtin.echo.name", Locale.ENGLISH, "Echo");
        source.addMessage("capability.builtin.echo.description", Locale.ENGLISH, "Echo capability");
        source.addMessage("capability.builtin.echo.success", Locale.ENGLISH, "echo success");
        source.addMessage("skill.workflow.error.registryUnavailable", Locale.ENGLISH, "registry unavailable");
        source.addMessage("skill.workflow.error.capabilityNotFound", Locale.ENGLISH, "capability missing: {0}");
        source.addMessage("skill.workflow.error.selfInvocation", Locale.ENGLISH, "self invocation: {0}");
        source.addMessage("skill.workflow.error.executionFailed", Locale.ENGLISH, "workflow execution failed");
        source.addMessage("capability.skill.workflow.success", Locale.SIMPLIFIED_CHINESE, "workflow skill executed");
        source.addMessage("capability.builtin.echo.name", Locale.SIMPLIFIED_CHINESE, "Echo");
        source.addMessage("capability.builtin.echo.description", Locale.SIMPLIFIED_CHINESE, "Echo capability");
        source.addMessage("capability.builtin.echo.success", Locale.SIMPLIFIED_CHINESE, "echo success");
        source.addMessage("skill.workflow.error.registryUnavailable", Locale.SIMPLIFIED_CHINESE, "registry unavailable");
        source.addMessage("skill.workflow.error.capabilityNotFound", Locale.SIMPLIFIED_CHINESE, "capability missing: {0}");
        source.addMessage("skill.workflow.error.selfInvocation", Locale.SIMPLIFIED_CHINESE, "self invocation: {0}");
        source.addMessage("skill.workflow.error.executionFailed", Locale.SIMPLIFIED_CHINESE, "workflow execution failed");
        return new MessageService(source);
    }
}
