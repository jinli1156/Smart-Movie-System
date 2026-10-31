package com.movie.ticket.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.*;

/** 模型 HTTP 客户端：读取服务配置、发送聊天请求，并解析模型响应或服务异常。 */
@Component
@RequiredArgsConstructor
public class DeepSeekClient {
    private final ObjectMapper mapper;
    @Value("${assistant.api-key:}") private String apiKey;
    @Value("${assistant.base-url:https://api.deepseek.com}") private String baseUrl;
    @Value("${assistant.model:deepseek-v4-flash}") private String model;
    @Value("${assistant.connect-timeout-ms:5000}") private int connectTimeoutMs;
    @Value("${assistant.proxy-url:${HTTPS_PROXY:http://127.0.0.1:7890}}") private String proxyUrl;
    @Value("${assistant.read-timeout-ms:60000}") private int readTimeoutMs;

    /** 检查模型访问配置是否完整。 */
    public boolean configured() { return apiKey != null && !apiKey.isBlank(); }
    /** 返回当前使用的模型名称。 */
    public String model() { return model; }

    /** Bounded, structured requests for recommendation panels. */
    public JsonNode completeJson(String systemPrompt, Object context) throws IOException {
        return completeJson(systemPrompt,context,2000);
    }
    /** Larger bounded output for splitting multi-record listing documents. */
    public JsonNode completeJson(String systemPrompt,Object context,int maxTokens) throws IOException {
        if (!configured()) throw new IOException("Model is not configured");
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectTimeoutMs);
        if (proxyUrl != null && !proxyUrl.isBlank()
                && !java.util.Set.of("localhost", "127.0.0.1", "::1").contains(java.net.URI.create(baseUrl).getHost())) {
            var proxy = java.net.URI.create(proxyUrl);
            factory.setProxy(new java.net.Proxy(java.net.Proxy.Type.HTTP,
                    new java.net.InetSocketAddress(proxy.getHost(), proxy.getPort())));
        }
        factory.setReadTimeout(Math.min(readTimeoutMs, maxTokens>2000?75000:20000));
        var client = RestClient.builder().baseUrl(baseUrl).requestFactory(factory)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey).build();
        var body = Map.of("model", model, "messages", List.of(
                        Map.of("role", "system", "content", systemPrompt),
                        Map.of("role", "user", "content", mapper.writeValueAsString(context))),
                "response_format", Map.of("type", "json_object"),
                "thinking", Map.of("type", "disabled"), "temperature", 0.3, "max_tokens", Math.max(1,Math.min(maxTokens,8000)));
        JsonNode response = client.post().uri("/chat/completions")
                .contentType(MediaType.APPLICATION_JSON).body(body).retrieve().body(JsonNode.class);
        if (response == null || !"stop".equals(response.path("choices").path(0).path("finish_reason").asText())) {
            throw new IOException("Incomplete model response");
        }
        JsonNode result = mapper.readTree(response.path("choices").path(0).path("message").path("content").asText());
        if (result == null || !result.isObject()) throw new IOException("Invalid model JSON");
        return result;
    }

    /** 发送模型聊天请求并处理响应或错误。 */
    public JsonNode complete(List<JsonNode> messages, JsonNode tools,
            Consumer<String> deltaConsumer, BooleanSupplier cancelled) {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectTimeoutMs);
        factory.setReadTimeout(readTimeoutMs);
        var client = RestClient.builder().baseUrl(baseUrl).requestFactory(factory)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey).build();
        var body = new LinkedHashMap<String, Object>(Map.of("model", model, "messages", messages,
                "stream", true, "thinking", Map.of("type", "disabled"), "temperature", 0.3, "max_tokens", 2000));
        body.put("tool_choice", tools.isEmpty() ? "none" : "auto");
        if (!tools.isEmpty()) body.put("tools", tools);
        return client.post().uri("/chat/completions").contentType(MediaType.APPLICATION_JSON).body(body)
                .exchange((request, response) -> {
                    if (!response.getStatusCode().is2xxSuccessful()) throw new IOException("Model service unavailable");
                    try (var reader = new BufferedReader(new InputStreamReader(response.getBody(), StandardCharsets.UTF_8))) {
                        return readStream(reader, deltaConsumer, cancelled);
                    }
                });
    }

    JsonNode readStream(BufferedReader reader, Consumer<String> deltaConsumer, BooleanSupplier cancelled) throws IOException {
        StringBuilder content = new StringBuilder();
        Map<Integer, ObjectNode> calls = new TreeMap<>();
        String finish = null;
        String line;
        while ((line = reader.readLine()) != null) {
            if (cancelled.getAsBoolean()) throw new IOException("Cancelled");
            if (!line.startsWith("data:")) continue;
            String data = line.substring(5).trim();
            if (data.equals("[DONE]")) break;
            if (data.isEmpty()) continue;
            if (data.length() > 100_000) throw new IOException("Oversized stream frame");
            JsonNode frame = mapper.readTree(data);
            if (frame.has("error")) throw new IOException("Upstream stream error");
            JsonNode choice = frame.path("choices").path(0);
            JsonNode delta = choice.path("delta");
            if (choice.hasNonNull("finish_reason")) finish = choice.path("finish_reason").asText();
            if (delta.hasNonNull("content")) {
                String text = delta.path("content").asText();
                content.append(text);
                if (content.length() > 16000) throw new IOException("Response too long");
                deltaConsumer.accept(text);
            }
            for (JsonNode fragment : delta.path("tool_calls")) {
                int index = fragment.path("index").asInt(-1);
                if (index < 0 || index >= 8) throw new IOException("Too many tool calls");
                ObjectNode call = calls.computeIfAbsent(index, ignored -> {
                    var node = mapper.createObjectNode().put("id", "").put("type", "function");
                    node.putObject("function").put("name", "").put("arguments", "");
                    return node;
                });
                append(call, "id", fragment.path("id"));
                append((ObjectNode) call.path("function"), "name", fragment.path("function").path("name"));
                append((ObjectNode) call.path("function"), "arguments", fragment.path("function").path("arguments"));
            }
        }
        if (!"stop".equals(finish) && !"tool_calls".equals(finish)) throw new IOException("Incomplete model response");
        var message = mapper.createObjectNode().put("role", "assistant").put("content", content.toString());
        if (!calls.isEmpty()) {
            var array = message.putArray("tool_calls");
            for (ObjectNode call : calls.values()) {
                if (call.path("id").asText().isBlank() || call.path("function").path("name").asText().isBlank()) throw new IOException("Invalid tool call");
                array.add(call);
            }
        } else if ("tool_calls".equals(finish)) throw new IOException("Missing tool calls");
        return message;
    }

    private void append(ObjectNode node, String key, JsonNode fragment) throws IOException {
        if (!fragment.isTextual()) return;
        String value = node.path(key).asText() + fragment.asText();
        if (value.length() > 8000) throw new IOException("Tool arguments too large");
        node.put(key, value);
    }
}
