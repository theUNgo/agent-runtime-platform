package com.example.agentruntime.model;

/**
 * 管理员审批模型访问申请时的请求体。
 */
public record AdminModelAccessReviewRequest(
        String comment,
        boolean makeDefault
) {
}
