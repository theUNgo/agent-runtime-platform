package com.example.agentruntime.skill;

/**
 * Workflow Skill 的单个步骤定义。
 * 当前版本除了支持条件分支、能力绑定和失败继续外，
 * 还支持通过 branch / group 显式标记步骤所属路径，以及声明显式汇总节点。
 */
public record SkillWorkflowStep(
        String id,
        String title,
        String instruction,
        String outputKey,
        String when,
        String capabilityId,
        Object capabilityInput,
        String branch,
        String group,
        String summaryFromBranch,
        String summaryFromGroup,
        Boolean continueOnFailure,
        String documentDocId,
        String documentDetailWhen
) {

    /**
     * 兼容旧版 workflow 步骤构造方式。
     * 在没有显式文档读取声明时，默认不绑定能力文档动态读取行为。
     */
    public SkillWorkflowStep(String id,
                             String title,
                             String instruction,
                             String outputKey,
                             String when,
                             String capabilityId,
                             Object capabilityInput,
                             String branch,
                             String group,
                             String summaryFromBranch,
                             String summaryFromGroup,
                             Boolean continueOnFailure) {
        this(id, title, instruction, outputKey, when, capabilityId, capabilityInput, branch, group,
                summaryFromBranch, summaryFromGroup, continueOnFailure, null, null);
    }

    public SkillWorkflowStep {
        id = id == null || id.isBlank() ? "step" : id;
        title = title == null || title.isBlank() ? id : title;
        instruction = instruction == null ? "" : instruction;
        outputKey = outputKey == null || outputKey.isBlank() ? id : outputKey;
        when = when == null || when.isBlank() ? null : when;
        capabilityId = capabilityId == null || capabilityId.isBlank() ? null : capabilityId;
        branch = branch == null || branch.isBlank() ? null : branch;
        group = group == null || group.isBlank() ? null : group;
        summaryFromBranch = summaryFromBranch == null || summaryFromBranch.isBlank() ? null : summaryFromBranch;
        summaryFromGroup = summaryFromGroup == null || summaryFromGroup.isBlank() ? null : summaryFromGroup;
        continueOnFailure = continueOnFailure != null && continueOnFailure;
        documentDocId = documentDocId == null || documentDocId.isBlank() ? null : documentDocId;
        documentDetailWhen = documentDetailWhen == null || documentDetailWhen.isBlank() ? null : documentDetailWhen;
    }
}
