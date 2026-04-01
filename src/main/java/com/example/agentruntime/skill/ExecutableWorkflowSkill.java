package com.example.agentruntime.skill;

import com.example.agentruntime.capability.CapabilityContext;
import com.example.agentruntime.capability.CapabilityDescriptor;
import com.example.agentruntime.capability.CapabilityMetadata;
import com.example.agentruntime.capability.CapabilityResult;
import com.example.agentruntime.capability.CapabilityType;
import com.example.agentruntime.i18n.MessageService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 支持条件分支与能力绑定的 Workflow Skill。
 * 这是当前 workflow skill 的主实现，负责执行步骤、保存中间状态，
 * 并在结果中输出按 branch / group 归类后的结构化摘要。
 */
public class ExecutableWorkflowSkill implements Skill {

    private final SkillManifest manifest;
    private final ObjectMapper objectMapper;
    private final MessageService messageService;
    private final WorkflowSkillExecutionSupport executionSupport;

    public ExecutableWorkflowSkill(SkillManifest manifest,
                                   ObjectMapper objectMapper,
                                   MessageService messageService,
                                   WorkflowSkillExecutionSupport executionSupport) {
        this.manifest = manifest;
        this.objectMapper = objectMapper;
        this.messageService = messageService;
        this.executionSupport = executionSupport;
    }

    @Override
    public SkillManifest manifest() {
        return manifest;
    }

    @Override
    public CapabilityDescriptor descriptor() {
        return new CapabilityDescriptor(
                "skill:" + manifest.id(),
                manifest.name(),
                CapabilityType.SKILL,
                new CapabilityMetadata(
                        "local-skill",
                        manifest.version(),
                        manifest.description(),
                        manifest.riskLevel(),
                        manifest.tags()),
                null,
                null
        );
    }

