package com.example.agentruntime.mcp;

import io.modelcontextprotocol.json.TypeRef;
import io.modelcontextprotocol.spec.McpClientTransport;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpTransportException;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CompatibleStreamableHttpTransportTest {

    @Test
    void shouldSuppressExpected405SseFallbackException() {
        FakeTransport delegate = new FakeTransport();
        CompatibleStreamableHttpTransport transport = new CompatibleStreamableHttpTransport(delegate);
        AtomicInteger counter = new AtomicInteger();

        transport.setExceptionHandler(ignored -> counter.incrementAndGet());
        delegate.emit(new McpTransportException("Unrecognized server error when connecting to SSE stream, status code: 405"));

        assertEquals(0, counter.get());
    }

    @Test
    void shouldForwardUnexpectedTransportExceptions() {
        FakeTransport delegate = new FakeTransport();
        CompatibleStreamableHttpTransport transport = new CompatibleStreamableHttpTransport(delegate);
        AtomicInteger counter = new AtomicInteger();

        transport.setExceptionHandler(ignored -> counter.incrementAndGet());
        delegate.emit(new McpTransportException("status code: 500"));

        assertEquals(1, counter.get());
    }

    @Test
    void shouldDelegateProtocolVersions() {
        FakeTransport delegate = new FakeTransport();
        CompatibleStreamableHttpTransport transport = new CompatibleStreamableHttpTransport(delegate);

        assertTrue(transport.protocolVersions().contains("2025-11-25"));
    }

    private static final class FakeTransport implements McpClientTransport {

        private Consumer<Throwable> exceptionHandler = ignored -> {
        };

        @Override
        public Mono<Void> connect(Function<Mono<McpSchema.JSONRPCMessage>, Mono<McpSchema.JSONRPCMessage>> handler) {
            return Mono.empty();
        }

        @Override
        public void setExceptionHandler(Consumer<Throwable> handler) {
            this.exceptionHandler = handler;
        }

        @Override
        public Mono<Void> closeGracefully() {
            return Mono.empty();
        }

        @Override
        public Mono<Void> sendMessage(McpSchema.JSONRPCMessage message) {
            return Mono.empty();
        }

        @Override
        public <T> T unmarshalFrom(Object data, TypeRef<T> typeRef) {
            return null;
        }

        @Override
        public List<String> protocolVersions() {
            return List.of("2025-11-25");
        }

        private void emit(Throwable throwable) {
            exceptionHandler.accept(throwable);
        }
    }
}
