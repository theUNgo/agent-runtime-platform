package com.example.agentruntime.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;

/**
 * 用于测试的最小 MCP stdio 假服务。
 * 只实现 initialize、tools/list、tools/call，足以验证官方 SDK 接入链路。
 */
public final class FakeStdioMcpServer {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private FakeStdioMcpServer() {
    }

    public static void main(String[] args) throws Exception {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
             BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(System.out, StandardCharsets.UTF_8))) {

            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                JsonNode request = OBJECT_MAPPER.readTree(line);
                JsonNode response = route(request);
                if (response != null) {
                    writer.write(OBJECT_MAPPER.writeValueAsString(response));
                    writer.write('\n');
                    writer.flush();
                }
            }
        }
    }

    private static JsonNode route(JsonNode request) {
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

    private static ObjectNode response(JsonNode id, JsonNode result) {
        ObjectNode response = OBJECT_MAPPER.createObjectNode();
        response.put("jsonrpc", "2.0");
        response.set("id", id);
        response.set("result", result);
        return response;
    }

    private static ObjectNode error(JsonNode id, int code, String message) {
        ObjectNode response = OBJECT_MAPPER.createObjectNode();
        response.put("jsonrpc", "2.0");
        response.set("id", id);
        ObjectNode error = response.putObject("error");
        error.put("code", code);
        error.put("message", message);
        return response;
    }

    private static ObjectNode initializeResult() {
        ObjectNode result = OBJECT_MAPPER.createObjectNode();
        result.put("protocolVersion", "2025-11-25");
        ObjectNode capabilities = result.putObject("capabilities");
        capabilities.putObject("tools");
        capabilities.putObject("resources");
        capabilities.putObject("prompts");
        ObjectNode serverInfo = result.putObject("serverInfo");
        serverInfo.put("name", "fake-stdio");
        serverInfo.put("title", "Fake STDIO");
        serverInfo.put("version", "1.0.0");
        result.put("instructions", "fake stdio server");
        return result;
    }

    private static ObjectNode listToolsResult() {
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

    private static ObjectNode callToolResult(String text) {
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

    private static ObjectNode listResourcesResult() {
        ObjectNode result = OBJECT_MAPPER.createObjectNode();
        ArrayNode resources = result.putArray("resources");
        resources.addObject()
                .put("uri", "file://docs/readme.md")
                .put("name", "readme")
                .put("title", "README")
                .put("description", "Project readme")
                .put("mimeType", "text/markdown");
        return result;
    }

    private static ObjectNode readResourceResult(String uri) {
        ObjectNode result = OBJECT_MAPPER.createObjectNode();
        ArrayNode contents = result.putArray("contents");
        contents.addObject()
                .put("uri", uri)
                .put("mimeType", "text/markdown")
                .put("text", "# README\nfake stdio content");
        return result;
    }

    private static ObjectNode listPromptsResult() {
        ObjectNode result = OBJECT_MAPPER.createObjectNode();
        ArrayNode prompts = result.putArray("prompts");
        prompts.addObject()
                .put("name", "summarize")
                .put("title", "Summarize")
                .put("description", "Summarize a topic");
        return result;
    }

    private static ObjectNode getPromptResult(String name, String topic) {
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
