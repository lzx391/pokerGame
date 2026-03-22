package com.example.mgdemoplus.service.serviceImpl.dp;

import com.example.mgdemoplus.entity.dp.DpPlayer;
import com.example.mgdemoplus.entity.dp.DpRoom;
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
     * 极短 system，省输入 token；具体规则在 user 里一行即可。
     */
    private static final String LLM_SYSTEM_PROMPT =
            "NLHE bot BOT_LLM. Reply with one JSON object only, no markdown: "
                    + "{\"action\":\"FOLD|CALL_OR_CHECK|RAISE|ALL_IN\",\"chips_to_add\":int}. "
                    + "chips_to_add = extra chips added this action; use 0 for FOLD/CALL_OR_CHECK.";

    private static final long PRE_API_DELAY_MS = 200L;
    private static final Pattern JSON_BLOCK = Pattern.compile("```(?:json)?\\s*([\\s\\S]*?)```", Pattern.CASE_INSENSITIVE);

    private final ExecutorService llmExecutor = Executors.newFixedThreadPool(4, r -> {
        Thread t = new Thread(r, "dp-llm-npc");
        t.setDaemon(true);
        return t;
    });

    private final ConcurrentHashMap<String, Inflight> inflightByKey = new ConcurrentHashMap<>();
    private final LlmNpc llmNpc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public DpLlmNpcDecisionService(
            @Value("${dp.llm.ark.api-key:}") String propApiKey,
            @Value("${dp.llm.ark.endpoint-id:}") String propEndpointId,
            @Value("${dp.llm.ark.base-url:}") String propBaseUrl) {
        String apiKey = firstNonBlank(propApiKey, env("ARK_API_KEY"));
        String endpointId = firstNonBlank(propEndpointId, env("ARK_ENDPOINT_ID"));
        String baseUrl = firstNonBlank(propBaseUrl, env("ARK_BASE_URL"));
        this.llmNpc = new LlmNpc(apiKey, endpointId, baseUrl.isEmpty() ? null : baseUrl);
    }

    private static String env(String name) {
        String v = System.getenv(name);
        return v == null ? "" : v.trim();
    }

    /** 配置文件优先，否则用环境变量。 */
    private static String firstNonBlank(String prop, String envVal) {
        if (prop != null && !prop.isBlank()) {
            return prop.trim();
        }
        return envVal != null && !envVal.isBlank() ? envVal.trim() : "";
    }

    private record LlmActionTicket(
            long handSeed,
            String stage,
            int actorIndex,
            String botNick,
            int heroBet,
            int betToCall
    ) {
        boolean stillValid(DpRoom room, DpPlayer bot) {
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

    private static final class Inflight {
        final CompletableFuture<DpNpcEngine.BotAction> future;
        final LlmActionTicket ticket;

        Inflight(CompletableFuture<DpNpcEngine.BotAction> future, LlmActionTicket ticket) {
            this.future = future;
            this.ticket = ticket;
        }
    }

    /**
     * 与 {@link DpNpcEngine#decideActionIfReady} 相同调用约定：未到点或请求未完成时返回 null。
     */
    public DpNpcEngine.BotAction decideActionIfReady(DpRoom room, DpPlayer bot) {
        //全是防御性编程
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

        Inflight stale = inflightByKey.get(key);
        if (stale != null && !stale.ticket.stillValid(room, bot)) {
            stale.future.cancel(true);
            inflightByKey.remove(key, stale);
        }

        Inflight slot = inflightByKey.get(key);
        long now = System.currentTimeMillis();
        long nextTime = bot.getNextBotActionTime();
//思考时间设置
        if (slot == null) {
            if (nextTime <= 0L) {//设置行动时间
                bot.setNextBotActionTime(now + PRE_API_DELAY_MS);
                return null;
            }
            if (now < nextTime) {//如果不到那个时间，就等
                return null;
            }
            LlmActionTicket ticket = new LlmActionTicket(
                    room.getCurrentHandSeed(),
                    room.getCurrentStage(),
                    actorIndex,
                    bot.getNickname(),
                    bot.getBet(),
                    room.getCurrentBetToCall());
            LlmNpcGameContext gameContext = DpNpcEngine.buildLlmNpcGameSnapshot(room, bot);//获取当前情况
            String userPrompt = buildUserPrompt(room, bot, gameContext);

            CompletableFuture<DpNpcEngine.BotAction> future = CompletableFuture
                    .supplyAsync(() -> invokeModel(userPrompt), llmExecutor)
                    .orTimeout(125, TimeUnit.SECONDS)
                    .exceptionally(ex -> {
                        System.out.println("[BOT_LLM] 异步请求异常/超时: " + (ex == null ? "?" : ex.getMessage()));
                        return null;
                    });

            System.out.println("[BOT_LLM] 【已发起大模型请求】room=" + room.getRoomId() + " stage=" + room.getCurrentStage());
            inflightByKey.put(key, new Inflight(future, ticket));
            return null;
        }

        if (!slot.future.isDone()) {
            return null;
        }

        inflightByKey.remove(key);
        bot.setNextBotActionTime(0L);

        if (!slot.ticket.stillValid(room, bot)) {
            return applyLocalFallback(room, bot, "局面已变(与发起请求时不一致，丢弃模型结果)");
        }

        DpNpcEngine.BotAction parsed = slot.future.getNow(null);
        if (parsed == null) {
            return applyLocalFallback(room, bot, "模型返回null/未配置/解析失败/超时");
        }
        DpNpcEngine.BotAction executed = normalizeAndClamp(room, bot, parsed);
        System.out.println("[BOT_LLM] 【采用大模型】解析动作=" + parsed.getType() + " chips_to_add=" + parsed.getAmount()
                + " -> 规范化执行=" + executed.getType() + " amount=" + executed.getAmount());
        return executed;
    }

    /** 未走模型或丢弃模型结果时的本地兜底，统一打日志便于对照控制台。 */
    private static DpNpcEngine.BotAction applyLocalFallback(DpRoom room, DpPlayer bot, String reason) {
        DpNpcEngine.BotAction a = fallback(room, bot);
        System.out.println("[BOT_LLM] 【本地决策】原因=" + reason + " -> " + a.getType() + " amount=" + a.getAmount());
        return a;
    }

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
            String raw = llmNpc.chat(LLM_SYSTEM_PROMPT, userPrompt);
            System.out.println("======== [BOT_LLM] 模型返回原文 ========");
            System.out.println(raw == null ? "(null)" : raw);
            DpNpcEngine.BotAction parsed = parseModelReply(raw);
            if (parsed == null && raw != null && !raw.isBlank()) {
                System.out.println("[BOT_LLM] 提示：模型有正文但 JSON 未解析成动作，主线程将打印【本地决策】");
            }
            if (parsed != null) {
                System.out.println("[BOT_LLM] JSON 已解析为: " + parsed.getType() + " chips_to_add=" + parsed.getAmount());
            }
            return parsed;
        } catch (Exception e) {
            System.err.println("[BOT_LLM] HTTP/调用失败: " + e.getMessage());
            return null;
        }
    }

    /** 本手牌里尚未标记离桌的玩家数（含自己）。≤1 表示没有对手在同一手内。 */
    private static int countPlayersStillInThisHand(DpRoom room) {
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

    private String buildUserPrompt(DpRoom room, DpPlayer bot, LlmNpcGameContext ctx) {
        StringBuilder sb = new StringBuilder(1024);
        sb.append("M BB=").append(DpRoom.getBBChips()).append(" SB=").append(DpRoom.getSBChips())
                .append(" rl=").append(room.getRaiseLevel()).append('\n');
        sb.append("T ").append(compactTable(room, bot)).append('\n');
        if (ctx != null) {
            sb.append(ctx.toPromptBlock());
        }
        sb.append("O H line: hole=your cards, board=community cards (- if none). CALL_OR_CHECK=check/call; RAISE chips_to_add=extra.\n");
        return sb.toString();
    }

    /**
     * 每人一段：昵称,后手,本街注,标记；| 分隔。D=庄 F=弃 A=全下 *=行动者。
     */
    private static String compactTable(DpRoom room, DpPlayer hero) {
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

    private DpNpcEngine.BotAction parseModelReply(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String text = raw.trim();
        Matcher m = JSON_BLOCK.matcher(text);
        if (m.find()) {
            text = m.group(1).trim();
        }
        int brace = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (brace >= 0 && end > brace) {
            text = text.substring(brace, end + 1);
        }
        try {
            JsonNode root = objectMapper.readTree(text);
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
        } catch (Exception e) {
            return null;
        }
    }

    private static DpNpcEngine.BotAction fallback(DpRoom room, DpPlayer bot) {
        int callAmount = Math.max(0, room.getCurrentBetToCall() - bot.getBet());
        if (callAmount <= 0) {
            return new DpNpcEngine.BotAction(DpNpcEngine.BotActionType.CALL_OR_CHECK, 0);
        }
        if (callAmount <= bot.getChips()) {
            return new DpNpcEngine.BotAction(DpNpcEngine.BotActionType.CALL_OR_CHECK, 0);
        }
        return new DpNpcEngine.BotAction(DpNpcEngine.BotActionType.FOLD, 0);
    }

    private static DpNpcEngine.BotAction normalizeAndClamp(DpRoom room, DpPlayer bot, DpNpcEngine.BotAction a) {
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
            int bb = Math.max(1, DpRoom.getBBChips());
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
