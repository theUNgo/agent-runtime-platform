package com.example.agentruntime.model;

/**
 * 模型访问审批状态。
 * 这里用于区分“尚未申请”“审批中”“已批准”“已拒绝”四种用户视角状态。
 */
public enum ModelAccessStatus {
    NOT_REQUESTED,
    PENDING,
    APPROVED,
    REJECTED
}