    @Override
    public CapabilityResult execute(CapabilityContext context, JsonNode input) {
        ObjectNode result = objectMapper.createObjectNode();
        result.put("skillId", manifest.id());
        result.put("skillType", manifest.type());
        result.put("workflowEntry", manifest.entry() == null ? "" : manifest.entry());
        result.put("userMessage", context.userMessage());
        result.set("input", input == null ? objectMapper.createObjectNode() : input);

        ObjectNode workflowState = objectMapper.createObjectNode();
        workflowState.putObject("workflowBranchState");
        workflowState.putObject("workflowGroupState");
        ArrayNode steps = result.putArray("steps");
        List<String> renderedSummaries = new ArrayList<>();
        ArrayNode executedStepIds = objectMapper.createArrayNode();
        ArrayNode skippedStepIds = objectMapper.createArrayNode();
        ArrayNode failedStepIds = objectMapper.createArrayNode();
        ArrayNode capabilityStepIds = objectMapper.createArrayNode();
        ArrayNode continuedFailureStepIds = objectMapper.createArrayNode();
        boolean workflowSuccess = true;

        for (SkillWorkflowStep step : manifest.steps()) {
            String renderedInstruction = executionSupport.renderTemplate(step.instruction(), context, input, workflowState);
            boolean shouldExecute = executionSupport.shouldExecute(step, context, input, workflowState);
            ObjectNode stepNode = steps.addObject();
            stepNode.put("id", step.id());
            stepNode.put("title", step.title());
            stepNode.put("instruction", step.instruction());
            stepNode.put("renderedInstruction", renderedInstruction);
            stepNode.put("outputKey", step.outputKey());
            stepNode.put("executed", shouldExecute);
            stepNode.put("continueOnFailure", step.continueOnFailure());
            stepNode.put("branch", normalizeBranchName(step.branch()));
            stepNode.put("group", normalizeGroupName(step.group(), step.branch()));
            if (step.summaryFromBranch() != null) {
                stepNode.put("summaryFromBranch", step.summaryFromBranch());
            }
            if (step.summaryFromGroup() != null) {
                stepNode.put("summaryFromGroup", step.summaryFromGroup());
            }

            if (step.when() != null) {
                stepNode.put("when", step.when());
                stepNode.put("renderedWhen", executionSupport.renderTemplate(step.when(), context, input, workflowState));
            }
            if (step.documentDocId() != null) {
                stepNode.put("documentDocId", executionSupport.renderTemplate(step.documentDocId(), context, input, workflowState));
            }
            if (step.documentDetailWhen() != null) {
                stepNode.put("documentDetailWhen", step.documentDetailWhen());
                stepNode.put("renderedDocumentDetailWhen",
                        executionSupport.renderTemplate(step.documentDetailWhen(), context, input, workflowState));
            }

            if (!shouldExecute) {
                stepNode.put("status", "SKIPPED");
                skippedStepIds.add(step.id());
                executionSupport.rememberStepMeta(
                        workflowState,
                        step,
                        executionSupport.buildStepMeta(step, null, "SKIPPED")
                );
                updateBranchAndGroupState(workflowState, stepNode);
                renderedSummaries.add(step.title() + "：跳过");
                continue;
            }

            if (step.summaryFromBranch() != null || step.summaryFromGroup() != null) {
                executedStepIds.add(step.id());
                ObjectNode aggregateOutput = buildAggregateOutput(step, steps);
                stepNode.put("status", "SUCCESS");
                stepNode.set("aggregateOutput", aggregateOutput);
                stepNode.put("renderedOutput", aggregateOutput.path("summary").asText(""));
                executionSupport.rememberStepOutput(workflowState, step, aggregateOutput);
                executionSupport.rememberStepMeta(
                        workflowState,
                        step,
                        executionSupport.buildStepMeta(step, null, "SUCCESS")
                );
                updateBranchAndGroupState(workflowState, stepNode);
                renderedSummaries.add(step.title() + "：" + aggregateOutput.path("summary").asText(""));
                continue;
            }

            if (step.documentDocId() != null) {
                executedStepIds.add(step.id());
                CapabilityResult documentResult = invokeDocumentStepSafely(context, input, workflowState, step);
                String status = documentResult.success() ? "SUCCESS" : "FAILED";
                stepNode.put("status", status);
                stepNode.set("documentResult", objectMapper.valueToTree(documentResult));
                if (documentResult.output() != null) {
                    String summaryPreview = documentResult.output().path("summary").path("content").asText("");
                    stepNode.put("renderedOutput", summaryPreview);
                }
                executionSupport.rememberStepOutput(
                        workflowState,
                        step,
                        documentResult.output() == null ? objectMapper.nullNode() : documentResult.output()
                );
                executionSupport.rememberStepMeta(
                        workflowState,
                        step,
                        executionSupport.buildStepMeta(step, documentResult, status)
                );
                if (documentResult.success()) {
                    updateBranchAndGroupState(workflowState, stepNode);
                    renderedSummaries.add(step.title() + "：已读取能力文档 " + stepNode.path("documentDocId").asText(""));
                    continue;
                }

                workflowSuccess = false;
                failedStepIds.add(step.id());
                if (step.continueOnFailure()) {
                    stepNode.put("continuedAfterFailure", true);
                    continuedFailureStepIds.add(step.id());
                    updateBranchAndGroupState(workflowState, stepNode);
                    renderedSummaries.add(step.title() + "：能力文档读取失败，但流程继续");
                    continue;
                }

                stepNode.put("haltedWorkflow", true);
                result.put("haltedAtStepId", step.id());
                result.put("haltedReason", documentResult.message() == null ? "" : documentResult.message());
                updateBranchAndGroupState(workflowState, stepNode);
                renderedSummaries.add(step.title() + "：能力文档读取失败，流程中断");
                break;
            }

            if (step.capabilityId() != null) {
                executedStepIds.add(step.id());
                capabilityStepIds.add(step.id());
                JsonNode capabilityInput = executionSupport.renderCapabilityInput(step.capabilityInput(), context, input, workflowState);
                CapabilityResult capabilityResult = invokeCapabilitySafely(context, step, capabilityInput);
                String status = capabilityResult.success() ? "SUCCESS" : "FAILED";
                stepNode.put("status", status);
                stepNode.put("capabilityId", step.capabilityId());
                stepNode.set("capabilityInput", capabilityInput);
                stepNode.set("capabilityResult", objectMapper.valueToTree(capabilityResult));
                executionSupport.rememberStepOutput(
                        workflowState,
                        step,
                        capabilityResult.output() == null ? objectMapper.nullNode() : capabilityResult.output()
                );
                executionSupport.rememberStepMeta(
                        workflowState,
                        step,
                        executionSupport.buildStepMeta(step, capabilityResult, status)
                );
                if (capabilityResult.success()) {
                    updateBranchAndGroupState(workflowState, stepNode);
                    renderedSummaries.add(step.title() + "：调用 " + step.capabilityId() + " 完成");
                    continue;
                }

                workflowSuccess = false;
                failedStepIds.add(step.id());
                if (step.continueOnFailure()) {
                    stepNode.put("continuedAfterFailure", true);
                    continuedFailureStepIds.add(step.id());
                    updateBranchAndGroupState(workflowState, stepNode);
                    renderedSummaries.add(step.title() + "：调用 " + step.capabilityId() + " 失败，但流程继续");
                    continue;
                }

                stepNode.put("haltedWorkflow", true);
                result.put("haltedAtStepId", step.id());
                result.put("haltedReason", capabilityResult.message() == null ? "" : capabilityResult.message());
                updateBranchAndGroupState(workflowState, stepNode);
                renderedSummaries.add(step.title() + "：调用 " + step.capabilityId() + " 失败，流程中断");
                break;
            }

            executedStepIds.add(step.id());
            stepNode.put("status", "SUCCESS");
            stepNode.put("renderedOutput", renderedInstruction);
            executionSupport.rememberStepOutput(
                    workflowState,
                    step,
                    objectMapper.getNodeFactory().textNode(renderedInstruction)
            );
            executionSupport.rememberStepMeta(
                    workflowState,
                    step,
                    executionSupport.buildStepMeta(step, null, "SUCCESS")
            );
            updateBranchAndGroupState(workflowState, stepNode);
            renderedSummaries.add(step.title() + "：" + renderedInstruction);
        }

        ArrayNode workflowBranches = buildWorkflowBranches(steps);
        result.set("state", workflowState);
        result.set("workflowBranches", workflowBranches);
        result.set("workflowBranchState", workflowState.path("workflowBranchState").deepCopy());
        result.set("workflowGroupState", workflowState.path("workflowGroupState").deepCopy());
        result.set("workflowStats", buildWorkflowStats(
                steps,
                executedStepIds,
                skippedStepIds,
                failedStepIds,
                capabilityStepIds,
                continuedFailureStepIds,
                workflowBranches,
                result.path("haltedAtStepId").asText("")
        ));
        result.put("workflowBranchSummary", buildWorkflowBranchSummary(
                executedStepIds,
                skippedStepIds,
                failedStepIds,
                continuedFailureStepIds,
                workflowBranches,
                result.path("haltedAtStepId").asText("")
        ));
        result.put("success", workflowSuccess);
        result.put("workflowSummary", String.join("\n", renderedSummaries));
        if (workflowSuccess) {
            return CapabilityResult.success(messageService.get("capability.skill.workflow.success"), result);
        }
        return new CapabilityResult(false, messageService.get("skill.workflow.error.executionFailed"), result);
    }

