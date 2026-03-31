package com.example.agentruntime.web;

import com.example.agentruntime.i18n.MessageService;
import com.example.agentruntime.mcp.McpException;
import com.example.agentruntime.model.ModelException;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 将常见异常统一转换为多语言错误响应，避免直接把英文堆栈暴露给用户。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private final MessageService messageService;

    public GlobalExceptionHandler(MessageService messageService) {
        this.messageService = messageService;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException exception) {
        String message = exception.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(error -> messageService.get("error.validation.field", error.getField()))
                .orElseGet(() -> messageService.get("error.validation"));
        return ResponseEntity.badRequest().body(new ApiError("VALIDATION_ERROR", message));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiError> handleConstraintViolation(ConstraintViolationException exception) {
        return ResponseEntity.badRequest()
                .body(new ApiError("VALIDATION_ERROR", messageService.get("error.validation")));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiError> handleIllegalArgument(IllegalArgumentException exception) {
        return ResponseEntity.badRequest()
                .body(new ApiError("BAD_REQUEST", exception.getMessage()));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiError> handleIllegalState(IllegalStateException exception) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ApiError("ILLEGAL_STATE", exception.getMessage()));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiError> handleAccessDenied(AccessDeniedException exception) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(new ApiError("FORBIDDEN", exception.getMessage()));
    }

    @ExceptionHandler(McpException.class)
    public ResponseEntity<ApiError> handleMcp(McpException exception) {
        HttpStatus status = switch (exception.getCode()) {
            case MCP_CAPABILITY_UNSUPPORTED -> HttpStatus.BAD_REQUEST;
            case MCP_TIMEOUT -> HttpStatus.GATEWAY_TIMEOUT;
            case MCP_TRANSPORT_ERROR -> HttpStatus.BAD_GATEWAY;
            case MCP_PROTOCOL_ERROR -> HttpStatus.BAD_GATEWAY;
        };
        return ResponseEntity.status(status)
                .body(new ApiError(exception.getCode().name(), exception.getMessage()));
    }

    @ExceptionHandler(ModelException.class)
    public ResponseEntity<ApiError> handleModel(ModelException exception) {
        HttpStatus status = switch (exception.getCode()) {
            case MODEL_CONFIGURATION_ERROR, MODEL_PROVIDER_UNSUPPORTED -> HttpStatus.BAD_REQUEST;
            case MODEL_TIMEOUT -> HttpStatus.GATEWAY_TIMEOUT;
            case MODEL_REQUEST_FAILED, MODEL_INVALID_RESPONSE -> HttpStatus.BAD_GATEWAY;
        };
        return ResponseEntity.status(status)
                .body(new ApiError(exception.getCode().name(), exception.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(Exception exception) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ApiError("INTERNAL_ERROR", messageService.get("error.internal")));
    }
}
