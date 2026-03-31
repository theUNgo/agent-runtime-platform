package com.example.agentruntime.web;

import com.example.agentruntime.i18n.MessageService;
import com.example.agentruntime.mcp.McpErrorCode;
import com.example.agentruntime.mcp.McpException;
import com.example.agentruntime.model.ModelErrorCode;
import com.example.agentruntime.model.ModelException;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.StaticMessageSource;
import org.springframework.http.HttpStatus;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GlobalExceptionHandlerTest {

    @Test
    void shouldMapMcpTimeoutToGatewayTimeout() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler(messageService());
        var response = handler.handleMcp(new McpException(McpErrorCode.MCP_TIMEOUT, "timeout"));

        assertEquals(HttpStatus.GATEWAY_TIMEOUT, response.getStatusCode());
        assertEquals("MCP_TIMEOUT", response.getBody().code());
    }

    @Test
    void shouldMapModelRequestFailureToBadGateway() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler(messageService());
        var response = handler.handleModel(new ModelException(ModelErrorCode.MODEL_REQUEST_FAILED, "upstream failed"));

        assertEquals(HttpStatus.BAD_GATEWAY, response.getStatusCode());
        assertEquals("MODEL_REQUEST_FAILED", response.getBody().code());
    }

    private MessageService messageService() {
        StaticMessageSource source = new StaticMessageSource();
        source.addMessage("error.internal", Locale.ENGLISH, "internal");
        source.addMessage("error.validation", Locale.ENGLISH, "validation");
        source.addMessage("error.validation.field", Locale.ENGLISH, "validation field");
        return new MessageService(source);
    }
}