    /**
     * 安全调用步骤绑定能力。
     * 如果底层能力直接抛异常，这里会转换成统一的失败结果，避免 workflow 直接崩掉。
     */
    private CapabilityResult invokeCapabilitySafely(CapabilityContext context, SkillWorkflowStep step, JsonNode capabilityInput) {
        try {
            return executionSupport.invokeCapability(
                    descriptor().id(),
                    step,
                    context,
                    capabilityInput
            );
        } catch (RuntimeException exception) {
            return new CapabilityResult(false, exception.getMessage(), objectMapper.nullNode());
        }
    }

    /**
     * 安全执行文档读取步骤。
     * 文档读取失败时统一转成失败结果，便于 workflow 继续沿用现有的失败继续 / 中断机制。
     */
    private CapabilityResult invokeDocumentStepSafely(CapabilityContext context,
                                                      JsonNode input,
                                                      ObjectNode workflowState,
                                                      SkillWorkflowStep step) {
        try {
            return executionSupport.readCapabilityDocumentStep(step, context, input, workflowState);
        } catch (RuntimeException exception) {
            return new CapabilityResult(false, exception.getMessage(), objectMapper.nullNode());
        }
    }

    /**
     * 生成 workflow 的结构化统计结果。
     * 这里额外加入 branchCount / groupCount，方便后续前端和压缩层优先消费分支级摘要。
     */
    private ObjectNode buildWorkflowStats(ArrayNode steps,
                                          ArrayNode executedStepIds,
                                          ArrayNode skippedStepIds,
                                          ArrayNode failedStepIds,
                                          ArrayNode capabilityStepIds,
                                          ArrayNode continuedFailureStepIds,
                                          ArrayNode workflowBranches,
                                          String haltedAtStepId) {
        ObjectNode stats = objectMapper.createObjectNode();
        stats.put("totalSteps", steps.size());
        stats.put("executedSteps", executedStepIds.size());
        stats.put("skippedSteps", skippedStepIds.size());
        stats.put("failedSteps", failedStepIds.size());
        stats.put("capabilitySteps", capabilityStepIds.size());
        stats.put("continuedFailureSteps", continuedFailureStepIds.size());
        stats.put("branchCount", workflowBranches.size());
        stats.put("groupCount", countWorkflowGroups(workflowBranches));
        stats.put("halted", haltedAtStepId != null && !haltedAtStepId.isBlank());
        stats.put("haltedAtStepId", haltedAtStepId == null ? "" : haltedAtStepId);
        stats.set("executedStepIds", executedStepIds);
        stats.set("skippedStepIds", skippedStepIds);
        stats.set("failedStepIds", failedStepIds);
        stats.set("capabilityStepIds", capabilityStepIds);
        stats.set("continuedFailureStepIds", continuedFailureStepIds);
        return stats;
    }

