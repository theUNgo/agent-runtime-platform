package com.example.agentruntime.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

/**
 * 用于测试 streamable-http 传输的最小 MCP 假服务。
 */
public final class FakeStreamableHttpMcpServer implements AutoCloseable {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final HttpServer server;

    public FakeStreamableHttpMcpServer() throws IOException {
        this.server = HttpServer.create(new InetSocketAddress(0), 0);
        this.server.createContext("/mcp", new McpHandler());
        this.server.start();
    }

    public String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/mcp";
    }

    @Override
    public void close() {
        server.stop(0);
    }

    private static final class McpHandler implements HttpHandler {

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(405, -1);
                return;
            }

            JsonNode request;
            try (InputStream inputStream = exchange.getRequestBody()) {
                request = OBJECT_MAPPER.readTree(inputStream);
            }

            JsonNode response = route(request);
            if (response == null) {
                exchange.sendResponseHeaders(202, -1);
                return;
            }
            byte[] payload = OBJECT_MAPPER.writeValueAsBytes(response);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, payload.length);
            try (OutputStream outputStream = exchange.getResponseBody()) {
                outputStream.write(payload);
            }
        }

        private JsonNode route(JsonNode request) {
            String method = request.path("method").asText();
            JsonNode id = request.get("id");

            return switch (method) {
                case "initialize" -> response(id, initializeResult());
                case "tools/list" -> response(id, listToolsResult());
                case "tools/call" -> response(id, callToolResult(request.path("params").path("arguments").path("text").asText("")));
                case "resources/list" -> response(id, listResourcesResult());
                case "resources/read" -> response(id, readResourceResult(request.path("params").path("uri").asText("")));
                case "prompts/list" -> response(id, listPromptsResult());
                case "prompts/get" -> response(id, getPromptResult(
                        request.path("params").path("name").asText(""),
                        request.path("params").path("arguments").path("topic").asText("unknown")));
                case "notifications/initialized" -> null;
                default -> error(id, -32601, "Method not found: " + method);
            };
        }

        private ObjectNode response(JsonNode id, JsonNode result) {
            ObjectNode response = OBJECT_MAPPER.createObjectNode();
            response.put("jsonrpc", "2.0");
            response.set("id", id);
            response.set("result", result);
            return response;
        }

        private ObjectNode error(JsonNode id, int code, String message) {
            ObjectNode response = OBJECT_MAPPER.createObjectNode();
            response.put("jsonrpc", "2.0");
            response.set("id", id);
            ObjectNode error = response.putObject("error");
            error.put("code", code);
            error.put("message", message);
            return response;
        }

        private ObjectNode initializeResult() {
            ObjectNode result = OBJECT_MAPPER.createObjectNode();
            result.put("protocolVersion", "2025-11-25");
            ObjectNode capabilities = result.putObject("capabilities");
            capabilities.putObject("tools");
            capabilities.putObject("resources");
            capabilities.putObject("prompts");
            ObjectNode serverInfo = result.putObject("serverInfo");
            serverInfo.put("name", "fake-http");
            serverInfo.put("title", "Fake HTTP");
            serverInfo.put("version", "1.0.0");
            result.put("instructions", "fake http server");
            return result;
        }

        private ObjectNode listToolsResult() {
            ObjectNode result = OBJECT_MAPPER.createObjectNode();
            ArrayNode tools = result.putArray("tools");
            ObjectNode tool = tools.addObject();
            tool.put("name", "echo_tool");
            tool.put("title", "Echo Tool");
            tool.put("description", "Echo back the provided text");
            ObjectNode inputSchema = tool.putObject("inputSchema");
            inputSchema.put("type", "object");
            ObjectNode properties = inputSchema.putObject("properties");
            properties.putObject("text").put("type", "string");
            return result;
        }

        private ObjectNode callToolResult(String text) {
            ObjectNode result = OBJECT_MAPPER.createObjectNode();
            result.put("isError", false);
            ArrayNode content = result.putArray("content");
            content.addObject()
                    .put("type", "text")
                    .put("text", "echo:" + text);
            result.putObject("structuredContent")
                    .put("echo", text);
            return result;
        }

        private ObjectNode listResourcesResult() {
            ObjectNode result = OBJECT_MAPPER.createObjectNode();
            ArrayNode resources = result.putArray("resources");
            resources.addObject()
                    .put("uri", "file://docs/http-readme.md")
                    .put("name", "http-readme")
                    .put("title", "HTTP README")
                    .put("description", "Project readme from http server")
                    .put("mimeType", "text/markdown");
            return result;
        }

        private ObjectNode readResourceResult(String uri) {
            ObjectNode result = OBJECT_MAPPER.createObjectNode();
            ArrayNode contents = result.putArray("contents");
            contents.addObject()
                    .put("uri", uri)
                    .put("mimeType", "text/markdown")
                    .put("text", "# HTTP README\nfake http content");
            return result;
        }

        private ObjectNode listPromptsResult() {
            ObjectNode result = OBJECT_MAPPER.createObjectNode();
            ArrayNode prompts = result.putArray("prompts");
            prompts.addObject()
                    .put("name", "summarize")
                    .put("title", "Summarize")
                    .put("description", "Summarize a topic");
            return result;
        }

        private ObjectNode getPromptResult(String name, String topic) {
            ObjectNode result = OBJECT_MAPPER.createObjectNode();
            result.put("description", "Prompt for " + name);
            ArrayNode messages = result.putArray("messages");
            messages.addObject()
                    .put("role", "user")
                    .putObject("content")
                    .put("type", "text")
                    .put("text", "Please summarize " + topic);
            return result;
        }
    }
}
