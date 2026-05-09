package com.example.mgdemoplus.service.serviceImpl.dp;

import com.example.mgdemoplus.bo.DpRoomBO;
import com.example.mgdemoplus.entity.dp.DpPlayer;
import com.example.mgdemoplus.service.serviceImpl.dp.npc.LlmNpc;
import com.example.mgdemoplus.service.serviceImpl.dp.npc.LlmNpcGameContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
     * BOT_LLM 流程与明细；配合 {@link #LOG_REASONING} 在 {@code application.yml} 里用 {@code logging.level} 分项开关。
     */
    private static final Logger LOG = LoggerFactory.getLogger(DpLlmNpcDecisionService.class);
    /**
     * 仅模型 reasoning（思考链）；要「控制台几乎只看思考」可把本 logger 设为 INFO、本类设为 WARN。
     */
    private static final Logger LOG_REASONING = LoggerFactory.getLogger("com.example.mgdemoplus.dp.BotLlmReasoning");

    /**
     * BOT_LLM 系统提示：强制信任局面包数值、禁止复算与重构行动顺序；解析侧只取正文中的 JSON 对象。
     */
    private static final String LLM_SYSTEM_PROMPT = String.join("\n",
            "你是无限注德州扑克AI引擎。你只接收一份已权威解析的【局面包】，禁止重新计算任何数字。",
            "行动纪律：",
            "- 你还须支付=0 → 只能CALL_OR_CHECK或RAISE/ALL_IN，绝不可FOLD。",
            "- 你还须支付>0 → 比对【胜率估计】与【跟注胜率门槛】：胜率≥门槛 → CALL_OR_CHECK；胜率<门槛 → 通常FOLD，但若对手风格为MANIAC/LOOSE或摊牌诈唬偏好≥0.65，或对策含bluffCatch/callDown，且胜率差距≤0.03，优先跟注抓诈。",
            "- 翻前决策：看位置、前面对手动作、自己手牌，按GTO表行动。",
            "- 翻后决策：先判断牌面干燥/湿润，再划分手牌梯队（强/中等/听牌/空气），然后根据对手行动、性格、赔率和胜率估计，按GTO+剥削策略决定动作。",
            "- RAISE必须给出具体尺度（跟满后额外加多少）。价值加注或半诈唬加注。",
            "输出格式：",
            "行动步骤只写简要推理，然后给出一个JSON对象，键：action (FOLD|CALL_OR_CHECK|RAISE|ALL_IN), chips_to_add (整数), brief_reason (≤120字中文)。",
            "- FOLD或CALL_OR_CHECK时chips_to_add=0；CALL_OR_CHECK: >0为跟注，=0为过牌。",
            "- RAISE时chips_to_add为跟满后额外加的筹码。ALL_IN时chips_to_add为全下总额(通常为后手筹码)。",
            "禁止输出markdown、代码块、多余文字。",
            "绝对规则：",
            "1. 局面包中的底池金额、你还须支付、胜率估计等均为唯一真实值，你不得质疑、不得重新计算。",
            "2. 行动顺序已在局面包中权威描述（谁已行动、现在轮到谁），你不得自行重构或猜测顺序。",
            "3. 小盲/大盲/庄位的标识已在局面包中给出，你直接使用即可，无需验证。",
            "4. 思考必须简短，直接引述局面包中的数值，禁止复述完整牌面。",
            "5. 当跟注是数学上强制要求时（胜率≥赔率），必须跟注，不准编造理由弃牌。"
    );
    /** 轮到 BOT_LLM 后、发起方舟请求前的额外等待；0 表示不人为拖延（总耗时几乎全在 API 侧）。 */
    private static final long PRE_API_DELAY_MS = 0L;
    /** 控制台打印 reasoning_content 上限，避免上万字刷屏；不影响 API 侧真实思考长度。 */
    private static final int REASONING_LOG_MAX_CHARS = 2000;
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
            if (!DpNpcEngine.isLlmBotNickname(bot.getNickname())) {
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
        /** {@link #decideActionIfReady} 把任务放进 map、发起异步的时刻，用于主线程侧「发起→落地」耗时 */
        final long startedAtMs;

        Inflight(CompletableFuture<DpNpcEngine.BotAction> future, LlmActionTicket ticket, long startedAtMs) {
            this.future = future;
            this.ticket = ticket;
            this.startedAtMs = startedAtMs;
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
        if (!DpNpcEngine.isLlmBotNickname(bot.getNickname())) {
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
            String roomId = room.getRoomId();
            String stage = room.getCurrentStage();
            CompletableFuture<DpNpcEngine.BotAction> future = CompletableFuture
                    .supplyAsync(() -> invokeModel(roomId, stage, userPrompt), llmExecutor)
                    .orTimeout(125, TimeUnit.SECONDS)
                    .exceptionally(ex -> {
                        LOG.warn("[BOT_LLM] 异步请求异常/超时: {}", ex == null ? "?" : ex.getMessage());
                        return null;
                    });

            long dispatchMs = System.currentTimeMillis();
            LOG.info("[BOT_LLM] 【已发起大模型请求】room={} stage={}", roomId, stage);
            inflightByKey.put(key, new Inflight(future, ticket, dispatchMs));// 钥匙和在途任务以及快照信息，等请求完成了，用key取出来结果核验快照
            return null;
        }
        // 如果返回了就删掉key，没返回就卡着
        if (!slot.future.isDone()) {
            return null;
        }

        inflightByKey.remove(key);
        bot.setNextBotActionTime(0L);
        long landingMs = System.currentTimeMillis();
        long sinceDispatchMs = landingMs - slot.startedAtMs;
        // 快照过期了，返回的决策不能用了，执行兜底决策
        if (!slot.ticket.stillValid(room, bot)) {// 执行兜底决策
            LOG.info("[BOT_LLM] 【决策耗时】发起异步→本tick落地={}ms room={} stage={} outcome=丢弃(局面已变)",
                    sinceDispatchMs, room.getRoomId(), room.getCurrentStage());
            return applyLocalFallback(room, bot, "局面已变(与发起请求时不一致，丢弃模型结果)");
        }
        // 模型返回null也走兜底
        DpNpcEngine.BotAction parsed = slot.future.getNow(null);
        if (parsed == null) {
            LOG.info("[BOT_LLM] 【决策耗时】发起异步→本tick落地={}ms room={} stage={} outcome=本地兜底(模型无有效动作)",
                    sinceDispatchMs, room.getRoomId(), room.getCurrentStage());
            return applyLocalFallback(room, bot, "模型返回null/未配置/解析失败/超时");
        }
        // 正常返回之后处理信息，有时候LLM返回的信息并不能实行，比如筹码剩下200了，他却要raise250，所以需要再最后把把关，弄成符合游戏实际的操作返回
        DpNpcEngine.BotAction executed = normalizeAndClamp(room, bot, parsed);
        LOG.info("[BOT_LLM] 【决策耗时】发起异步→本tick落地={}ms room={} stage={} outcome=采用大模型",
                sinceDispatchMs, room.getRoomId(), room.getCurrentStage());
        LOG.info("[BOT_LLM] 【采用大模型】解析动作={} chips_to_add={} -> 规范化执行={} amount={}",
                parsed.getType(), parsed.getAmount(), executed.getType(), executed.getAmount());
        return executed;
    }

    // 已学习，离线决策
    /** 未走模型或丢弃模型结果时的本地兜底，统一打日志便于对照控制台。 */
    private static DpNpcEngine.BotAction applyLocalFallback(DpRoomBO room, DpPlayer bot, String reason) {
        DpNpcEngine.BotAction a = fallback(room, bot);
        LOG.info("[BOT_LLM] 【本地决策】原因={} -> {} amount={}", reason, a.getType(), a.getAmount());
        return a;
    }

    // 已学习，返回决策结果
    /**
     * @param roomId 仅用于耗时日志关联房间
     * @param stage  仅用于耗时日志关联阶段
     */
    private DpNpcEngine.BotAction invokeModel(String roomId, String stage, String userPrompt) {
        if (!llmNpc.isConfigured()) {
            LOG.warn("[BOT_LLM] 未配置密钥/接入点，跳过请求");
            return null;
        }
        LOG.debug("======== [BOT_LLM] 输入 system ========\n{}", LLM_SYSTEM_PROMPT);
        LOG.debug("======== [BOT_LLM] 输入 user ========\n{}", userPrompt);
        long t0 = System.nanoTime();
        try {
            // 把大模型key id 提示词 信息包输入进去
            LlmNpc.LlmReply reply = llmNpc.chatMessagesDetailed(
                    LLM_SYSTEM_PROMPT,
                    LlmNpc.singleUserMessage(userPrompt),
                    llmResponseJsonObject);
            String raw = reply == null ? "" : reply.finalText();
            String reasoning = reply == null ? "" : reply.reasoningText();
            
            LOG.debug("======== [BOT_LLM] 模型返回原文 ========\n{}", raw == null ? "(null)" : raw);
            if (reasoning != null && !reasoning.isBlank()) {
                String r = reasoning.strip();
                if (r.length() > REASONING_LOG_MAX_CHARS) {
                    r = r.substring(0, REASONING_LOG_MAX_CHARS) + " …(控制台已截断，约 " + reasoning.length()
                            + " 字；缩短思考请将环境变量 ARK_REASONING_EFFORT 设为 medium 或 low)";
                }
                LOG_REASONING.info("======== [BOT_LLM] 模型思考 ========\n{}", r);
            }
            LlmParseResult parsed = parseModelReply(raw);
            DpNpcEngine.BotAction action = parsed == null ? null : parsed.action();
            if (parsed != null && parsed.briefReason() != null && !parsed.briefReason().isBlank()) {
                LOG.info("[BOT_LLM] 决策理由(brief_reason)={}", parsed.briefReason());
            }
            if (action == null && raw != null && !raw.isBlank()) {
                LOG.warn("[BOT_LLM] 提示：模型有正文但 JSON 未解析成动作，主线程将打印【本地决策】");
            }
            if (parsed != null && action != null) {
                LOG.info("[BOT_LLM] JSON 已解析 path={} -> {} chips_to_add={}",
                        parsed.path(), action.getType(), action.getAmount());
            }
            return action;
        } catch (Exception e) {
            LOG.warn("[BOT_LLM] HTTP/调用失败: {}", e.getMessage());
            return null;
        } finally {
            long modelWallMs = (System.nanoTime() - t0) / 1_000_000L;
            LOG.info("[BOT_LLM] 【模型调用耗时】HTTP+解析={}s room={} stage={}", modelWallMs/1000, roomId, stage);
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

    // 已学习，构建喂给AI的信息包（权威数值摘要 + 本街行动序 + smartContext 映射块）
    private String buildUserPrompt(DpRoomBO room, DpPlayer bot, LlmNpcGameContext ctx) {
        StringBuilder sb = new StringBuilder(2048);
        sb.append("【BOT_LLM 局面包】\n");
        sb.append("当前阶段: ").append(room.getCurrentStage()).append('\n');
        sb.append("你的位置: ").append(getPositionName(room, bot)).append('\n');
        String holeLine = heroHoleCardsLine(bot, ctx);
        sb.append("你的手牌(与服务端胜率所用一致): ").append(holeLine).append('\n');

        int callAmount = Math.max(0, room.getCurrentBetToCall() - bot.getBet());
        int potNow = room.getPot();
        int potAfterCall = potNow + callAmount;
        sb.append("当前底池: ").append(potNow).append('\n');
        sb.append("你还须支付: ").append(callAmount).append(" (如跟注)\n");
        sb.append("跟注后总底池: ").append(potAfterCall).append('\n');
        sb.append("你的后手筹码: ").append(bot.getChips()).append('\n');
        sb.append("盲注结构: 小盲=").append(room.getSmallBlindChips())
                .append(" 大盲=").append(room.getBigBlindChips())
                .append(" 本街加注层级rl=").append(room.getRaiseLevel())
                .append("（0=尚无人抬注 1=面对开局加注 2=面对3bet 3=面对4bet或更高）\n");

        sb.append("本街行动顺序 (从庄位下一座位起沿座位环，已权威记录):\n");
        sb.append(buildActionHistory(room, bot));

        sb.append("小盲玩家: ").append(getBlindPlayerNickname(room, true)).append('\n');
        sb.append("大盲玩家: ").append(getBlindPlayerNickname(room, false)).append('\n');
        sb.append("庄位玩家: ").append(getDealerPlayerNickname(room)).append('\n');

        if (ctx != null) {
            sb.append('\n').append(ctx.toPromptBlock());
        }
        sb.append("\n【注意】以上所有数字和行动顺序均为真实游戏状态，请直接使用，无需重新推导。\n");
        return sb.toString();
    }

    /** 优先使用 Context 中的底牌标签（与胜率/hsl 同源），否则退回房间手牌列表。 */
    private static String heroHoleCardsLine(DpPlayer bot, LlmNpcGameContext ctx) {
        if (ctx != null) {
            String h = ctx.getHoleCardsText();
            if (h != null && !h.isBlank()) {
                return h.trim();
            }
        }
        return cardsToString(bot.getHoleCards());
    }

    private static String cardsToString(List<String> cards) {
        if (cards == null || cards.isEmpty()) {
            return "无";
        }
        return String.join(" ", cards).trim();
    }

    private static String getPositionName(DpRoomBO room, DpPlayer bot) {
        List<DpPlayer> ps = room.getPlayers();
        if (ps == null) {
            return "未知";
        }
        int idx = ps.indexOf(bot);
        if (idx < 0) {
            return "未知";
        }
        if (bot.isDealer()) {
            return "按钮位(BTN)";
        }
        int did = room.getLastDealerIndex();
        if (did >= 0 && ps.size() >= 2) {
            int sbIdx = (did + 1) % ps.size();
            int bbIdx = (did + 2) % ps.size();
            if (idx == sbIdx) {
                return "小盲位(SB)";
            }
            if (idx == bbIdx) {
                return "大盲位(BB)";
            }
            int dist = (idx - did + ps.size()) % ps.size();
            if (dist <= 2) {
                return "前位";
            }
            if (dist <= ps.size() - 2) {
                return "中位";
            }
            return "后位";
        }
        return "未知";
    }

    private String buildActionHistory(DpRoomBO room, DpPlayer hero) {
        StringBuilder sb = new StringBuilder();
        List<DpPlayer> ps = room.getPlayers();
        if (ps == null || ps.isEmpty()) {
            return sb.toString();
        }
        int n = ps.size();
        int did = room.getLastDealerIndex();
        if (did < 0 || did >= n) {
            did = 0;
        }
        int bbIdx = n >= 2 ? (did + 2) % n : -1;
        DpPlayer bbPlayer = bbIdx >= 0 ? ps.get(bbIdx) : null;
        int start = (did + 1) % n;
        String stage = room.getCurrentStage();
        int bbChips = Math.max(1, room.getBigBlindChips());

        for (int i = 0; i < n; i++) {
            int idx = (start + i) % n;
            DpPlayer p = ps.get(idx);
            if (p == null || p.isLeftThisHand()) {
                continue;
            }
            if (p == hero) {
                sb.append(">> 轮到你行动 <<\n");
                continue;
            }
            String actionDesc;
            if (p.isFold()) {
                actionDesc = "弃牌";
            } else if (p.isAllIn()) {
                actionDesc = "全下(" + p.getBet() + ")";
            } else if (p.getBet() > 0) {
                boolean preflopBbPost = "preflop".equals(stage)
                        && p == bbPlayer
                        && p.getBet() == bbChips
                        && room.getCurrentBetToCall() <= bbChips;
                if (preflopBbPost) {
                    actionDesc = "大盲注(" + bbChips + ")";
                } else {
                    actionDesc = "下注/加注至 " + p.getBet();
                }
            } else if (p.isActed()) {
                actionDesc = "过牌";
            } else {
                actionDesc = "尚未行动(本轮)";
            }
            sb.append(p.getNickname()).append(": ").append(actionDesc).append('\n');
        }
        return sb.toString();
    }

    private static String getBlindPlayerNickname(DpRoomBO room, boolean smallBlind) {
        BlindSeatNames b = resolveBlindSeatNames(room);
        if (b == null) {
            return "?";
        }
        return smallBlind ? b.sbNick() : b.bbNick();
    }

    private static String getDealerPlayerNickname(DpRoomBO room) {
        List<DpPlayer> ps = room.getPlayers();
        if (ps == null) {
            return "?";
        }
        for (DpPlayer p : ps) {
            if (p != null && p.isDealer()) {
                String nick = p.getNickname();
                return nick != null ? nick : "?";
            }
        }
        return "?";
    }

    private record BlindSeatNames(String sbNick, String bbNick) {}

    /** 本手小盲/大盲昵称，与 {@link DpRoomServiceImpl} 庄位后 (D+1)/(D+2) 一致；人不足 2 则返回 null。 */
    private static BlindSeatNames resolveBlindSeatNames(DpRoomBO room) {
        if (room == null) {
            return null;
        }
        List<DpPlayer> ps = room.getPlayers();
        if (ps == null || ps.size() < 2) {
            return null;
        }
        int did = room.getLastDealerIndex();
        if (did < 0 || did >= ps.size()) {
            return null;
        }
        int sbIdx = (did + 1) % ps.size();
        int bbIdx = (did + 2) % ps.size();
        DpPlayer sbp = ps.get(sbIdx);
        DpPlayer bbp = ps.get(bbIdx);
        String sbn = sbp != null && sbp.getNickname() != null ? sbp.getNickname() : "?";
        String bbn = bbp != null && bbp.getNickname() != null ? bbp.getNickname() : "?";
        return new BlindSeatNames(sbn, bbn);
    }

    /** 解析结果 + 来源；briefReason 来自 JSON 的 brief_reason，仅日志展示。 */
    private record LlmParseResult(DpNpcEngine.BotAction action, String path, String briefReason) {}

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
            return new LlmParseResult(direct, fromContentUnwrap ? "unwrap" : "top", briefReasonFromJsonNode(root));
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
                return new LlmParseResult(nested, "unwrap", briefReasonFromJsonNode(content));
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
                    return new LlmParseResult(a, "scan", briefReasonFromJsonNode(node));
                }
                LlmParseResult inner = tryParseDecisionJson(slice, 1, false);
                if (inner != null) {
                    return new LlmParseResult(inner.action(), "scan", inner.briefReason());
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

    /** 模型输出的简短理由；仅用于日志，不参与下注逻辑。 */
    private static String briefReasonFromJsonNode(JsonNode root) {
        if (root == null || !root.has("brief_reason")) {
            return null;
        }
        String s = root.path("brief_reason").asText("").trim();
        if (s.isEmpty()) {
            return null;
        }
        final int cap = 300;
        if (s.length() > cap) {
            return s.substring(0, cap) + "…";
        }
        return s;
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
            // 跟注额超过后手时只能全下：与 ALL_IN 等价（bet 会夹成全下），避免误以为「call 失败会弃牌」
            if (callAmount > chips) {
                if (chips <= 0) {
                    return new DpNpcEngine.BotAction(DpNpcEngine.BotActionType.FOLD, 0);
                }
                return new DpNpcEngine.BotAction(DpNpcEngine.BotActionType.ALL_IN, chips);
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
