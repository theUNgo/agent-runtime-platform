package com.example.agentruntime.demo;

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
 * 工程内置的 demo MCP stdio server。
 * 这个实现的目标不是替代真实生产 server，而是给当前宿主提供一个“开箱即联调”的真实样例，
 * 方便验证 stdio 传输、协议协商、tools/resources/prompts 主链是否工作正常。
 */
public final class DemoMcpStdioServer {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private DemoMcpStdioServer() {
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
            case "tools/call" -> response(id, callToolResult(
                    request.path("params").path("name").asText(""),
                    request.path("params").path("arguments")));
            case "resources/list" -> response(id, listResourcesResult());
            case "resources/read" -> response(id, readResourceResult(request.path("params").path("uri").asText("")));
            case "prompts/list" -> response(id, listPromptsResult());
            case "prompts/get" -> response(id, getPromptResult(
                    request.path("params").path("name").asText(""),
                    request.path("params").path("arguments").path("topic").asText("demo topic")));
            case "notifications/initialized" -> null;
            default -> error(id, -32601, "Method not found: " + method);
        };
    }

    private static ObjectNode initializeResult() {
        ObjectNode result = OBJECT_MAPPER.createObjectNode();
        result.put("protocolVersion", "2025-11-25");
        ObjectNode capabilities = result.putObject("capabilities");
        capabilities.putObject("tools");
        capabilities.putObject("resources");
        capabilities.putObject("prompts");
        ObjectNode serverInfo = result.putObject("serverInfo");
        serverInfo.put("name", "demo-stdio");
        serverInfo.put("title", "Demo STDIO MCP Server");
        serverInfo.put("version", "1.0.0");
        result.put("instructions", "This is a built-in demo MCP stdio server.");
        return result;
    }

    private static ObjectNode listToolsResult() {
        ObjectNode result = OBJECT_MAPPER.createObjectNode();
        ArrayNode tools = result.putArray("tools");

        ObjectNode echoTool = tools.addObject();
        echoTool.put("name", "echo_tool");
        echoTool.put("title", "Echo Tool");
        echoTool.put("description", "Echo back the provided text.");
        ObjectNode echoSchema = echoTool.putObject("inputSchema");
        echoSchema.put("type", "object");
        echoSchema.putObject("properties").putObject("text").put("type", "string");

        ObjectNode summaryTool = tools.addObject();
        summaryTool.put("name", "summarize_topic");
        summaryTool.put("title", "Summarize Topic");
        summaryTool.put("description", "Return a short built-in summary for the given topic.");
        ObjectNode summarySchema = summaryTool.putObject("inputSchema");
        summarySchema.put("type", "object");
        summarySchema.putObject("properties").putObject("topic").put("type", "string");

        return result;
    }

    private static ObjectNode callToolResult(String toolName, JsonNode arguments) {
        return switch (toolName) {
            case "echo_tool" -> toolResult("echo", arguments.path("text").asText(""));
            case "summarize_topic" -> toolResult("summary", "Demo summary for " + arguments.path("topic").asText("unknown"));
            default -> errorResult("Unsupported tool: " + toolName);
        };
    }

    private static ObjectNode listResourcesResult() {
        ObjectNode result = OBJECT_MAPPER.createObjectNode();
        ArrayNode resources = result.putArray("resources");
        resources.addObject()
                .put("uri", "demo://guides/quickstart")
                .put("name", "quickstart")
                .put("title", "Quickstart Guide")
                .put("description", "Built-in quickstart guide for demo validation.")
                .put("mimeType", "text/markdown");
        return result;
    }

    private static ObjectNode readResourceResult(String uri) {
        ObjectNode result = OBJECT_MAPPER.createObjectNode();
        ArrayNode contents = result.putArray("contents");
        contents.addObject()
                .put("uri", uri)
                .put("mimeType", "text/markdown")
                .put("text", "# Quickstart\nThis content comes from the built-in demo MCP stdio server.");
        return result;
    }

    private static ObjectNode listPromptsResult() {
        ObjectNode result = OBJECT_MAPPER.createObjectNode();
        ArrayNode prompts = result.putArray("prompts");
        prompts.addObject()
                .put("name", "summarize")
                .put("title", "Summarize Topic")
                .put("description", "Generate a short summary prompt for a topic.");
        return result;
    }

    private static ObjectNode getPromptResult(String promptName, String topic) {
        ObjectNode result = OBJECT_MAPPER.createObjectNode();
        result.put("description", "Prompt for " + promptName);
        ArrayNode messages = result.putArray("messages");
        messages.addObject()
                .put("role", "user")
                .putObject("content")
                .put("type", "text")
                .put("text", "Please summarize " + topic + " in three bullet points.");
        return result;
    }

    private static ObjectNode toolResult(String key, String value) {
        ObjectNode result = OBJECT_MAPPER.createObjectNode();
        result.put("isError", false);
        result.putArray("content")
                .addObject()
                .put("type", "text")
                .put("text", value);
        result.putObject("structuredContent").put(key, value);
        return result;
    }

    private static ObjectNode errorResult(String message) {
        ObjectNode result = OBJECT_MAPPER.createObjectNode();
        result.put("isError", true);
        result.putArray("content")
                .addObject()
                .put("type", "text")
                .put("text", message);
        return result;
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
}
