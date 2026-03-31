package com.example.agentruntime.mcp;

import io.modelcontextprotocol.json.TypeRef;
import io.modelcontextprotocol.spec.McpClientTransport;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpTransportException;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * 对官方 streamable-http transport 做一层宿主侧兼容包装。
 * 当前官方 SDK 在服务端不支持 SSE 长连接时，会通过 transport 异常回调抛出 405。
 * 对宿主来说，这通常属于“请求响应模式可继续工作”的预期内情况，因此这里在边界上做过滤。
 */
public final class CompatibleStreamableHttpTransport implements McpClientTransport {

    private final McpClientTransport delegate;

    public CompatibleStreamableHttpTransport(McpClientTransport delegate) {
        this.delegate = delegate;
    }

    @Override
    public Mono<Void> connect(Function<Mono<McpSchema.JSONRPCMessage>, Mono<McpSchema.JSONRPCMessage>> handler) {
        return delegate.connect(handler);
    }

    @Override
    public void setExceptionHandler(Consumer<Throwable> handler) {
        delegate.setExceptionHandler(throwable -> {
            if (isExpectedSseFallback(throwable)) {
                return;
            }
            handler.accept(throwable);
        });
    }

    @Override
    public Mono<Void> closeGracefully() {
        return delegate.closeGracefully();
    }

    @Override
    public Mono<Void> sendMessage(McpSchema.JSONRPCMessage message) {
        return delegate.sendMessage(message);
    }

    @Override
    public <T> T unmarshalFrom(Object data, TypeRef<T> typeRef) {
        return delegate.unmarshalFrom(data, typeRef);
    }

    @Override
    public List<String> protocolVersions() {
        return delegate.protocolVersions();
    }

    @Override
    public void close() {
        delegate.close();
    }

    static boolean isExpectedSseFallback(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current instanceof McpTransportException
                && current.getMessage() != null
                && current.getMessage().contains("status code: 405");
    }
}
