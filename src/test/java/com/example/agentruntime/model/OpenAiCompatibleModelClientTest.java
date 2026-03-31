package com.example.agentruntime.model;

import com.example.agentruntime.i18n.MessageService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.StaticMessageSource;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenAiCompatibleModelClientTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Test
    void shouldCallOpenAiCompatibleEndpointWithImagePayload() throws Exception {
        try (FakeModelServer server = new FakeModelServer()) {
            OpenAiCompatibleModelClient client = new OpenAiCompatibleModelClient(
                    RestClient.builder(),
                    OBJECT_MAPPER,
                    messageService()
            );

            JsonNode input = OBJECT_MAPPER.readTree("""
                    {
                      "imageUrls": ["https://example.com/demo.png"],
                      "thinking": {"type":"enabled"}
                    }
                    """);

            ModelChatResponse response = client.chat(
                    new ModelRuntimeProfile(
                            1L,
                            "Vision",
                            "openai-compatible",
                            server.baseUrl(),
                            "test-key",
                            "glm-4.6v",
                            true,
                            true,
                            false,
                            true,
                            true,
                            true,
                            true
                    ),
                    new ModelChatRequest(
                            "Where is the bottle?",
                            "You are a helpful assistant.",
                            null,
                            input,
                            null,
                            null
                    )
            );

            assertEquals("[[10,20,30,40]]", response.content());
            assertEquals("Bearer test-key", server.lastAuthorizationHeader());
            assertTrue(server.lastRequestBody().path("messages").isArray());
            assertEquals("glm-4.6v", server.lastRequestBody().path("model").asText());
            assertEquals("enabled", server.lastRequestBody().path("thinking").path("type").asText());
        }
    }

    @Test
    void shouldAppendChatCompletionsSuffixWhenBaseUrlIsApiRoot() throws Exception {
        try (FakeModelServer server = new FakeModelServer()) {
            OpenAiCompatibleModelClient client = new OpenAiCompatibleModelClient(
                    RestClient.builder(),
                    OBJECT_MAPPER,
                    messageService()
            );

            client.chat(
                    new ModelRuntimeProfile(
                            1L,
                            "General",
                            "openai-compatible",
                            server.apiRootUrl(),
                            "test-key",
                            "glm-4.6v",
                            true,
                            false,
                            false,
                            true,
                            true,
                            true,
                            true
                    ),
                    new ModelChatRequest("Say hi", null, null, null, null, null)
            );

            assertEquals("/api/paas/v4/chat/completions", server.lastPath());
        }
    }

    @Test
    void shouldMergeMultiModalAndExtraBodyFields() throws Exception {
        try (FakeModelServer server = new FakeModelServer()) {
            OpenAiCompatibleModelClient client = new OpenAiCompatibleModelClient(
                    RestClient.builder(),
                    OBJECT_MAPPER,
                    messageService()
            );

            JsonNode input = OBJECT_MAPPER.readTree("""
                    {
                      "images": [
                        "https://example.com/a.png",
                        {"url": "https://example.com/b.png"}
                      ],
                      "audioUrls": [
                        "https://example.com/demo.mp3"
                      ],
                      "extraBody": {
                        "stream": false,
                        "metadata": {
                          "scene": "preview"
                        }
                      }
                    }
                    """);

            client.chat(
                    new ModelRuntimeProfile(
                            1L,
                            "General",
                            "openai-compatible",
                            server.baseUrl(),
                            "test-key",
                            "glm-4.6v",
                            true,
                            true,
                            true,
                            true,
                            true,
                            true,
                            true
                    ),
                    new ModelChatRequest("请总结这些输入", null, null, input, null, null)
            );

            JsonNode requestBody = server.lastRequestBody();
            JsonNode content = requestBody.path("messages").get(0).path("content");
            assertEquals("image_url", content.get(0).path("type").asText());
            assertEquals("image_url", content.get(1).path("type").asText());
            assertEquals("input_audio", content.get(2).path("type").asText());
            assertEquals("https://example.com/demo.mp3", content.get(2).path("input_audio").path("url").asText());
            assertEquals(false, requestBody.path("stream").asBoolean(true));
            assertEquals("preview", requestBody.path("metadata").path("scene").asText());
        }
    }

    @Test
    void shouldConvertAudioDataUrlIntoDataAndFormat() throws Exception {
        try (FakeModelServer server = new FakeModelServer()) {
            OpenAiCompatibleModelClient client = new OpenAiCompatibleModelClient(
                    RestClient.builder(),
                    OBJECT_MAPPER,
                    messageService()
            );

            JsonNode input = OBJECT_MAPPER.readTree("""
                    {
                      "audioUrls": [
                        "data:audio/mpeg;base64,QUJDRA=="
                      ]
                    }
                    """);

            client.chat(
                    new ModelRuntimeProfile(
                            1L,
                            "Audio",
                            "openai-compatible",
                            server.baseUrl(),
                            "test-key",
                            "glm-4.6v",
                            true,
                            false,
                            true,
                            true,
                            true,
                            true,
                            true
                    ),
                    new ModelChatRequest("请识别音频内容", null, null, input, null, null)
            );

            JsonNode audioInput = server.lastRequestBody()
                    .path("messages").get(0)
                    .path("content").get(0)
                    .path("input_audio");

            assertEquals("QUJDRA==", audioInput.path("data").asText());
            assertEquals("mpeg", audioInput.path("format").asText());
        }
    }

    @Test
    void shouldAppendFewShotExamplesBeforeUserMessage() throws Exception {
        try (FakeModelServer server = new FakeModelServer()) {
            OpenAiCompatibleModelClient client = new OpenAiCompatibleModelClient(
                    RestClient.builder(),
                    OBJECT_MAPPER,
                    messageService()
            );

            JsonNode examples = OBJECT_MAPPER.readTree("""
                    [
                      {
                        "role": "user",
                        "content": "请提取图片中的品牌名称。"
                      },
                      {
                        "role": "assistant",
                        "content": "{\\\"brand\\\":\\\"Acme\\\"}"
                      }
                    ]
                    """);

            client.chat(
                    new ModelRuntimeProfile(
                            1L,
                            "Vision",
                            "openai-compatible",
                            server.baseUrl(),
                            "test-key",
                            "glm-4.6v",
                            true,
                            true,
                            false,
                            true,
                            true,
                            true,
                            true
                    ),
                    new ModelChatRequest("请处理这张新图片", "你是抽取助手。", examples, null, null, null)
            );

            JsonNode messages = server.lastRequestBody().path("messages");
            assertEquals("system", messages.get(0).path("role").asText());
            assertEquals("user", messages.get(1).path("role").asText());
            assertEquals("assistant", messages.get(2).path("role").asText());
            assertEquals("user", messages.get(3).path("role").asText());
        }
    }

    @Test
    void shouldAppendBackgroundContextIntoUserContent() throws Exception {
        try (FakeModelServer server = new FakeModelServer()) {
            OpenAiCompatibleModelClient client = new OpenAiCompatibleModelClient(
                    RestClient.builder(),
                    OBJECT_MAPPER,
                    messageService()
            );

            client.chat(
                    new ModelRuntimeProfile(
                            1L,
                            "General",
                            "openai-compatible",
                            server.baseUrl(),
                            "test-key",
                            "glm-4.6v",
                            true,
                            false,
                            false,
                            true,
                            true,
                            true,
                            true
                    ),
                    new ModelChatRequest("继续处理当前问题", null, null, null, null, "较早历史摘要：用户已经确认目录结构无误。")
            );

            String contentText = server.lastRequestBody()
                    .path("messages").get(0)
                    .path("content").get(0)
                    .path("text").asText();

            assertTrue(contentText.contains("背景上下文摘要"));
            assertTrue(contentText.contains("目录结构无误"));
        }
    }

    private MessageService messageService() {
        StaticMessageSource source = new StaticMessageSource();
        source.addMessage("model.error.baseUrlRequired", Locale.ENGLISH, "base url required");
        source.addMessage("model.error.apiKeyRequired", Locale.ENGLISH, "api key required");
        source.addMessage("model.error.modelIdRequired", Locale.ENGLISH, "model required");
        source.addMessage("model.error.timeout", Locale.ENGLISH, "timeout");
        source.addMessage("model.error.requestFailed", Locale.ENGLISH, "request failed");
        source.addMessage("model.error.invalidResponse", Locale.ENGLISH, "invalid response");
        source.addMessage("model.error.emptyResponseFallback", Locale.ENGLISH, "empty {0}");
        return new MessageService(source);
    }

    private static final class FakeModelServer implements AutoCloseable {

        private final HttpServer server;
        private volatile JsonNode lastRequestBody;
        private volatile String lastAuthorizationHeader;
        private volatile String lastPath;

        private FakeModelServer() throws IOException {
            server = HttpServer.create(new InetSocketAddress(0), 0);
            server.createContext("/api/paas/v4/chat/completions", this::handle);
            server.start();
        }

        private void handle(HttpExchange exchange) throws IOException {
            lastAuthorizationHeader = exchange.getRequestHeaders().getFirst("Authorization");
            lastPath = exchange.getRequestURI().getPath();
            try (InputStream inputStream = exchange.getRequestBody()) {
                lastRequestBody = OBJECT_MAPPER.readTree(inputStream);
            }

            byte[] payload = """
                    {
                      "choices": [
                        {
                          "finish_reason": "stop",
                          "message": {
                            "content": "[[10,20,30,40]]"
                          }
                        }
                      ]
                    }
                    """.getBytes(StandardCharsets.UTF_8);

            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, payload.length);
            try (OutputStream outputStream = exchange.getResponseBody()) {
                outputStream.write(payload);
            }
        }

        private String baseUrl() {
            return "http://127.0.0.1:" + server.getAddress().getPort() + "/api/paas/v4/chat/completions";
        }

        private String apiRootUrl() {
            return "http://127.0.0.1:" + server.getAddress().getPort() + "/api/paas/v4";
        }

        private JsonNode lastRequestBody() {
            return lastRequestBody;
        }

        private String lastAuthorizationHeader() {
            return lastAuthorizationHeader;
        }

        private String lastPath() {
            return lastPath;
        }

        @Override
        public void close() {
            server.stop(0);
        }
    }
}