    /**
     * 生成 workflow 分支摘要文本。
     * 除了总量，还会附带每个 branch 的步骤统计，便于快速判断执行路径。
     */
    private String buildWorkflowBranchSummary(ArrayNode executedStepIds,
                                              ArrayNode skippedStepIds,
                                              ArrayNode failedStepIds,
                                              ArrayNode continuedFailureStepIds,
                                              ArrayNode workflowBranches,
                                              String haltedAtStepId) {
        List<String> lines = new ArrayList<>();
        lines.add("Workflow 分支摘要：");
        lines.add("- 已执行步骤数：" + executedStepIds.size());
        lines.add("- 已跳过步骤数：" + skippedStepIds.size());
        lines.add("- 失败步骤数：" + failedStepIds.size());
        lines.add("- 失败后继续步骤数：" + continuedFailureStepIds.size());
        lines.add("- 分支数：" + workflowBranches.size());
        lines.add("- 分组数：" + countWorkflowGroups(workflowBranches));
        for (JsonNode branchNode : workflowBranches) {
            lines.add("- 分支 " + branchNode.path("branch").asText("default")
                    + "：步骤 " + branchNode.path("totalSteps").asInt()
                    + "，执行 " + branchNode.path("executedSteps").asInt()
                    + "，失败 " + branchNode.path("failedSteps").asInt());
        }
        if (haltedAtStepId != null && !haltedAtStepId.isBlank()) {
            lines.add("- 流程中断于步骤：" + haltedAtStepId);
        }
        return String.join("\n", lines);
    }

    /**
     * 按 branch / group 归并 workflow 步骤。
     * 未显式声明 branch / group 的步骤会自动回退到 default 分支，保证输出结构稳定。
     */
    private ArrayNode buildWorkflowBranches(ArrayNode steps) {
        Map<String, BranchAggregate> aggregates = new LinkedHashMap<>();
        for (JsonNode stepNode : steps) {
            String branchName = normalizeBranchName(stepNode.path("branch").asText(""));
            String groupName = normalizeGroupName(stepNode.path("group").asText(""), branchName);
            BranchAggregate aggregate = aggregates.computeIfAbsent(branchName, BranchAggregate::new);
            aggregate.accept(stepNode, groupName);
        }

        ArrayNode workflowBranches = objectMapper.createArrayNode();
        for (BranchAggregate aggregate : aggregates.values()) {
            workflowBranches.add(aggregate.toNode(objectMapper));
        }
        return workflowBranches;
    }

