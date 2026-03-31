package com.example.agentruntime.web;

/**
 * 统一的错误返回模型，方便前端或调用方稳定处理。
 */
public record ApiError(
        String code,
        String message
) {
}
