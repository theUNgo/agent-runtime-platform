package com.example.agentruntime.model;

/**
 * 模型调用链路的稳定错误码。
 */
public enum ModelErrorCode {
    MODEL_CONFIGURATION_ERROR,
    MODEL_PROVIDER_UNSUPPORTED,
    MODEL_REQUEST_FAILED,
    MODEL_TIMEOUT,
    MODEL_INVALID_RESPONSE
}