    private int countWorkflowGroups(ArrayNode workflowBranches) {
        int count = 0;
        for (JsonNode branchNode : workflowBranches) {
            count += branchNode.path("groups").size();
        }
        return count;
    }

    private String normalizeBranchName(String branch) {
        return branch == null || branch.isBlank() ? "default" : branch;
    }

    private String normalizeGroupName(String group, String branch) {
        return group == null || group.isBlank() ? normalizeBranchName(branch) : group;
    }

    /**
     * 为显式汇总节点生成结构化输出。
     * 汇总步骤不调用外部能力，而是直接把当前 branch 或 group 的执行状态收口成可复用结果，
     * 方便后续步骤按 outputKey 继续引用。
     */
    private ObjectNode buildAggregateOutput(SkillWorkflowStep step, ArrayNode steps) {
        String branchKey = normalizeScopeKey(step.summaryFromBranch());
        String groupKey = normalizeScopeKey(step.summaryFromGroup());
        ObjectNode aggregate = objectMapper.createObjectNode();
        aggregate.put("scopeType", branchKey != null ? "branch" : "group");
        aggregate.put("scopeKey", branchKey != null ? branchKey : groupKey == null ? "default" : groupKey);

        if (branchKey != null) {
            ObjectNode branchNode = findBranchNode(steps, branchKey);
            aggregate.set("scope", branchNode);
            aggregate.put("summary", buildAggregateSummaryText("分支", branchKey, branchNode));
            return aggregate;
        }

        ObjectNode groupNode = findGroupNode(steps, groupKey == null ? "default" : groupKey);
        aggregate.set("scope", groupNode);
        aggregate.put("summary", buildAggregateSummaryText("分组", groupKey == null ? "default" : groupKey, groupNode));
        return aggregate;
    }

