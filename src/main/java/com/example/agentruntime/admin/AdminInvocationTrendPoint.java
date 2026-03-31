package com.example.agentruntime.admin;

import java.time.LocalDate;

/**
 * 管理员总览中的按日调用趋势点。
 * 这里保留总调用、成功和失败三个维度，方便前端既能看走势，也能看稳定性。
 */
public record AdminInvocationTrendPoint(
        LocalDate date,
        long invocationCount,
        long successCount,
        long failureCount
) {
}
