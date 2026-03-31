package com.example.agentruntime.model;

/**
 * 统一封装模型供应商调用时产生的异常，方便在 Web 层映射成稳定响应。
 */
public class ModelException extends RuntimeException {

    private final ModelErrorCode code;

    public ModelException(ModelErrorCode code, String message) {
        super(message);
        this.code = code;
    }

    public ModelException(ModelErrorCode code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public ModelErrorCode getCode() {
        return code;
    }
}
