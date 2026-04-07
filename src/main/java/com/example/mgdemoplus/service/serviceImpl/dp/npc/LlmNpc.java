package com.example.mgdemoplus.service.serviceImpl.dp.npc;

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
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * 通过火山方舟（豆包等）OpenAI 兼容接口调用大模型，用于 NPC 台词或决策辅助。
 * <p>
 * 不需要安装火山引擎本地 SDK：使用 JDK {@link HttpClient} 访问
 * {@code https://ark.cn-beijing.volces.com/api/v3/chat/completions} 即可。
 * <p>
 * 密钥请用环境变量 {@code ARK_API_KEY}；接入点 ID 用 {@code ARK_ENDPOINT_ID}（形如 {@code ep-...}）。
 * 不要把密钥写进代码仓库或提交到 Git。
 */
public final class LlmNpc {

    public static final String DEFAULT_ARK_BASE_URL = "https://ark.cn-beijing.volces.com/api/v3/chat/completions";

    private final String apiKey;
    private final String endpointModelId;
    private final String baseUrl;
    /**
     * 方舟文档 {@code reasoning_effort}（Chat API）：调节思维链长度；默认多为 medium。
     * 为空则不写入请求体。
     */
    private final String reasoningEffort;
    /**
     * 方舟文档 {@code thinking.type}：{@code enabled} / {@code disabled} / {@code auto}；
     * 若接入点为深度思考模型且未传，服务端对 Seed 系列多为 {@code enabled}，会明显变慢。
     * 为空则不写入请求体。
     */
    private final String thinkingType;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    /**
     * 从环境变量读取 {@code ARK_API_KEY}、{@code ARK_ENDPOINT_ID}，可选
     * {@code ARK_BASE_URL}、{@code ARK_REASONING_EFFORT}、{@code ARK_THINKING_TYPE}。
     */
    public LlmNpc() {
        this(
                trimToNull(System.getenv("ARK_API_KEY")),
                trimToNull(System.getenv("ARK_ENDPOINT_ID")),
                trimToNull(System.getenv("ARK_BASE_URL")),
                trimToNull(System.getenv("ARK_REASONING_EFFORT")),
                trimToNull(System.getenv("ARK_THINKING_TYPE")));
    }

    public LlmNpc(String apiKey, String endpointModelId, String baseUrl) {
        this(apiKey, endpointModelId, baseUrl, null, null);
    }

    public LlmNpc(String apiKey, String endpointModelId, String baseUrl, String reasoningEffort) {
        this(apiKey, endpointModelId, baseUrl, reasoningEffort, null);
    }

    public LlmNpc(
            String apiKey,
            String endpointModelId,
            String baseUrl,
            String reasoningEffort,
            String thinkingType) {
        this.apiKey = apiKey != null ? apiKey : "";
        this.endpointModelId = endpointModelId != null ? endpointModelId : "";
        this.baseUrl = (baseUrl != null && !baseUrl.isBlank()) ? baseUrl.trim() : DEFAULT_ARK_BASE_URL;
        this.reasoningEffort = (reasoningEffort != null && !reasoningEffort.isBlank()) ? reasoningEffort.trim() : null;
        this.thinkingType = normalizeThinkingType(thinkingType);
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .build();
        this.objectMapper = new ObjectMapper();
    }

    /** 仅允许文档中的取值，非法则忽略（不写请求体）。 */
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

    public String getApiKey() {
        return apiKey;
    }

    public String getEndpointModelId() {
        return endpointModelId;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public boolean isConfigured() {
        return !apiKey.isEmpty() && !endpointModelId.isEmpty();
    }

    /**
     * 原始 chat/completions：system + user 各一条。
     */
    public String chat(String systemPrompt, String userMessage) throws IOException, InterruptedException {
        return chatMessages(
                systemPrompt,
                Collections.singletonList(new ChatMessage("user", userMessage)));
    }

    /**
     * system 一条 + 多轮 user/assistant（用于多轮对话）；roles 仅支持 user / assistant。
     */
    public String chatMessages(String systemPrompt, List<ChatMessage> messages)//含多轮对话
            throws IOException, InterruptedException {
        if (!isConfigured()) {
            throw new IllegalStateException(
                    "LLM 未配置：请设置环境变量 ARK_API_KEY 与 ARK_ENDPOINT_ID（方舟推理接入点 ID）");
        }
        Objects.requireNonNull(messages, "messages");

        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", endpointModelId);
        body.put("temperature", 0.2);
        // 决策只需几十个 token，压低输出侧计费与胡扯长度
        body.put("max_tokens", 64);
        // 方舟：thinking.type=disabled 时不可与 reasoning_effort（如 low）同传，否则 400 InvalidParameter
        if (reasoningEffort != null && !"disabled".equals(thinkingType)) {
            body.put("reasoning_effort", reasoningEffort);
        }
        if (thinkingType != null) {
            body.putObject("thinking").put("type", thinkingType);
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

        String json = objectMapper.writeValueAsString(body);//把body转换成json字符串
        HttpRequest request = HttpRequest.newBuilder()//构建请求
                .uri(URI.create(baseUrl))
                .timeout(Duration.ofSeconds(120))
                .header("Content-Type", "application/json; charset=utf-8")
                .header("Authorization", "Bearer " + apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("方舟接口 HTTP " + response.statusCode() + ": " + response.body());
        }
        return extractAssistantText(response.body());
    }

    public String extractAssistantText(String responseJsonBody) throws IOException {
        JsonNode root = objectMapper.readTree(responseJsonBody);
        if (root.hasNonNull("error")) {
            throw new IOException("方舟返回 error: " + root.get("error"));
        }
        JsonNode content = root.path("choices").path(0).path("message").path("content");
        if (content.isMissingNode() || content.isNull()) {
            return "";
        }
        if (content.isTextual()) {
            return content.asText("");
        }
        return content.toString();
    }

    public static List<ChatMessage> singleUserMessage(String text) {
        List<ChatMessage> list = new ArrayList<>(1);
        list.add(new ChatMessage("user", text != null ? text : ""));
        return list;
    }

    private static String trimToNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    public record ChatMessage(String role, String content) {
    }
}
