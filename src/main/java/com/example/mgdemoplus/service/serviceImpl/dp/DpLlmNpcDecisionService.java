package com.example.mgdemoplus.service.serviceImpl.dp;

import com.example.mgdemoplus.bo.DpRoomBO;
import com.example.mgdemoplus.entity.dp.DpPlayer;
import com.example.mgdemoplus.service.serviceImpl.dp.npc.LlmNpc;
import com.example.mgdemoplus.service.serviceImpl.dp.npc.LlmNpcGameContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * {@code BOT_LLM} 专用：把房间对局摘要发给方舟大模型，解析 JSON 决策为 {@link DpNpcEngine.BotAction}。
 * 与普通规则 NPC（{@link DpNpcEngine#decideActionIfReady}）完全分离。
 */
@Service
public class DpLlmNpcDecisionService {
    /**
     * 规则集中在此，user 仅留字段词典 + 数据，减少重复与 token。
     */
    private static final String LLM_SYSTEM_PROMPT = String.join(
            "",
            "你是 NLHE 的 BOT_LLM 决策器。",
            "【reasoning】仅中文，≤120字或≤3短句：rk+hsl 一句、pot/call 一句、倾向一句。",
            "禁止复述规则、禁止长篇推演 streetBet/底池定义、禁止英文长考；推理只走接口 reasoning 通道。",
            "【牌力】hsl 为用户包真值；与心算冲突以 hsl 为准；不得仅凭 hole 两张定强弱。",
            "【正文】仅输出一个 JSON 对象，键仅限 action、chips_to_add。",
            "action∈FOLD|CALL_OR_CHECK|RAISE|ALL_IN；FOLD/CALL_OR_CHECK 的 chips_to_add=0；",
            "RAISE 的 chips_to_add=本次额外加注筹码；非负；不确定用 {\"action\":\"CALL_OR_CHECK\",\"chips_to_add\":0}。",
            "不要输出 {\"reasoning\":...,\"content\":...}，不要把 JSON 嵌成转义字符串；服务端仍会容错提取。");
    /** 与 system 不重复的极简字段说明；动态 M/T/H/E 紧随其后。 */
    private static final String USER_PROMPT_STATIC_PREFIX = String.join(
            "\n",
            "NLHE 决策包(权威)，顺序 M→T→H→E。",
            "M: BB SB rl",
            "T: nickname,stack,streetBet,tags — D庄 F弃 A全 *=当前行动者",
            "H: rk hsl hole board pos call stk；st/pot 见行内",
            "E: 对手摘要",
            "勿纠格式，直接输出 system 要求的 JSON。");
    /** 轮到 BOT_LLM 后、发起方舟请求前的额外等待；0 表示不人为拖延（总耗时几乎全在 API 侧）。 */
    private static final long PRE_API_DELAY_MS = 0L;
    private static final Pattern JSON_BLOCK = Pattern.compile("```(?:json)?\\s*([\\s\\S]*?)```",
            Pattern.CASE_INSENSITIVE);

    private final ExecutorService llmExecutor = Executors.newFixedThreadPool(4, r -> {
        Thread t = new Thread(r, "dp-llm-npc");
        t.setDaemon(true);
        return t;
    });

    private final ConcurrentHashMap<String, Inflight> inflightByKey = new ConcurrentHashMap<>();
    private final LlmNpc llmNpc;
    private final boolean llmResponseJsonObject;
    private final ObjectMapper objectMapper = new ObjectMapper();

    // 构造器中从配置文件里取输入大模型请求的id，key baseUrl信息
    public DpLlmNpcDecisionService(
            @Value("${dp.llm.ark.api-key:}") String propApiKey,
            @Value("${dp.llm.ark.endpoint-id:}") String propEndpointId,
            @Value("${dp.llm.ark.base-url:}") String propBaseUrl,
            @Value("${dp.llm.ark.reasoning-effort:}") String propReasoningEffort,
            @Value("${dp.llm.ark.thinking-type:}") String propThinkingType,
            @Value("${dp.llm.ark.response-json-object:true}") boolean responseJsonObject) {
        String apiKey = trimToNull(propApiKey);
        String endpointId = trimToNull(propEndpointId);
        String baseUrl = propBaseUrl != null ? propBaseUrl.trim() : "";
        String reasoningEffort = propReasoningEffort != null ? propReasoningEffort.trim() : "";
        String thinkingType = propThinkingType != null ? propThinkingType.trim() : "";
        this.llmResponseJsonObject = responseJsonObject;
        this.llmNpc = new LlmNpc(
                apiKey,
                endpointId,
                baseUrl.isEmpty() ? null : baseUrl,
                reasoningEffort.isEmpty() ? null : reasoningEffort,
                thinkingType.isEmpty() ? null : thinkingType);
    }

    private static String trimToNull(String s) {
        if (s == null || s.isBlank()) {
            return null;
        }
        return s.trim();
    }

    // 已学习，检查快照是否有效的类和方法
    private record LlmActionTicket(
            long handSeed,
            String stage,
            int actorIndex,
            String botNick,
            int heroBet,
            int betToCall) {
        boolean stillValid(DpRoomBO room, DpPlayer bot) {
            if (room == null || bot == null) {
                return false;
            }
            if (!DpNpcEngine.LLM_BOT_NICKNAME.equals(bot.getNickname())) {
                return false;
            }
            if (room.getCurrentActorIndex() != actorIndex) {
                return false;
            }
            if (!Objects.equals(botNick, bot.getNickname())) {
                return false;
            }
            if (!Objects.equals(stage, room.getCurrentStage())) {
                return false;
            }
            if (room.getCurrentBetToCall() != betToCall) {
                return false;
            }
            if (bot.getBet() != heroBet) {
                return false;
            }
            return room.getCurrentHandSeed() == handSeed;
        }
    }

    // 已学习，这个是用来保存future异步任务信息，ticket保存离开前的桌面快照，说白了没有这个就不知道是否有在途任务，也无法取消它
    private static final class Inflight {
        final CompletableFuture<DpNpcEngine.BotAction> future;
        final LlmActionTicket ticket;

        Inflight(CompletableFuture<DpNpcEngine.BotAction> future, LlmActionTicket ticket) {
            this.future = future;
            this.ticket = ticket;
        }
    }

    // 已学习，决策核心
    /**
     * 与 {@link DpNpcEngine#decideActionIfReady} 相同调用约定：未到点或请求未完成时返回 null。
     */
    public DpNpcEngine.BotAction decideActionIfReady(DpRoomBO room, DpPlayer bot) {
        // 全是防御性编程
        if (room == null || bot == null || !room.isPlaying()) {
            return null;
        }
        int actorIndex = room.getCurrentActorIndex();
        if (actorIndex < 0 || actorIndex >= room.getPlayers().size()) {
            return null;
        }
        if (room.getPlayers().get(actorIndex) != bot) {
            return null;
        }
        if (!DpNpcEngine.LLM_BOT_NICKNAME.equals(bot.getNickname())) {
            return null;
        }
        if (bot.isFold() || bot.isAllIn() || bot.isLeftThisHand()) {
            return null;
        }

        String key = room.getRoomId() + "|" + bot.getNickname();

        // 本手未离桌的只有自己时，不再调用大模型（省 token，也避免“真空”乱决策）
        if (countPlayersStillInThisHand(room) <= 1) {
            bot.setNextBotActionTime(0L);
            Inflight cancelled = inflightByKey.remove(key);
            if (cancelled != null) {
                cancelled.future.cancel(true);
            }
            return applyLocalFallback(room, bot, "桌上仅1名未离桌玩家(不请求大模型)");
        }
        // 如果这个在途请求有，但是快照变了，那么就要撤销这个请求，消耗这个key
        Inflight stale = inflightByKey.get(key);
        if (stale != null && !stale.ticket.stillValid(room, bot)) {
            stale.future.cancel(true);
            inflightByKey.remove(key, stale);
        }
        // 如果都没问题的话，正片开始
        Inflight slot = inflightByKey.get(key);// 这里看有没有在途任务
        // 这里分别用stale和slot检测两次是因为stale是检测快照是否有效，slot是检测在途任务是否存在
        long now = System.currentTimeMillis();
        long nextTime = bot.getNextBotActionTime();
        // 思考时间设置
        // 这里检测slot是因为如果是null说明没有在途任务，需要设置思考时间
        if (slot == null) {
            if (nextTime <= 0L) {// 设置行动时间
                bot.setNextBotActionTime(now + PRE_API_DELAY_MS);
                return null;
            }
            if (now < nextTime) {// 如果不到那个时间，就等
                return null;
            }
            LlmActionTicket ticket = new LlmActionTicket(
                    room.getCurrentHandSeed(),
                    room.getCurrentStage(),
                    actorIndex,
                    bot.getNickname(),
                    bot.getBet(),
                    room.getCurrentBetToCall());
            LlmNpcGameContext gameContext = DpNpcEngine.buildLlmNpcGameSnapshot(room, bot);// 获取当前情况,Mapper相当于转接器，把smartcontext信息利用了
            String userPrompt = buildUserPrompt(room, bot, gameContext);// 用的是GameContext的方法，打包压缩送给大模型的信息
            // 异步发送打包信息，把传回来的信息给future
            CompletableFuture<DpNpcEngine.BotAction> future = CompletableFuture
                    .supplyAsync(() -> invokeModel(userPrompt), llmExecutor)
                    .orTimeout(125, TimeUnit.SECONDS)
                    .exceptionally(ex -> {
                        System.out.println("[BOT_LLM] 异步请求异常/超时: " + (ex == null ? "?" : ex.getMessage()));
                        return null;
                    });

            System.out.println("[BOT_LLM] 【已发起大模型请求】room=" + room.getRoomId() + " stage=" + room.getCurrentStage());
            inflightByKey.put(key, new Inflight(future, ticket));// 钥匙和在途任务以及快照信息，等请求完成了，用key取出来结果核验快照
            return null;
        }
        // 如果返回了就删掉key，没返回就卡着
        if (!slot.future.isDone()) {
            return null;
        }

        inflightByKey.remove(key);
        bot.setNextBotActionTime(0L);
        // 快照过期了，返回的决策不能用了，执行兜底决策
        if (!slot.ticket.stillValid(room, bot)) {// 执行兜底决策
            return applyLocalFallback(room, bot, "局面已变(与发起请求时不一致，丢弃模型结果)");
        }
        // 模型返回null也走兜底
        DpNpcEngine.BotAction parsed = slot.future.getNow(null);
        if (parsed == null) {
            return applyLocalFallback(room, bot, "模型返回null/未配置/解析失败/超时");
        }
        // 正常返回之后处理信息，有时候LLM返回的信息并不能实行，比如筹码剩下200了，他却要raise250，所以需要再最后把把关，弄成符合游戏实际的操作返回
        DpNpcEngine.BotAction executed = normalizeAndClamp(room, bot, parsed);
        System.out.println("[BOT_LLM] 【采用大模型】解析动作=" + parsed.getType() + " chips_to_add=" + parsed.getAmount()
                + " -> 规范化执行=" + executed.getType() + " amount=" + executed.getAmount());
        return executed;
    }

    // 已学习，离线决策
    /** 未走模型或丢弃模型结果时的本地兜底，统一打日志便于对照控制台。 */
    private static DpNpcEngine.BotAction applyLocalFallback(DpRoomBO room, DpPlayer bot, String reason) {
        DpNpcEngine.BotAction a = fallback(room, bot);
        System.out.println("[BOT_LLM] 【本地决策】原因=" + reason + " -> " + a.getType() + " amount=" + a.getAmount());
        return a;
    }

    // 已学习，返回决策结果
    private DpNpcEngine.BotAction invokeModel(String userPrompt) {
        if (!llmNpc.isConfigured()) {
            System.out.println("[BOT_LLM] 未配置密钥/接入点，跳过请求");
            return null;
        }
        System.out.println("======== [BOT_LLM] 输入 system ========");
        System.out.println(LLM_SYSTEM_PROMPT);
        System.out.println("======== [BOT_LLM] 输入 user ========");
        System.out.println(userPrompt);
        try {
            // 把大模型key id 提示词 信息包输入进去
            LlmNpc.LlmReply reply = llmNpc.chatMessagesDetailed(
                    LLM_SYSTEM_PROMPT,
                    LlmNpc.singleUserMessage(userPrompt),
                    llmResponseJsonObject);
            String raw = reply == null ? "" : reply.finalText();
            String reasoning = reply == null ? "" : reply.reasoningText();
            System.out.println("======== [BOT_LLM] 模型返回原文 ========");
            System.out.println(raw == null ? "(null)" : raw);
            if (reasoning != null && !reasoning.isBlank()) {
                System.out.println("======== [BOT_LLM] 模型思考摘要 ========");
                System.out.println(reasoning);
            }
            LlmParseResult parsed = parseModelReply(raw);
            DpNpcEngine.BotAction action = parsed == null ? null : parsed.action();
            if (action == null && raw != null && !raw.isBlank()) {
                System.out.println("[BOT_LLM] 提示：模型有正文但 JSON 未解析成动作，主线程将打印【本地决策】");
            }
            if (parsed != null && action != null) {
                System.out.println("[BOT_LLM] JSON 已解析 path=" + parsed.path() + " -> " + action.getType()
                        + " chips_to_add=" + action.getAmount());
            }
            return action;
        } catch (Exception e) {
            System.err.println("[BOT_LLM] HTTP/调用失败: " + e.getMessage());
            return null;
        }
    }

    // 已学习，防御性编程
    /** 本手牌里尚未标记离桌的玩家数（含自己）。≤1 表示没有对手在同一手内。 */
    private static int countPlayersStillInThisHand(DpRoomBO room) {
        List<DpPlayer> ps = room.getPlayers();
        if (ps == null) {
            return 0;
        }
        int n = 0;
        for (DpPlayer p : ps) {
            if (p != null && !p.isLeftThisHand()) {
                n++;
            }
        }
        return n;
    }

    // 已学习，构建喂给AI的信息包
    private String buildUserPrompt(DpRoomBO room, DpPlayer bot, LlmNpcGameContext ctx) {
        StringBuilder sb = new StringBuilder(1024);
        sb.append(USER_PROMPT_STATIC_PREFIX).append('\n');
        sb.append("M BB=").append(room.getBigBlindChips()).append(" SB=").append(room.getSmallBlindChips())
                .append(" rl=").append(room.getRaiseLevel()).append('\n');
        sb.append("T ").append(compactTable(room, bot)).append('\n');
        if (ctx != null) {
            sb.append(ctx.toPromptBlock());// LNGameContext的方法
        }
        return sb.toString();
    }

    // 已学习，用于拼接场上信息
    /**
     * 每人一段：昵称,后手,本街注,标记；| 分隔。D=庄 F=弃 A=全下 *=行动者。
     */
    private static String compactTable(DpRoomBO room, DpPlayer hero) {
        StringBuilder sb = new StringBuilder();
        List<DpPlayer> ps = room.getPlayers();
        if (ps == null) {
            return "";
        }
        for (DpPlayer p : ps) {
            if (p == null || p.isLeftThisHand()) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append('|');
            }
            sb.append(p.getNickname()).append(',').append(p.getChips()).append(',').append(p.getBet());
            if (p.isDealer()) {
                sb.append(",D");
            }
            if (p.isFold()) {
                sb.append(",F");
            }
            if (p.isAllIn()) {
                sb.append(",A");
            }
            if (p == hero) {
                sb.append(",*");
            }
        }
        return sb.toString();
    }

    /** 解析结果 + 来源，便于对照日志与模型行为。 */
    private record LlmParseResult(DpNpcEngine.BotAction action, String path) {}

    // 已学习，获取LLM返回结果并打包npc决策格式的数据结构
    /**
     * 兼容历史行为：模型常在正文里夹 reasoning、或输出 {@code {"reasoning","content":"..."}}，
     * 或 {@code content\{...}} 等非严格单 JSON。此处尽量抽出含 {@code action} 的对象。
     */
    private LlmParseResult parseModelReply(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String trimmed = raw.trim();
        LlmParseResult r = tryParseDecisionJson(trimmed, 0, false);
        if (r != null) {
            return r;
        }
        return scanJsonObjectsForAction(trimmed);
    }

    private LlmParseResult tryParseDecisionJson(String text, int depth, boolean fromContentUnwrap) {
        if (text == null || text.isBlank() || depth > 6) {
            return null;
        }
        String work = text.trim();
        Matcher m = JSON_BLOCK.matcher(work);
        if (m.find()) {
            work = m.group(1).trim();
        }
        int brace = work.indexOf('{');
        int end = work.lastIndexOf('}');
        if (brace >= 0 && end > brace) {
            work = work.substring(brace, end + 1);
        }
        final JsonNode root;
        try {
            root = objectMapper.readTree(work);
        } catch (Exception e) {
            return null;
        }
        DpNpcEngine.BotAction direct = botActionFromJsonNode(root);
        if (direct != null) {
            return new LlmParseResult(direct, fromContentUnwrap ? "unwrap" : "top");
        }
        JsonNode content = root.get("content");
        if (content != null && content.isTextual()) {
            LlmParseResult inner = tryParseDecisionJson(content.asText(), depth + 1, true);
            if (inner != null) {
                return inner;
            }
        }
        if (content != null && content.isObject()) {
            DpNpcEngine.BotAction nested = botActionFromJsonNode(content);
            if (nested != null) {
                return new LlmParseResult(nested, "unwrap");
            }
        }
        return null;
    }

    private LlmParseResult scanJsonObjectsForAction(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) != '{') {
                continue;
            }
            int close = indexOfMatchingCloseBrace(text, i);
            if (close < 0) {
                continue;
            }
            String slice = text.substring(i, close + 1);
            try {
                JsonNode node = objectMapper.readTree(slice);
                DpNpcEngine.BotAction a = botActionFromJsonNode(node);
                if (a != null) {
                    return new LlmParseResult(a, "scan");
                }
                LlmParseResult inner = tryParseDecisionJson(slice, 1, false);
                if (inner != null) {
                    return new LlmParseResult(inner.action(), "scan");
                }
            } catch (Exception ignored) {
                // try next '{'
            }
        }
        return null;
    }

    /** 忽略字符串内的括号，避免误判。 */
    private static int indexOfMatchingCloseBrace(String s, int openIdx) {
        int depth = 0;
        boolean inString = false;
        boolean escape = false;
        for (int i = openIdx; i < s.length(); i++) {
            char c = s.charAt(i);
            if (escape) {
                escape = false;
                continue;
            }
            if (c == '\\' && inString) {
                escape = true;
                continue;
            }
            if (c == '"') {
                inString = !inString;
                continue;
            }
            if (inString) {
                continue;
            }
            if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0) {
                    return i;
                }
            }
        }
        return -1;
    }

    private DpNpcEngine.BotAction botActionFromJsonNode(JsonNode root) {
        if (root == null || !root.has("action")) {
            return null;
        }
        String action = root.path("action").asText("").trim().toUpperCase();
        int chipsToAdd = root.path("chips_to_add").asInt(0);
        if (chipsToAdd < 0) {
            chipsToAdd = 0;
        }
        return switch (action) {
            case "FOLD" -> new DpNpcEngine.BotAction(DpNpcEngine.BotActionType.FOLD, 0);
            case "CALL_OR_CHECK", "CHECK", "CALL", "CHECK_OR_CALL" ->
                new DpNpcEngine.BotAction(DpNpcEngine.BotActionType.CALL_OR_CHECK, 0);
            case "ALL_IN", "ALLIN" -> new DpNpcEngine.BotAction(DpNpcEngine.BotActionType.ALL_IN, chipsToAdd);
            case "RAISE", "BET" -> new DpNpcEngine.BotAction(DpNpcEngine.BotActionType.RAISE, chipsToAdd);
            default -> null;
        };
    }

    // 已学习，简单离线决策，有钱直接跟或者过牌，没钱直接弃
    private static DpNpcEngine.BotAction fallback(DpRoomBO room, DpPlayer bot) {
        int callAmount = Math.max(0, room.getCurrentBetToCall() - bot.getBet());
        if (callAmount <= 0) {
            return new DpNpcEngine.BotAction(DpNpcEngine.BotActionType.CALL_OR_CHECK, 0);
        }
        if (callAmount <= bot.getChips()) {
            return new DpNpcEngine.BotAction(DpNpcEngine.BotActionType.CALL_OR_CHECK, 0);
        }
        return new DpNpcEngine.BotAction(DpNpcEngine.BotActionType.FOLD, 0);
    }

    // 已学习，LLM决策修正
    private static DpNpcEngine.BotAction normalizeAndClamp(DpRoomBO room, DpPlayer bot, DpNpcEngine.BotAction a) {
        int callAmount = Math.max(0, room.getCurrentBetToCall() - bot.getBet());
        int chips = bot.getChips();
        if (a.getType() == DpNpcEngine.BotActionType.FOLD) {
            return new DpNpcEngine.BotAction(DpNpcEngine.BotActionType.FOLD, 0);
        }
        if (a.getType() == DpNpcEngine.BotActionType.CALL_OR_CHECK) {
            if (callAmount > chips) {
                return new DpNpcEngine.BotAction(DpNpcEngine.BotActionType.FOLD, 0);
            }
            return new DpNpcEngine.BotAction(DpNpcEngine.BotActionType.CALL_OR_CHECK, 0);
        }
        if (a.getType() == DpNpcEngine.BotActionType.ALL_IN) {
            return new DpNpcEngine.BotAction(DpNpcEngine.BotActionType.ALL_IN, Math.max(0, chips));
        }
        if (a.getType() == DpNpcEngine.BotActionType.RAISE) {
            int add = Math.max(0, a.getAmount());
            if (chips <= 0) {
                return new DpNpcEngine.BotAction(DpNpcEngine.BotActionType.FOLD, 0);
            }
            if (add >= chips) {
                return new DpNpcEngine.BotAction(DpNpcEngine.BotActionType.ALL_IN, chips);
            }
            int newTotal = bot.getBet() + add;
            int bb = Math.max(1, room.getBigBlindChips());
            int minTotal = room.getCurrentBetToCall() + bb;
            if (newTotal <= room.getCurrentBetToCall()) {
                newTotal = minTotal;
            }
            if (newTotal < minTotal) {
                newTotal = minTotal;
            }
            int chipsNeeded = newTotal - bot.getBet();
            if (chipsNeeded > chips) {
                return new DpNpcEngine.BotAction(DpNpcEngine.BotActionType.ALL_IN, chips);
            }
            if (chipsNeeded <= 0) {
                return fallback(room, bot);
            }
            return new DpNpcEngine.BotAction(DpNpcEngine.BotActionType.RAISE, chipsNeeded);
        }
        return fallback(room, bot);
    }

    @PreDestroy
    public void shutdown() {
        llmExecutor.shutdown();
    }
}