    private String normalizeScopeKey(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private ObjectNode findBranchNode(ArrayNode steps, String branchKey) {
        String normalizedBranch = normalizeBranchName(branchKey);
        ObjectNode branchNode = objectMapper.createObjectNode();
        branchNode.put("branch", normalizedBranch);
        branchNode.put("totalSteps", 0);
        branchNode.put("executedSteps", 0);
        branchNode.put("skippedSteps", 0);
        branchNode.put("failedSteps", 0);
        branchNode.put("continuedFailureSteps", 0);
        branchNode.put("halted", false);
        branchNode.put("status", "PENDING");
        ArrayNode stepIds = branchNode.putArray("stepIds");
        ArrayNode groups = branchNode.putArray("groups");
        Map<String, ObjectNode> groupNodes = new LinkedHashMap<>();

        for (JsonNode stepNode : steps) {
            if (normalizedBranch.equals(stepNode.path("branch").asText("")) && stepNode.has("status")) {
                accumulateScopeNode(branchNode, stepNode, stepIds);
                String groupKey = stepNode.path("group").asText(normalizedBranch);
                ObjectNode groupNode = groupNodes.computeIfAbsent(groupKey, key -> {
                    ObjectNode created = objectMapper.createObjectNode();
                    created.put("group", key);
                    created.put("totalSteps", 0);
                    created.put("executedSteps", 0);
                    created.put("skippedSteps", 0);
                    created.put("failedSteps", 0);
                    created.put("continuedFailureSteps", 0);
                    created.put("halted", false);
                    created.put("status", "PENDING");
                    created.putArray("stepIds");
                    groups.add(created);
                    return created;
                });
                accumulateScopeNode(groupNode, stepNode, (ArrayNode) groupNode.path("stepIds"));
            }
        }

        finalizeScopeNode(branchNode);
        for (JsonNode groupNode : groups) {
            finalizeScopeNode((ObjectNode) groupNode);
        }
        return branchNode;
    }

    private ObjectNode findGroupNode(ArrayNode steps, String groupKey) {
        String normalizedGroup = groupKey == null || groupKey.isBlank() ? "default" : groupKey;
        ObjectNode groupNode = objectMapper.createObjectNode();
        groupNode.put("group", normalizedGroup);
        groupNode.put("totalSteps", 0);
        groupNode.put("executedSteps", 0);
        groupNode.put("skippedSteps", 0);
        groupNode.put("failedSteps", 0);
        groupNode.put("continuedFailureSteps", 0);
        groupNode.put("halted", false);
        groupNode.put("status", "PENDING");
        ArrayNode stepIds = groupNode.putArray("stepIds");

        for (JsonNode stepNode : steps) {
            if (normalizedGroup.equals(stepNode.path("group").asText("")) && stepNode.has("status")) {
                accumulateScopeNode(groupNode, stepNode, stepIds);
            }
        }

        finalizeScopeNode(groupNode);
        return groupNode;
    }

    private void accumulateScopeNode(ObjectNode scopeNode, JsonNode stepNode, ArrayNode stepIds) {
        scopeNode.put("totalSteps", scopeNode.path("totalSteps").asInt() + 1);
        scopeNode.put("executedSteps", scopeNode.path("executedSteps").asInt() + (stepNode.path("executed").asBoolean() ? 1 : 0));
        scopeNode.put("skippedSteps", scopeNode.path("skippedSteps").asInt() + (isStatus(stepNode, "SKIPPED") ? 1 : 0));
        scopeNode.put("failedSteps", scopeNode.path("failedSteps").asInt() + (isStatus(stepNode, "FAILED") ? 1 : 0));
        scopeNode.put("continuedFailureSteps", scopeNode.path("continuedFailureSteps").asInt() + (stepNode.path("continuedAfterFailure").asBoolean() ? 1 : 0));
        scopeNode.put("halted", scopeNode.path("halted").asBoolean() || stepNode.path("haltedWorkflow").asBoolean());
        stepIds.add(stepNode.path("id").asText(""));
    }

    private void finalizeScopeNode(ObjectNode scopeNode) {
        int failedSteps = scopeNode.path("failedSteps").asInt();
        int continuedFailureSteps = scopeNode.path("continuedFailureSteps").asInt();
        int executedSteps = scopeNode.path("executedSteps").asInt();
        int skippedSteps = scopeNode.path("skippedSteps").asInt();
        // 这里直接使用 put，避免 Jackson 泛型返回值在 JDK 21 下触发不必要的类型推断问题。
        scopeNode.put("hasFailure", failedSteps > 0);
        scopeNode.put("status", resolveScopeStatus(failedSteps, continuedFailureSteps, executedSteps, skippedSteps));
    }

    private String buildAggregateSummaryText(String scopeTypeLabel, String scopeKey, JsonNode scopeNode) {
        return scopeTypeLabel + " " + scopeKey
                + "：状态 " + scopeNode.path("status").asText("PENDING")
                + "，总步骤 " + scopeNode.path("totalSteps").asInt()
                + "，执行 " + scopeNode.path("executedSteps").asInt()
                + "，跳过 " + scopeNode.path("skippedSteps").asInt()
                + "，失败 " + scopeNode.path("failedSteps").asInt();
    }

    /**
     * 将分支和分组的实时状态写回 workflow state。
     * 后续步骤可以直接通过 state.workflowBranchState / state.workflowGroupState 判断某条路径是否失败，
     * 从而基于现有 when 机制实现轻量回退。
     */
    private void updateBranchAndGroupState(ObjectNode workflowState, ObjectNode stepNode) {
        updateScopeState(
                ensureObjectNode(workflowState, "workflowBranchState"),
                stepNode.path("branch").asText("default"),
                stepNode
        );
        updateScopeState(
                ensureObjectNode(workflowState, "workflowGroupState"),
                stepNode.path("group").asText("default"),
                stepNode
        );
    }

    private void updateScopeState(ObjectNode stateRoot, String scopeKey, ObjectNode stepNode) {
        ObjectNode scopeNode = ensureObjectNode(stateRoot, scopeKey);
        int totalSteps = scopeNode.path("totalSteps").asInt() + 1;
        int executedSteps = scopeNode.path("executedSteps").asInt() + (stepNode.path("executed").asBoolean() ? 1 : 0);
        int skippedSteps = scopeNode.path("skippedSteps").asInt() + (isStatus(stepNode, "SKIPPED") ? 1 : 0);
        int failedSteps = scopeNode.path("failedSteps").asInt() + (isStatus(stepNode, "FAILED") ? 1 : 0);
        int capabilitySteps = scopeNode.path("capabilitySteps").asInt() + (stepNode.hasNonNull("capabilityId") ? 1 : 0);
        int continuedFailureSteps = scopeNode.path("continuedFailureSteps").asInt() + (stepNode.path("continuedAfterFailure").asBoolean() ? 1 : 0);

        scopeNode.put("totalSteps", totalSteps);
        scopeNode.put("executedSteps", executedSteps);
        scopeNode.put("skippedSteps", skippedSteps);
        scopeNode.put("failedSteps", failedSteps);
        scopeNode.put("capabilitySteps", capabilitySteps);
        scopeNode.put("continuedFailureSteps", continuedFailureSteps);
        scopeNode.put("hasFailure", failedSteps > 0);
        scopeNode.put("halted", scopeNode.path("halted").asBoolean() || stepNode.path("haltedWorkflow").asBoolean());
        scopeNode.put("lastStepId", stepNode.path("id").asText(""));
        scopeNode.put("status", resolveScopeStatus(failedSteps, continuedFailureSteps, executedSteps, skippedSteps));
    }

    private ObjectNode ensureObjectNode(ObjectNode parent, String fieldName) {
        JsonNode existing = parent.get(fieldName);
        if (existing instanceof ObjectNode objectNode) {
            return objectNode;
        }
        ObjectNode created = objectMapper.createObjectNode();
        parent.set(fieldName, created);
        return created;
    }

    /**
     * 统一判断步骤节点状态。
     * 这里接收 JsonNode，避免在分支汇总、分组汇总和实时状态回写时反复做类型收窄。
     */
    private boolean isStatus(JsonNode stepNode, String status) {
        return status.equalsIgnoreCase(stepNode.path("status").asText(""));
    }

    private String resolveScopeStatus(int failedSteps, int continuedFailureSteps, int executedSteps, int skippedSteps) {
        if (failedSteps > 0 && failedSteps == continuedFailureSteps) {
            return "DEGRADED";
        }
        if (failedSteps > 0) {
            return "FAILED";
        }
        if (executedSteps == 0 && skippedSteps > 0) {
            return "SKIPPED";
        }
        return executedSteps > 0 ? "SUCCESS" : "PENDING";
    }

    /**
     * workflow 分支聚合器。
     * 负责把同一 branch 下的步骤、统计和分组信息合成一个稳定输出节点。
     */
    private static final class BranchAggregate {

        private final String branch;
        private final Map<String, GroupAggregate> groups = new LinkedHashMap<>();
        private final List<JsonNode> steps = new ArrayList<>();

        private BranchAggregate(String branch) {
            this.branch = branch;
        }

        private void accept(JsonNode stepNode, String group) {
            steps.add(stepNode.deepCopy());
            groups.computeIfAbsent(group, GroupAggregate::new).accept(stepNode);
        }

        private ObjectNode toNode(ObjectMapper objectMapper) {
            ObjectNode branchNode = objectMapper.createObjectNode();
            branchNode.put("branch", branch);
            branchNode.put("totalSteps", steps.size());
            branchNode.put("executedSteps", countByBoolean("executed", true));
            branchNode.put("skippedSteps", countByStatus("SKIPPED"));
            branchNode.put("failedSteps", countByStatus("FAILED"));
            branchNode.put("capabilitySteps", countByField("capabilityId"));
            branchNode.put("continuedFailureSteps", countByBoolean("continuedAfterFailure", true));
            branchNode.put("hasFailure", countByStatus("FAILED") > 0);
            branchNode.put("halted", countByBoolean("haltedWorkflow", true) > 0);
            branchNode.put("status", resolveAggregateStatus());
            ArrayNode stepIds = branchNode.putArray("stepIds");
            for (JsonNode stepNode : steps) {
                stepIds.add(stepNode.path("id").asText(""));
            }
            ArrayNode groupsNode = branchNode.putArray("groups");
            for (GroupAggregate aggregate : groups.values()) {
                groupsNode.add(aggregate.toNode(objectMapper));
            }
            return branchNode;
        }

        private int countByBoolean(String fieldName, boolean expected) {
            int count = 0;
            for (JsonNode stepNode : steps) {
                if (stepNode.path(fieldName).asBoolean() == expected) {
                    count++;
                }
            }
            return count;
        }

        private int countByStatus(String status) {
            int count = 0;
            for (JsonNode stepNode : steps) {
                if (status.equalsIgnoreCase(stepNode.path("status").asText(""))) {
                    count++;
                }
            }
            return count;
        }

        private int countByField(String fieldName) {
            int count = 0;
            for (JsonNode stepNode : steps) {
                if (stepNode.hasNonNull(fieldName) && !stepNode.path(fieldName).asText("").isBlank()) {
                    count++;
                }
            }
            return count;
        }

        private String resolveAggregateStatus() {
            int failedSteps = countByStatus("FAILED");
            int continuedFailureSteps = countByBoolean("continuedAfterFailure", true);
            int executedSteps = countByBoolean("executed", true);
            int skippedSteps = countByStatus("SKIPPED");
            if (failedSteps > 0 && failedSteps == continuedFailureSteps) {
                return "DEGRADED";
            }
            if (failedSteps > 0) {
                return "FAILED";
            }
            if (executedSteps == 0 && skippedSteps > 0) {
                return "SKIPPED";
            }
            return executedSteps > 0 ? "SUCCESS" : "PENDING";
        }
    }

    /**
     * workflow 分组聚合器。
     * group 用来表达 branch 下更细一层的处理段落，适合后续做可视化和压缩摘要。
     */
    private static final class GroupAggregate {

        private final String group;
        private final List<JsonNode> steps = new ArrayList<>();

        private GroupAggregate(String group) {
            this.group = group;
        }

        private void accept(JsonNode stepNode) {
            steps.add(stepNode.deepCopy());
        }

        private ObjectNode toNode(ObjectMapper objectMapper) {
            ObjectNode groupNode = objectMapper.createObjectNode();
            groupNode.put("group", group);
            groupNode.put("totalSteps", steps.size());
            groupNode.put("executedSteps", countByBoolean("executed", true));
            groupNode.put("skippedSteps", countByStatus("SKIPPED"));
            groupNode.put("failedSteps", countByStatus("FAILED"));
            groupNode.put("continuedFailureSteps", countByBoolean("continuedAfterFailure", true));
            groupNode.put("hasFailure", countByStatus("FAILED") > 0);
            groupNode.put("halted", countByBoolean("haltedWorkflow", true) > 0);
            groupNode.put("status", resolveAggregateStatus());
            ArrayNode stepIds = groupNode.putArray("stepIds");
            for (JsonNode stepNode : steps) {
                stepIds.add(stepNode.path("id").asText(""));
            }
            return groupNode;
        }

        private int countByBoolean(String fieldName, boolean expected) {
            int count = 0;
            for (JsonNode stepNode : steps) {
                if (stepNode.path(fieldName).asBoolean() == expected) {
                    count++;
                }
            }
            return count;
        }

        private int countByStatus(String status) {
            int count = 0;
            for (JsonNode stepNode : steps) {
                if (status.equalsIgnoreCase(stepNode.path("status").asText(""))) {
                    count++;
                }
            }
            return count;
        }

        private String resolveAggregateStatus() {
            int failedSteps = countByStatus("FAILED");
            int continuedFailureSteps = countByBoolean("continuedAfterFailure", true);
            int executedSteps = countByBoolean("executed", true);
            int skippedSteps = countByStatus("SKIPPED");
            if (failedSteps > 0 && failedSteps == continuedFailureSteps) {
                return "DEGRADED";
            }
            if (failedSteps > 0) {
                return "FAILED";
            }
            if (executedSteps == 0 && skippedSteps > 0) {
                return "SKIPPED";
            }
            return executedSteps > 0 ? "SUCCESS" : "PENDING";
        }
    }
}
