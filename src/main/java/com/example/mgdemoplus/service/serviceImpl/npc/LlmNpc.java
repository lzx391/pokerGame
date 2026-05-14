package com.example.mgdemoplus.service.serviceImpl.npc;

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
 * 通过火山方舟（豆包等）OpenAI 兼容接口调用大模型，用于 NPC 台词或决策辅助。
 * <p>
 * 不需要安装火山引擎本地 SDK：使用 JDK {@link HttpClient} 访问
 * {@code https://ark.cn-beijing.volces.com/api/v3/chat/completions} 即可。
 * <p>
 * 密钥请用环境变量 {@code ARK_API_KEY}；接入点 ID 用 {@code ARK_ENDPOINT_ID}（形如
 * {@code ep-...}）。
 * 不要把密钥写进代码仓库或提交到 Git。
 */
public final class LlmNpc {

    // ========================= 常量配置 =========================
    public static final String DEFAULT_ARK_BASE_URL = "https://ark.cn-beijing.volces.com/api/v3/chat/completions";

    // ========================= 对象属性 =========================
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

    // ========================= 构造器 =========================
    /**
     * 构造器，提供API key、模型id、baseUrl，reasoningEffort和thinkingType为null
     */
    public LlmNpc(String apiKey, String endpointModelId, String baseUrl) {
        this(apiKey, endpointModelId, baseUrl, null, null);
    }

    /**
     * 构造器，提供API key、模型id、baseUrl、reasoningEffort，thinkingType为null
     */
    public LlmNpc(String apiKey, String endpointModelId, String baseUrl, String reasoningEffort) {
        this(apiKey, endpointModelId, baseUrl, reasoningEffort, null);
    }

    /**
     * 构造器，提供API key、模型id、baseUrl、reasoningEffort、thinkingType
     */
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

    // ========================= 配置校验 =========================
    /**
     * 检查配置是否齐全（API key和接入点ID都不为空）
     *
     * @return 配置齐全返回true，否则返回false
     */
    public boolean isConfigured() {
        return !apiKey.isEmpty() && !endpointModelId.isEmpty();
    }

    /**
     * 合法化thinkingType，仅保留文档允许的值，非法则返回null
     */
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

    // ========================= 公共业务调用入口 =========================

    /**
     * 多轮对话接口（结构化结果）：可获取模型回复正文和推理链
     * 
     * @param systemPrompt 系统设定
     * @param messages     多轮消息（role仅支持user/assistant）
     * @return LlmReply对象，含finalText和reasoningText
     */
    public LlmReply chatMessagesDetailed(String systemPrompt, List<ChatMessage> messages)
            throws IOException, InterruptedException {
        return chatMessagesDetailed(systemPrompt, messages, false);
    }

    /**
     * 同 {@link #chatMessagesDetailed(String, List)}，可选请求
     * {@code response_format.type=json_object}（OpenAI 兼容），
     * 用于强制助手正文为 JSON，减少夹带说明；若与当前接入点/思考模式不兼容导致 HTTP 400，请改回 {@code false}。
     */
    public LlmReply chatMessagesDetailed(
            String systemPrompt,
            List<ChatMessage> messages,
            boolean jsonObjectResponseFormat)
            throws IOException, InterruptedException {
        if (!isConfigured()) {
            throw new IllegalStateException(
                    "LLM 未配置：请设置环境变量 ARK_API_KEY 与 ARK_ENDPOINT_ID（方舟推理接入点 ID）");
        }
        Objects.requireNonNull(messages, "messages");

        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", endpointModelId);
        body.put("temperature", 0.2);
        // 不再硬编码 max_tokens，避免思考模式下正文被截断。
        // 方舟：thinking.type=disabled 时不可与 reasoning_effort（如 low）同传，否则 400
        // InvalidParameter
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

        HttpResponse<String> response = httpClient.send(request,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("方舟接口 HTTP " + response.statusCode() + ": " + response.body());
        }
        return extractAssistantReply(response.body());
    }

    // ========================= 结果解析工具 =========================

    /**
     * 提取assistant回复，返回结构体（含正文和reasoning内容）
     * 
     * @param responseJsonBody ark接口返回的JSON字符串
     * @return LlmReply对象，含assistant正文和reasoning链
     */
    public LlmReply extractAssistantReply(String responseJsonBody) throws IOException {
        JsonNode root = objectMapper.readTree(responseJsonBody);
        if (root.hasNonNull("error")) {
            throw new IOException("方舟返回 error: " + root.get("error"));
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
        return new LlmReply(finalText, reasoningText);
    }

    /**
     * 将一个复杂的json文本节点拍平成字符串，递归策略
     * 
     * @param node 待拍平的JsonNode
     * @return 字符串内容
     */
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

    /**
     * 辅助：如果给定字符串非空，给StringBuilder追加一行，自动换行
     * 
     * @param sb   StringBuilder
     * @param text 要添加的文本
     */
    private static void appendLine(StringBuilder sb, String text) {
        if (text == null || text.isBlank()) {
            return;
        }
        if (sb.length() > 0) {
            sb.append('\n');
        }
        sb.append(text.trim());
    }

    // ========================= 辅助静态工具 =========================

    /**
     * 工具方法：生成单条user消息的消息列表
     * 
     * @param text 用户内容
     * @return 只包含一条user消息的列表
     */
    public static List<ChatMessage> singleUserMessage(String text) {
        List<ChatMessage> list = new ArrayList<>(1);
        list.add(new ChatMessage("user", text != null ? text : ""));
        return list;
    }

    // ========================= 内部数据类型 =========================

    /**
     * LLM对话消息结构体，role和content字段
     */
    public record ChatMessage(String role, String content) {
    }

    /**
     * LLM返回结构体，含assistant回复正文和reasoning链
     */
    public record LlmReply(String finalText, String reasoningText) {
    }
}
