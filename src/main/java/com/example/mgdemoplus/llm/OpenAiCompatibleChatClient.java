package com.example.mgdemoplus.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * OpenAI 兼容 Chat Completions HTTP 客户端（JDK {@link HttpClient}），可用于方舟等平台；
 * 与具体业务域（NPC、房间等）无关。
 * <p>
 * 密钥请用环境变量或调用方注入；不要把密钥写进代码仓库。
 */
public final class OpenAiCompatibleChatClient {

    public static final String DEFAULT_VOLCES_ARK_CHAT_URL =
            "https://ark.cn-beijing.volces.com/api/v3/chat/completions";

    private final String apiKey;
    private final String endpointModelId;
    private final String baseUrl;
    /** 方舟 {@code reasoning_effort}；为空则不写入请求体。 */
    private final String reasoningEffort;
    /** 方舟 {@code thinking.type}：{@code enabled} / {@code disabled} / {@code auto}；为空则不写入。 */
    private final String thinkingType;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public OpenAiCompatibleChatClient(String apiKey, String endpointModelId, String baseUrl) {
        this(apiKey, endpointModelId, baseUrl, null, null);
    }

    public OpenAiCompatibleChatClient(
            String apiKey, String endpointModelId, String baseUrl, String reasoningEffort) {
        this(apiKey, endpointModelId, baseUrl, reasoningEffort, null);
    }

    public OpenAiCompatibleChatClient(
            String apiKey,
            String endpointModelId,
            String baseUrl,
            String reasoningEffort,
            String thinkingType) {
        this.apiKey = apiKey != null ? apiKey : "";
        this.endpointModelId = endpointModelId != null ? endpointModelId : "";
        this.baseUrl = (baseUrl != null && !baseUrl.isBlank()) ? baseUrl.trim() : DEFAULT_VOLCES_ARK_CHAT_URL;
        this.reasoningEffort =
                (reasoningEffort != null && !reasoningEffort.isBlank()) ? reasoningEffort.trim() : null;
        this.thinkingType = normalizeThinkingType(thinkingType);
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();
        this.objectMapper = new ObjectMapper();
    }

    public boolean isConfigured() {
        return !apiKey.isEmpty() && !endpointModelId.isEmpty();
    }

    private static String normalizeThinkingType(String s) {
        if (s == null || s.isBlank()) {
            return null;
        }
        String t = s.trim().toLowerCase();
        if ("enabled".equals(t) || "disabled".equals(t) || "auto".equals(t)) {
            return t;
        }
        return null;
    }

    public ChatCompletionReply chatMessagesDetailed(String systemPrompt, List<ChatMessage> messages)
            throws IOException, InterruptedException {
        return chatMessagesDetailed(systemPrompt, messages, false);
    }

    /**
     * 可选 {@code response_format.type=json_object}（OpenAI 兼容）；若接入点返回 HTTP 400，请改为 {@code false}。
     */
    public ChatCompletionReply chatMessagesDetailed(
            String systemPrompt,
            List<ChatMessage> messages,
            boolean jsonObjectResponseFormat)
            throws IOException, InterruptedException {
        if (!isConfigured()) {
            throw new IllegalStateException("LLM 未配置：请设置 API key 与 endpoint/model id");
        }
        Objects.requireNonNull(messages, "messages");

        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", endpointModelId);
        body.put("temperature", 0.2);
        if (reasoningEffort != null && !"disabled".equals(thinkingType)) {
            body.put("reasoning_effort", reasoningEffort);
        }
        if (thinkingType != null) {
            body.putObject("thinking").put("type", thinkingType);
        }
        if (jsonObjectResponseFormat) {
            body.putObject("response_format").put("type", "json_object");
        }

        ArrayNode arr = body.putArray("messages");
        if (systemPrompt != null && !systemPrompt.isBlank()) {
            ObjectNode sys = arr.addObject();
            sys.put("role", "system");
            sys.put("content", systemPrompt);
        }
        for (ChatMessage m : messages) {
            if (m == null || m.role() == null || m.content() == null) {
                continue;
            }
            String r = m.role().trim().toLowerCase();
            if (!"user".equals(r) && !"assistant".equals(r)) {
                continue;
            }
            ObjectNode node = arr.addObject();
            node.put("role", r);
            node.put("content", m.content());
        }

        String json = objectMapper.writeValueAsString(body);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl))
                .timeout(Duration.ofSeconds(120))
                .header("Content-Type", "application/json; charset=utf-8")
                .header("Authorization", "Bearer " + apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> response =
                httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("Chat API HTTP " + response.statusCode() + ": " + response.body());
        }
        return extractAssistantReply(response.body());
    }

    public ChatCompletionReply extractAssistantReply(String responseJsonBody) throws IOException {
        JsonNode root = objectMapper.readTree(responseJsonBody);
        if (root.hasNonNull("error")) {
            throw new IOException("API 返回 error: " + root.get("error"));
        }
        JsonNode choice0 = root.path("choices").path(0);
        JsonNode message = choice0.path("message");

        String finalText = flattenTextNode(message.path("content"));
        if (finalText.isBlank()) {
            finalText = flattenTextNode(message.path("text"));
        }
        if (finalText.isBlank()) {
            finalText = flattenTextNode(choice0.path("text"));
        }
        if (finalText.isBlank()) {
            finalText = flattenTextNode(root.path("output_text"));
        }

        String reasoningText = flattenTextNode(message.path("reasoning_content"));
        return new ChatCompletionReply(finalText, reasoningText);
    }

    private static String flattenTextNode(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return "";
        }
        if (node.isTextual()) {
            return node.asText("");
        }
        if (node.isArray()) {
            StringBuilder sb = new StringBuilder();
            for (JsonNode item : node) {
                if (item == null || item.isNull()) {
                    continue;
                }
                if (item.isTextual()) {
                    appendLine(sb, item.asText(""));
                    continue;
                }
                if (item.isObject()) {
                    appendLine(sb, item.path("text").asText(""));
                    appendLine(sb, item.path("content").asText(""));
                    appendLine(sb, item.path("value").asText(""));
                }
            }
            return sb.toString().trim();
        }
        if (node.isObject()) {
            StringBuilder sb = new StringBuilder();
            appendLine(sb, node.path("text").asText(""));
            appendLine(sb, node.path("content").asText(""));
            appendLine(sb, node.path("value").asText(""));
            String merged = sb.toString().trim();
            return merged.isEmpty() ? node.toString() : merged;
        }
        return node.asText("");
    }

    private static void appendLine(StringBuilder sb, String text) {
        if (text == null || text.isBlank()) {
            return;
        }
        if (sb.length() > 0) {
            sb.append('\n');
        }
        sb.append(text.trim());
    }

    public static List<ChatMessage> singleUserMessage(String text) {
        List<ChatMessage> list = new ArrayList<>(1);
        list.add(new ChatMessage("user", text != null ? text : ""));
        return list;
    }

    public record ChatMessage(String role, String content) {}

    /** choices[0].message 解析后的正文与推理链（若平台返回）。 */
    public record ChatCompletionReply(String finalText, String reasoningText) {}
}
