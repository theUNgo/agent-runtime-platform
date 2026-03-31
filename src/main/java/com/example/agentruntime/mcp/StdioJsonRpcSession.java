package com.example.agentruntime.mcp;

import com.example.agentruntime.i18n.MessageService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;

/**
 * stdio 传输下的 JSON-RPC 会话管理器。
 * 负责启动子进程、发送请求、接收响应，并把响应按 request id 关联回调用方。
 */
public final class StdioJsonRpcSession implements AutoCloseable {

    private final ProcessBuilder processBuilder;
    private final ObjectMapper objectMapper;
    private final MessageService messageService;
    private final Duration timeout;
    private final AtomicLong requestId = new AtomicLong(0);
    private final Map<Long, CompletableFuture<JsonNode>> pendingRequests = new ConcurrentHashMap<>();
    private final ExecutorService ioExecutor = Executors.newVirtualThreadPerTaskExecutor();

    private volatile Process process;
    private volatile BufferedWriter writer;
    private volatile boolean started;

    public StdioJsonRpcSession(ProcessBuilder processBuilder,
                               ObjectMapper objectMapper,
                               MessageService messageService,
                               Duration timeout) {
        this.processBuilder = processBuilder;
        this.objectMapper = objectMapper;
        this.messageService = messageService;
        this.timeout = timeout;
    }

    /**
     * 启动 MCP 子进程并拉起 stdout/stderr 监听循环。
     */
    public synchronized void start() {
        if (started) {
            return;
        }
        try {
            process = processBuilder.start();
            writer = new BufferedWriter(new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8));
            ioExecutor.submit(this::readStdoutLoop);
            ioExecutor.submit(this::readStderrLoop);
            started = true;
        } catch (IOException exception) {
            throw new IllegalStateException(messageService.get("mcp.stdio.process.start.failed", processBuilder.command()), exception);
        }
    }

    /**
     * 发送 JSON-RPC request 并等待对应 response。
     */
    public JsonNode sendRequest(String method, JsonNode params) {
        start();
        long id = requestId.incrementAndGet();
        CompletableFuture<JsonNode> future = new CompletableFuture<>();
        pendingRequests.put(id, future);

        ObjectNode request = objectMapper.createObjectNode();
        request.put("jsonrpc", "2.0");
        request.put("id", id);
        request.put("method", method);
        if (params != null && !params.isNull()) {
            request.set("params", params);
        }

        writeMessage(request);

        try {
            return future.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException exception) {
            pendingRequests.remove(id);
            throw new IllegalStateException(messageService.get("mcp.stdio.request.timeout", method, timeout.toMillis()), exception);
        } catch (Exception exception) {
            pendingRequests.remove(id);
            throw new IllegalStateException(messageService.get("mcp.stdio.request.failed", method), exception);
        }
    }

    /**
     * 发送 JSON-RPC notification，不等待返回。
     */
    public void sendNotification(String method, JsonNode params) {
        start();
        ObjectNode notification = objectMapper.createObjectNode();
        notification.put("jsonrpc", "2.0");
        notification.put("method", method);
        if (params != null && !params.isNull()) {
            notification.set("params", params);
        }
        writeMessage(notification);
    }

    private synchronized void writeMessage(JsonNode node) {
        try {
            writer.write(objectMapper.writeValueAsString(node));
            writer.write('\n');
            writer.flush();
        } catch (IOException exception) {
            throw new IllegalStateException(messageService.get("mcp.stdio.write.failed"), exception);
        }
    }

    private void readStdoutLoop() {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                handleIncomingJson(line);
            }
        } catch (IOException exception) {
            completePendingExceptionally(exception);
        }
    }

    private void readStderrLoop() {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8))) {
            while (reader.readLine() != null) {
                // 当前阶段先忽略 server stderr 日志。
                // 后续可接入日志总线或审计链路。
            }
        } catch (IOException ignored) {
        }
    }

    private void handleIncomingJson(String payload) throws IOException {
        JsonNode message = objectMapper.readTree(payload);
        if (message.isArray()) {
            for (JsonNode item : message) {
                handleMessage(item);
            }
            return;
        }
        handleMessage(message);
    }

    private void handleMessage(JsonNode message) {
        JsonNode idNode = message.get("id");
        if (idNode != null && message.has("result")) {
            completeRequest(idNode, message.get("result"));
            return;
        }
        if (idNode != null && message.has("error")) {
            completeRequestExceptionally(idNode, message.get("error"));
        }
    }

    private void completeRequest(JsonNode idNode, JsonNode result) {
        CompletableFuture<JsonNode> future = pendingRequests.remove(idNode.asLong());
        if (future != null) {
            future.complete(result);
        }
    }

    private void completeRequestExceptionally(JsonNode idNode, JsonNode error) {
        CompletableFuture<JsonNode> future = pendingRequests.remove(idNode.asLong());
        if (future != null) {
            future.completeExceptionally(new IllegalStateException(
                    messageService.get("mcp.stdio.response.error", error.toString())));
        }
    }

    private void completePendingExceptionally(Exception exception) {
        pendingRequests.values().forEach(future -> future.completeExceptionally(exception));
        pendingRequests.clear();
    }

    @Override
    public synchronized void close() {
        if (process != null) {
            process.destroy();
        }
        ioExecutor.close();
        started = false;
    }
}
