package com.example.mgdemoplus.npc.llm;

import com.example.mgdemoplus.common.bo.DpRoomBO;
import com.example.mgdemoplus.common.entity.DpPlayer;
import com.example.mgdemoplus.npc.engine.DpNpcEngine;
import com.example.mgdemoplus.llm.OpenAiCompatibleChatClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * {@code BOT_LLM} / {@code BOT_LLM_GLOBAL}：把房间对局摘要发给方舟兼容 Chat API，解析 JSON 决策为 {@link DpNpcEngine.BotAction}。
 * GLOBAL 在每手牌 {@code handSeed} 下维护与本 bot 的多轮 user/assistant 历史（见 {@link LlmNpcGlobalHandConversationStore}）。
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
    private static final Logger LOG_REASONING = LoggerFactory.getLogger("com.example.mgdemoplus.BotLlmReasoning");

    /**
     * BOT_LLM 系统提示：纪律、教学与输出契约；局面数值仅见 user 快照（见 {@link LlmNpcUserSnapshot}）。
     */
    private static final String LLM_SYSTEM_PROMPT = """
            你是无限注德州扑克（NLHE）决策引擎。user 消息是一条固定格式的局面快照：首行 v1|BOT_LLM|snap，接着键名表，一行 ----，再按同序逐行给出值。键名表永远相同；从 ---- 的下一行起才随对局变化。
            快照字段提示（勿在回复中复述整表）：stage_en=街英文；hero_seat_label_zh=相对庄家座位中文；raise_level_rl=本街加注层级；street_action_esc 内数字为各座本街贡献/面对档位；你还须支付**唯一**以 call_amount_chips 为准，禁止把他人本街贡献误当跟注额。equity_gate_*、pot_odds_value、pot_odds_rule_tag、equity_estimate、impl 等均在**同一冻结时刻**与 call_amount_chips、pot_chips 对齐，禁止用心算重算来否定快照。pot_odds_rule_tag：OFF=勿比赔（八字：零跟注勿比 pot_odds）；ON=须比赔（八字：有跟注对齐胜率）。equity_gate_rule_tag=PAY0_NO_FOLD_GATE 表示无「跟注赔率门槛」、不得仅因 equity 低而 FOLD；PAY_GT0_USE_IMPL 时 equity_gate_impl_ratio 为跟注所需胜率门槛近似值；>>ACT_HERO<<=轮到你；空字段用 —。
            【快照冻结纪律（单行）】call_amount_chips、equity_gate_*、pot_odds_*、equity_estimate 同帧自洽；禁止纠结 ON/OFF 定义超过一句；禁止论证 street_action_esc 与 call_amount_chips 矛盾——二者已按面对档位与本街已下核对。
            【快照真值纪律】胜率估计、赔率、SPR、hsl/rk、对手风格档、线路可信度、计数器关键词、挤压风险文案等均为服务端快照真值；禁止与常识比对以论证快照错；禁止因怀疑快照而拉长推理；禁止复述整份 user。
            【行动】你还须支付（见快照 call_amount_chips）=0：仅可 CALL_OR_CHECK（过牌/看牌，chips_to_add=0）或 RAISE/ALL_IN，禁止 FOLD。call_amount>0：一般比较 equity_estimate 与 equity_gate_impl_ratio；该跟则 CALL_OR_CHECK；该弃则 FOLD；但若对手偏 MANIAC/LOOSE 或 showdown_bluffiness≥0.65 或 counter_strategy_kw 含 bluffCatch/callDown 且 equity 与门槛差距≤0.03，可优先考虑跟注抓诈。数学上必须跟注时（胜率不低于门槛）不得编造理由弃牌。
            【策略】翻前综合位置、前序行动、手牌结构与加注层级，按 GTO 粗表+合理剥削；翻后先干/湿面与听牌环境，再把手牌分强/中/听牌/空气，结合对手范围、下注尺度、可信度与 SPR 决策，当你持顶对或更好牌力时，优先考虑下注/加注拿价值，而非仅过牌。进攻：价值与半诈唬用 RAISE，须给出具体再加注额。全下：仅当选择 ALL_IN；chips_to_add 为全下总额（通常后手）。
            【成牌标签】hsl_en 为服务端最佳成牌英文标签，勿推翻。PAIR_OF_x：若 hsl 为 PAIR_OF_某点且底牌不含该点，多为板对弱踢脚，勿口述为顶对。
            【桌边话术 table_talk】每轮 JSON **必填** table_talk：boolean false 表示本轮不说话；或 ≤80 字中文字符串作桌边表演/心理战（可误导，但**必须与本轮 action 一致**，禁止口头弃牌却 RAISE）。与 brief_reason 区分：brief_reason=决策理由，table_talk=对局表演。缺失视为 false。
            【输出】先极短内部思考（禁止长篇独白/自我辩论快照），然后**仅一行** JSON 对象：{action,chips_to_add,brief_reason,table_talk}；action（FOLD|CALL_OR_CHECK|RAISE|ALL_IN）；chips_to_add（整数）；brief_reason（≤120 字中文，与 action、stage_en **一致**）。brief_reason **必须出现**当前街中文名之一（翻前/翻牌/转牌/河牌，与 stage_en 对应）；preflop 时禁止把「翻后/河牌/转牌/翻牌圈/在翻牌/到翻牌」当成当前已发生语境（「便宜看翻牌」类意图除外）。FOLD/CALL_OR_CHECK 时 chips_to_add=0；CALL_OR_CHECK 且需跟注时由解析侧规范化；RAISE：chips_to_add=面对档位之上的再加；ALL_IN：chips_to_add=全下总额。禁止 markdown、代码围栏、多余 JSON 外套或口癖；除该行 JSON 外禁止任何字符。
            """.stripIndent();

    /**
     * BOT_LLM_GLOBAL：与 {@link #LLM_SYSTEM_PROMPT} 同结构、同纪律；额外约定多轮对话与 plan_next / follow_up。
     */
    private static final String LLM_GLOBAL_SYSTEM_PROMPT = """
            你是无限注德州扑克（NLHE）决策引擎——**全局叙事版**。user 消息仍是固定格式局面快照：首行 v1|BOT_LLM_GLOBAL|snap，接着键名表，一行 ----，再按同序逐行给出值；键名表与同项目 BOT_LLM 一致。同一手牌内你会收到**多轮**此类 user，此前 messages 中还可能有历史 user 与上一轮 assistant 的**单行 JSON**（含你当时写的 plan_next）；把整条街的线路与计划串起来想，但**一律以本轮最新 user 快照的冻结数值为准**，禁止用口述或旧轮数值推翻本轮 call_amount_chips、equity、赔率与 hsl/rk。
            快照字段提示（勿在回复中复述整表）：stage_en=街英文；hero_seat_label_zh=相对庄家座位中文；raise_level_rl=本街加注层级；street_action_esc 内数字为各座本街贡献/面对档位；你还须支付**唯一**以 call_amount_chips 为准，禁止把他人本街贡献误当跟注额。equity_gate_*、pot_odds_value、pot_odds_rule_tag、equity_estimate、impl 等均在**同一冻结时刻**与 call_amount_chips、pot_chips 对齐，禁止用心算重算来否定快照。pot_odds_rule_tag：OFF=勿比赔（八字：零跟注勿比 pot_odds）；ON=须比赔（八字：有跟注对齐胜率）。equity_gate_rule_tag=PAY0_NO_FOLD_GATE 表示无「跟注赔率门槛」、不得仅因 equity 低而 FOLD；PAY_GT0_USE_IMPL 时 equity_gate_impl_ratio 为跟注所需胜率门槛近似值；>>ACT_HERO<<=轮到你；空字段用 —。
            【快照冻结纪律（单行）】call_amount_chips、equity_gate_*、pot_odds_*、equity_estimate 同帧自洽；禁止纠结 ON/OFF 定义超过一句；禁止论证 street_action_esc 与 call_amount_chips 矛盾——二者已按面对档位与本街已下核对。
            【快照真值纪律】胜率估计、赔率、SPR、hsl/rk、对手风格档、线路可信度、计数器关键词、挤压风险文案等均为服务端快照真值；禁止与常识比对以论证快照错；禁止因怀疑快照而拉长推理；禁止复述整份 user。
            【行动】你还须支付（见快照 call_amount_chips）=0：仅可 CALL_OR_CHECK（过牌/看牌，chips_to_add=0）或 RAISE/ALL_IN，禁止 FOLD。call_amount>0：一般比较 equity_estimate 与 equity_gate_impl_ratio；该跟则 CALL_OR_CHECK；该弃则 FOLD；但若对手偏 MANIAC/LOOSE 或 showdown_bluffiness≥0.65 或 counter_strategy_kw 含 bluffCatch/callDown 且 equity 与门槛差距≤0.03，可优先考虑跟注抓诈。数学上必须跟注时（胜率不低于门槛）不得编造理由弃牌。
            【策略】翻前综合位置、前序行动、手牌结构与加注层级，按 GTO 粗表+合理剥削；翻后先干/湿面与听牌环境，再把手牌分强/中/听牌/空气，结合对手范围、下注尺度、可信度与 SPR 决策,当你持顶对或更好牌力时，优先考虑下注/加注拿价值，而非仅过牌。进攻：价值与半诈唬用 RAISE，须给出具体再加注额。全下：仅当选择 ALL_IN；chips_to_add 为全下总额（通常后手）。可参照你在上一轮 JSON 里的 plan_next 调整线路，但以本轮快照为准。
            【计划对照】当本条请求的 messages 里已出现**上一轮 assistant 单行 JSON**（其中有你当时的 plan_next）：**brief_reason 必须显式接上计划**——在同一 brief_reason 内，用极简中文写清「与上轮计划一致」或「因本轮快照变化而修正」（各用 ≤25 字的短分句串联即可，总得仍受 brief_reason 字数上限）；须扣住上轮 JSON 里的 plan_next 要义，禁止空洞套话、禁止编造上一轮没有的内容。**首轮**轮到该 hero、尚无历史 assistant 时本条免写。**内部思考**可先核对上轮 plan_next 与本轮 street_action_esc / call_amount，但最终对外仍只出一行 JSON。
            【成牌标签】hsl_en 为服务端最佳成牌英文标签，勿推翻；不得把已成牌（顺子、同花、葫芦等）说成「仍在听」「仅听牌」类未成章口径。PAIR_OF_x：若 hsl 为 PAIR_OF_某点且底牌不含该点，多为板对弱踢脚，勿口述为顶对。
            【桌边话术 table_talk】每轮 JSON **必填** table_talk：boolean false 表示本轮不说话；或 ≤80 字中文字符串作桌边表演/心理战（可误导，但**必须与本轮 action 一致**，禁止口头弃牌却 RAISE）。与 brief_reason 区分：brief_reason=决策理由，table_talk=对局表演。缺失视为 false。
            【输出】先极短内部思考（禁止长篇独白/自我辩论快照），然后**仅一行** JSON 对象，字段：**action,chips_to_add,brief_reason,table_talk,plan_next**；另可选 reasoning **或** thought 其一（极短调试句，服务端可记日志，不得替代 brief_reason）。action（FOLD|CALL_OR_CHECK|RAISE|ALL_IN）；chips_to_add（整数）；brief_reason（≤140 字中文，与 action、stage_en **一致**）。brief_reason **必须出现**当前街中文名之一（翻前/翻牌/转牌/河牌，与 stage_en 对应）；并遵守上文【计划对照】。preflop 时禁止把「翻后/河牌/转牌/翻牌圈/在翻牌/到翻牌」当成当前已发生语境（「便宜看翻牌」类意图除外）。**plan_next**（必填，≤120 字中文）：写给「下一轮到你的冻结快照」时快速接续的策略要点（可含下一街假设与调整条件），勿写废话剧情。**follow_up**（可选，≤80 字）：对 plan_next 的一行补丁或备忘。FOLD/CALL_OR_CHECK 时 chips_to_add=0；CALL_OR_CHECK 且需跟注时由解析侧规范化；RAISE：chips_to_add=面对档位之上的再加；ALL_IN：chips_to_add=全下总额。禁止 markdown、代码围栏、多余 JSON 外套或口癖；除该行 JSON 外禁止任何字符。
            """.stripIndent();
    /** 轮到 BOT_LLM 后、发起方舟请求前的额外等待；0 表示不人为拖延（总耗时几乎全在 API 侧）。 */
    private static final long PRE_API_DELAY_MS = 0L;
    /** 控制台打印 reasoning_content 上限，避免上万字刷屏；不影响 API 侧真实思考长度。 */
    private static final int REASONING_LOG_MAX_CHARS = 2000;
    /** BOT_LLM 并发：固定 4 线程；一桌多 BOT_LLM 同时思考时会排队，拉长尾延迟（与模型本身长尾叠加）。增大线程会抬高供应商并发与费用风险，需产品与预算评估。 */
    private static final Pattern JSON_BLOCK = Pattern.compile("```(?:json)?\\s*([\\s\\S]*?)```",
            Pattern.CASE_INSENSITIVE);

    private final ExecutorService llmExecutor = Executors.newFixedThreadPool(4, r -> {
        Thread t = new Thread(r, "dp-llm-npc");
        t.setDaemon(true);
        return t;
    });

    private final ConcurrentHashMap<String, Inflight> inflightByKey = new ConcurrentHashMap<>();
    private final OpenAiCompatibleChatClient chatClient;
    private final LlmNpcGlobalHandConversationStore globalConversationStore;
    private final boolean llmResponseJsonObject;
    private final ObjectMapper objectMapper = new ObjectMapper();

    // 构造器中从配置文件里取输入大模型请求的id，key baseUrl信息
    public DpLlmNpcDecisionService(
            LlmNpcGlobalHandConversationStore globalConversationStore,
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
        this.globalConversationStore = Objects.requireNonNull(globalConversationStore, "globalConversationStore");
        this.chatClient = new OpenAiCompatibleChatClient(
                apiKey,
                endpointId,
                baseUrl.isEmpty() ? null : baseUrl,
                reasoningEffort.isEmpty() ? null : reasoningEffort,
                thinkingType.isEmpty() ? null : thinkingType);
    }

    /** 异步闭包回填：BOT_LLM_GLOBAL 在多轮采纳后写入 {@link LlmNpcGlobalHandConversationStore}。 */
    private record AsyncLlmDecision(DpNpcEngine.BotAction action, String rawAssistantBody) {}

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
        final CompletableFuture<AsyncLlmDecision> future;
        final LlmActionTicket ticket;
        /** {@link #decideActionIfReady} 把任务放进 map、发起异步的时刻，用于主线程侧「发起→落地」耗时 */
        final long startedAtMs;
        /** 本条请求对应的 user 快照（GLOBAL 存档用；标准 BOT_LLM 忽略）。 */
        final String userPromptSnapshot;
        /** 是否 BOT_LLM_GLOBAL（票据作废时亦不写入历史）。 */
        final boolean globalConversation;

        Inflight(
                CompletableFuture<AsyncLlmDecision> future,
                LlmActionTicket ticket,
                long startedAtMs,
                String userPromptSnapshot,
                boolean globalConversation) {
            this.future = future;
            this.ticket = ticket;
            this.startedAtMs = startedAtMs;
            this.userPromptSnapshot = userPromptSnapshot;
            this.globalConversation = globalConversation;
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
            LlmNpcGameContext gameContext = DpNpcEngine.buildLlmNpcGameSnapshot(room, bot);
            boolean globalConversation = DpNpcEngine.isGlobalLlmBotNickname(bot.getNickname());
            String userPrompt = buildUserPrompt(room, bot, gameContext, globalConversation);// 用的是GameContext的方法，打包压缩送给大模型的信息
            // 异步发送打包信息，把传回来的信息给future
            String roomId = room.getRoomId();
            String stage = room.getCurrentStage();
            final long dispatchHandSeed = room.getCurrentHandSeed();
            final String dispatchBotNick = bot.getNickname();

            // 进程退出 / DevTools 热重载会先 @PreDestroy shutdown 线程池，Timer tick 仍可能落到本 Bean 上一拍：勿向已关闭池 submit（否则 Timer 线程抛 RejectedExecutionException）
            if (llmExecutor.isShutdown()) {
                LOG.warn("{} 【LLM线程池已关闭】跳过异步调用，本地兜底 room={} stage={}", logTagForNickname(dispatchBotNick),
                        roomId, stage);
                return applyLocalFallback(room, bot, "LLM执行器不可用(关停或重启间隙)");
            }

            CompletableFuture<AsyncLlmDecision> future = CompletableFuture
                    .supplyAsync(
                        //异步执行的任务内容
                            () -> globalConversation
                                    ? invokeModelGlobal(
                                            roomId, stage, dispatchHandSeed, dispatchBotNick, userPrompt)
                                    : invokeModelClassic(roomId, stage, userPrompt),
                            llmExecutor)
                    .orTimeout(125, TimeUnit.SECONDS)
                    .exceptionally(ex -> {
                        LOG.warn("{} 异步请求异常/超时: {}", logTagForNickname(dispatchBotNick),
                                ex == null ? "?" : ex.getMessage());
                        return null;
                    });

            long dispatchMs = System.currentTimeMillis();
            LOG.info("{} 【已发起大模型请求】room={} stage={}", logTagForNickname(dispatchBotNick), roomId, stage);
            inflightByKey.put(
                    key,
                    new Inflight(future, ticket, dispatchMs, userPrompt, globalConversation)); // 钥匙和在途任务以及快照信息，等请求完成了，用key取出来结果核验快照
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
        String logTag = logTagForNickname(bot.getNickname());
        // 快照过期了，返回的决策不能用了，执行兜底决策
        if (!slot.ticket.stillValid(room, bot)) {// 执行兜底决策
            LOG.info("{} 【决策耗时】发起异步→本tick落地={}ms room={} stage={} outcome=丢弃(局面已变)",
                    logTag, sinceDispatchMs, room.getRoomId(), room.getCurrentStage());
            return applyLocalFallback(room, bot, "局面已变(与发起请求时不一致，丢弃模型结果)");
        }
        // 模型返回null也走兜底
        AsyncLlmDecision outcome = slot.future.getNow(null);
        if (outcome == null || outcome.action() == null) {
            LOG.info("{} 【决策耗时】发起异步→本tick落地={}ms room={} stage={} outcome=本地兜底(模型无有效动作)",
                    logTag, sinceDispatchMs, room.getRoomId(), room.getCurrentStage());
            return applyLocalFallback(room, bot, "模型返回null/未配置/解析失败/超时");
        }
        DpNpcEngine.BotAction parsed = outcome.action();
        // 正常返回之后处理信息，有时候LLM返回的信息并不能实行，比如筹码剩下200了，他却要raise250，所以需要再最后把把关，弄成符合游戏实际的操作返回
        DpNpcEngine.BotAction executed = normalizeAndClamp(room, bot, parsed);
        if (slot.globalConversation) {
            globalConversationStore.recordSuccessfulTurn(
                    room.getRoomId(),
                    bot.getNickname(),
                    slot.ticket.handSeed,
                    slot.userPromptSnapshot,
                    outcome.rawAssistantBody() != null ? outcome.rawAssistantBody() : "");
        }
        LOG.info("{} 【决策耗时】发起异步→本tick落地={}ms room={} stage={} outcome=采用大模型",
                logTag, sinceDispatchMs, room.getRoomId(), room.getCurrentStage());
        LOG.info("{} 【采用大模型】解析动作={} chips_to_add={} -> 规范化执行={} amount={}",
                logTag, parsed.getType(), parsed.getAmount(), executed.getType(), executed.getAmount());
        return executed;
    }

    // 已学习，离线决策
    /** 日志前缀：BOT_LLM vs BOT_LLM_GLOBAL。 */
    private static String logTagForNickname(String botNick) {
        return botNick != null && DpNpcEngine.isGlobalLlmBotNickname(botNick) ? "[BOT_LLM_GLOBAL]" : "[BOT_LLM]";
    }

    /** 未走模型或丢弃模型结果时的本地兜底，统一打日志便于对照控制台。 */
    private DpNpcEngine.BotAction applyLocalFallback(DpRoomBO room, DpPlayer bot, String reason) {
        DpNpcEngine.BotAction a = fallback(room, bot);
        LOG.info("{} 【本地决策】原因={} -> {} amount={}",
                logTagForNickname(bot != null ? bot.getNickname() : null), reason, a.getType(), a.getAmount());
        return a;
    }

    // 已学习，返回决策结果
    /**
     * @param roomId 仅用于耗时日志关联房间
     * @param stage  仅用于耗时日志关联阶段
     */
    private AsyncLlmDecision invokeModelClassic(String roomId, String stage, String userPrompt) {
        return callModelAndParse(
                roomId,
                stage,
                "[BOT_LLM]",
                LLM_SYSTEM_PROMPT,
                OpenAiCompatibleChatClient.singleUserMessage(userPrompt),
                userPrompt,
                false);
    }

    private AsyncLlmDecision invokeModelGlobal(
            String roomId, String stage, long handSeed, String botNickname, String userPrompt) {
        //通过map获取之前的对话list，并添加新的信息包，然后发送
                ArrayList<OpenAiCompatibleChatClient.ChatMessage> msgs =
                new ArrayList<>(globalConversationStore.copyHistory(roomId, botNickname, handSeed));
        msgs.add(new OpenAiCompatibleChatClient.ChatMessage("user", userPrompt));
        if (LOG.isDebugEnabled()) {
            int prior = msgs.size() - 1;
            LOG.debug(
                    "[BOT_LLM_GLOBAL] 多轮 API messages 条数={}（本轮前历史={}）room={} stage={} handSeed={}",
                    msgs.size(),
                    prior,
                    roomId,
                    stage,
                    handSeed);
        }
        return callModelAndParse(roomId, stage, "[BOT_LLM_GLOBAL]", LLM_GLOBAL_SYSTEM_PROMPT, msgs, userPrompt, true);
    }

    /** 共通：发起 HTTP → 日志 → JSON 解析 → {@link AsyncLlmDecision}。 */
    private AsyncLlmDecision callModelAndParse(
            String roomId,
            String stage,
            String logTag,
            String systemPrompt,
            List<OpenAiCompatibleChatClient.ChatMessage> messages,
            String userPayloadForStableMetrics,
            boolean globalStablePrefixVariant) {
        if (!chatClient.isConfigured()) {
            LOG.warn("{} 未配置密钥/接入点，跳过请求", logTag);
            return null;
        }
        LOG.debug("======== {} 输入 system ========\n{}", logTag, systemPrompt);
        if (messages != null && !messages.isEmpty()) {
            OpenAiCompatibleChatClient.ChatMessage last = messages.get(messages.size() - 1);
            LOG.debug(
                    "======== {} 输入 last message role={} ========\n{}",
                    logTag,
                    last != null ? last.role() : "?",
                    last != null ? last.content() : "(null)");
        }
        if (LOG.isDebugEnabled()) {
            int stableChars = LlmNpcUserSnapshot.stablePrefixLength(globalStablePrefixVariant);
            int ulen = userPayloadForStableMetrics != null ? userPayloadForStableMetrics.length() : 0;
            String head =
                    userPayloadForStableMetrics != null && ulen >= stableChars
                            ? userPayloadForStableMetrics.substring(0, stableChars)
                            : userPayloadForStableMetrics;
            LOG.debug(
                    "{} prompt_metrics systemChars={} userChars={} stablePrefixChars={} stablePrefixHash={}",
                    logTag,
                    systemPrompt.length(),
                    ulen,
                    stableChars,
                    head == null ? "0" : Integer.toHexString(head.hashCode()));
        }
        long t0 = System.nanoTime();
        int reasoningChars = 0;
        try {
            OpenAiCompatibleChatClient.ChatCompletionReply reply =
                    chatClient.chatCompletionMultiTurn(systemPrompt, messages, llmResponseJsonObject);
            String raw = reply == null ? "" : reply.finalText();
            String reasoning = reply == null ? "" : reply.reasoningText();
            reasoningChars = reasoning == null ? 0 : reasoning.length();

            LOG.debug("======== {} 模型返回原文 ========\n{}", logTag, raw == null ? "(null)" : raw);
            if (reasoning != null && !reasoning.isBlank()) {
                String r = reasoning.strip();
                if (r.length() > REASONING_LOG_MAX_CHARS) {
                    r = r.substring(0, REASONING_LOG_MAX_CHARS) + " …(控制台已截断；全文 reasoning_chars≈"
                            + reasoningChars + ")";
                }
                LOG_REASONING.info("======== {} 模型 reasoning_content ========\n{}", logTag, r);
            }
            LlmParseResult parsed = parseModelReply(raw);
            DpNpcEngine.BotAction action = parsed == null ? null : parsed.action();
            if (parsed != null && parsed.briefReason() != null && !parsed.briefReason().isBlank()) {
                String shown = normalizeBriefReasonForStage(parsed.briefReason(), stage);
                if (!shown.equals(parsed.briefReason())) {
                    LOG.warn("{} brief/thought 与 stage 矛盾或缺街名，已展示规范化：{}", logTag, shown);
                }
                LOG.info("{} 决策简述(brief/thought)={}", logTag, shown);
            }
            if (parsed != null && parsed.planHint() != null && !parsed.planHint().isBlank()) {
                LOG.info("{} plan_next/follow_up={}", logTag, parsed.planHint());
            }
            if (action == null && raw != null && !raw.isBlank()) {
                LOG.warn("{} 提示：模型有正文但 JSON 未解析成动作，主线程将打印【本地决策】", logTag);
            }
            if (parsed != null && action != null) {
                LOG.info(
                        "{} JSON 已解析 path={} -> {} chips_to_add={}",
                        logTag,
                        parsed.path(),
                        action.getType(),
                        action.getAmount());
            }
            String body = raw == null ? "" : raw.strip();
            return new AsyncLlmDecision(action, body);
        } catch (Exception e) {
            LOG.warn("{} HTTP/调用失败: {}", logTag, e.getMessage());
            return null;
        } finally {
            long modelWallMs = (System.nanoTime() - t0) / 1_000_000L;
            LOG.info("{} 【模型调用耗时】HTTP+解析={}ms room={} stage={} reasoningChars={}",
                    logTag, modelWallMs, roomId, stage, reasoningChars);
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

    /** 单一局面包出口：键表固定 + ---- 后值行（见 {@link LlmNpcUserSnapshot}）。 */
    private static String buildUserPrompt(DpRoomBO room, DpPlayer bot, LlmNpcGameContext ctx, boolean globalVariant) {
        return LlmNpcUserSnapshot.formatUserPayload(room, bot, ctx, globalVariant);
    }

    /** 解析结果 + 来源；briefReason 可为 brief_reason / reasoning / thought 合并；planHint = plan_next|follow_up。 */
    private record LlmParseResult(
            DpNpcEngine.BotAction action,
            String path,
            String briefReason,
            String planHint) {}

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
            return new LlmParseResult(
                    direct,
                    fromContentUnwrap ? "unwrap" : "top",
                    mergedThoughtBriefFromJsonNode(root),
                    planContinuationHintFromJsonNode(root));
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
                return new LlmParseResult(
                        nested,
                        "unwrap",
                        mergedThoughtBriefFromJsonNode(content),
                        planContinuationHintFromJsonNode(content));
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
                    return new LlmParseResult(
                            a, "scan", mergedThoughtBriefFromJsonNode(node), planContinuationHintFromJsonNode(node));
                }
                LlmParseResult inner = tryParseDecisionJson(slice, 1, false);
                if (inner != null) {
                    return new LlmParseResult(
                            inner.action(),
                            "scan",
                            inner.briefReason(),
                            inner.planHint());
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

    private static String stageZhForBrief(String stageEn) {
        if (stageEn == null || stageEn.isBlank()) {
            return "";
        }
        return switch (stageEn.toLowerCase(Locale.ROOT)) {
            case "preflop" -> "翻前";
            case "flop" -> "翻牌";
            case "turn" -> "转牌";
            case "river" -> "河牌";
            default -> "";
        };
    }

    /**
     * 日志展示用：补足当前街中文标签，并在明显与 stage_en 矛盾时收束措辞（不改动已解析的 action）。
     */
    private static String normalizeBriefReasonForStage(String reason, String stageEn) {
        if (reason == null || reason.isBlank()) {
            return reason;
        }
        String s = reason.trim();
        final int cap = 300;
        if (s.length() > cap) {
            s = s.substring(0, cap) + "…";
        }
        String tag = stageZhForBrief(stageEn);
        String out = s;
        if (!tag.isEmpty() && !out.contains(tag)) {
            out = "【" + tag + "】" + out;
        }
        String low = stageEn == null ? "" : stageEn.toLowerCase(Locale.ROOT);
        if (!"preflop".equals(low)) {
            return out;
        }
        boolean badToken = out.contains("翻后") || out.contains("河牌") || out.contains("转牌");
        boolean badFlopCtx = out.contains("翻牌圈") || out.contains("在翻牌") || out.contains("到翻牌");
        if (!badToken && !badFlopCtx) {
            return out;
        }
        String cleaned = out;
        for (String w : new String[] {"翻后", "河牌", "转牌", "翻牌圈", "在翻牌", "到翻牌"}) {
            cleaned = cleaned.replace(w, "");
        }
        cleaned = cleaned.replace("  ", " ").trim();
        if (cleaned.isEmpty() || cleaned.equals("【翻前】")) {
            cleaned = "【翻前】理由与阶段冲突已收敛，按快照决策。";
        } else if (!cleaned.contains("翻前")) {
            cleaned = "【翻前】" + cleaned;
        }
        return cleaned;
    }

    /** 精简思考：brief_reason / reasoning / thought 取最长一条；日志用。 */
    private static String mergedThoughtBriefFromJsonNode(JsonNode root) {
        if (root == null) {
            return null;
        }
        String[] keys = {"brief_reason", "reasoning", "thought"};
        String best = null;
        for (String k : keys) {
            if (!root.has(k)) {
                continue;
            }
            String s = root.path(k).asText("").trim();
            if (s.isEmpty()) {
                continue;
            }
            if (best == null || s.length() > best.length()) {
                best = s;
            }
        }
        final int cap = 300;
        if (best == null) {
            return null;
        }
        return best.length() > cap ? best.substring(0, cap) + "…" : best;
    }

    /** plan_next / follow_up 合并摘要；下一轮助手上下文由模型自由阅读 JSON。 */
    private static String planContinuationHintFromJsonNode(JsonNode root) {
        if (root == null) {
            return null;
        }
        String pn = root.path("plan_next").asText("").trim();
        String fu = root.path("follow_up").asText("").trim();
        if (pn.isEmpty() && fu.isEmpty()) {
            return null;
        }
        if (pn.isEmpty()) {
            return capPlan(fu);
        }
        if (fu.isEmpty()) {
            return capPlan(pn);
        }
        return capPlan(pn + " | " + fu);
    }

    private static String capPlan(String s) {
        final int cap = 400;
        if (s == null || s.isEmpty()) {
            return null;
        }
        return s.length() > cap ? s.substring(0, cap) + "…" : s;
    }

    private static final int TABLE_TALK_MAX_CHARS = 80;

    private DpNpcEngine.BotAction botActionFromJsonNode(JsonNode root) {
        if (root == null || !root.has("action")) {
            return null;
        }
        String action = root.path("action").asText("").trim().toUpperCase();
        int chipsToAdd = root.path("chips_to_add").asInt(0);
        if (chipsToAdd < 0) {
            chipsToAdd = 0;
        }
        DpNpcEngine.LlmTableTalkMode talkMode;
        String talkText;
        TableTalkParsed talk = parseTableTalkFromJsonNode(root);
        talkMode = talk.mode();
        talkText = talk.text();
        return switch (action) {
            case "FOLD" -> new DpNpcEngine.BotAction(DpNpcEngine.BotActionType.FOLD, 0, talkMode, talkText);
            case "CALL_OR_CHECK", "CHECK", "CALL", "CHECK_OR_CALL" ->
                new DpNpcEngine.BotAction(DpNpcEngine.BotActionType.CALL_OR_CHECK, 0, talkMode, talkText);
            case "ALL_IN", "ALLIN" ->
                new DpNpcEngine.BotAction(DpNpcEngine.BotActionType.ALL_IN, chipsToAdd, talkMode, talkText);
            case "RAISE", "BET" ->
                new DpNpcEngine.BotAction(DpNpcEngine.BotActionType.RAISE, chipsToAdd, talkMode, talkText);
            default -> null;
        };
    }

    private record TableTalkParsed(DpNpcEngine.LlmTableTalkMode mode, String text) {}

    /** 缺失或非字符串且非 false → 不说话；string → trim 后推送。 */
    private static TableTalkParsed parseTableTalkFromJsonNode(JsonNode root) {
        if (root == null || !root.has("table_talk")) {
            return new TableTalkParsed(DpNpcEngine.LlmTableTalkMode.SILENT, null);
        }
        JsonNode tt = root.get("table_talk");
        if (tt == null || tt.isNull()) {
            return new TableTalkParsed(DpNpcEngine.LlmTableTalkMode.SILENT, null);
        }
        if (tt.isBoolean()) {
            if (!tt.asBoolean()) {
                return new TableTalkParsed(DpNpcEngine.LlmTableTalkMode.SILENT, null);
            }
            return new TableTalkParsed(DpNpcEngine.LlmTableTalkMode.SILENT, null);
        }
        if (tt.isTextual()) {
            String s = tt.asText("").replace('\r', ' ').replace('\n', ' ').trim();
            if (s.isEmpty()) {
                return new TableTalkParsed(DpNpcEngine.LlmTableTalkMode.SILENT, null);
            }
            if (s.length() > TABLE_TALK_MAX_CHARS) {
                s = s.substring(0, TABLE_TALK_MAX_CHARS);
            }
            return new TableTalkParsed(DpNpcEngine.LlmTableTalkMode.SAY, s);
        }
        return new TableTalkParsed(DpNpcEngine.LlmTableTalkMode.SILENT, null);
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
            return DpNpcEngine.BotAction.withExecution(a, DpNpcEngine.BotActionType.FOLD, 0);
        }
        if (a.getType() == DpNpcEngine.BotActionType.CALL_OR_CHECK) {
            // 跟注额超过后手时只能全下：与 ALL_IN 等价（bet 会夹成全下），避免误以为「call 失败会弃牌」
            if (callAmount > chips) {
                if (chips <= 0) {
                    return DpNpcEngine.BotAction.withExecution(a, DpNpcEngine.BotActionType.FOLD, 0);
                }
                return DpNpcEngine.BotAction.withExecution(a, DpNpcEngine.BotActionType.ALL_IN, chips);
            }
            return DpNpcEngine.BotAction.withExecution(a, DpNpcEngine.BotActionType.CALL_OR_CHECK, 0);
        }
        if (a.getType() == DpNpcEngine.BotActionType.ALL_IN) {
            return DpNpcEngine.BotAction.withExecution(a, DpNpcEngine.BotActionType.ALL_IN, Math.max(0, chips));
        }
        if (a.getType() == DpNpcEngine.BotActionType.RAISE) {
            int add = Math.max(0, a.getAmount());
            if (chips <= 0) {
                return DpNpcEngine.BotAction.withExecution(a, DpNpcEngine.BotActionType.FOLD, 0);
            }
            if (add >= chips) {
                return DpNpcEngine.BotAction.withExecution(a, DpNpcEngine.BotActionType.ALL_IN, chips);
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
                return DpNpcEngine.BotAction.withExecution(a, DpNpcEngine.BotActionType.ALL_IN, chips);
            }
            if (chipsNeeded <= 0) {
                return fallback(room, bot);
            }
            return DpNpcEngine.BotAction.withExecution(a, DpNpcEngine.BotActionType.RAISE, chipsNeeded);
        }
        return fallback(room, bot);
    }

    @PreDestroy
    public void shutdown() {
        llmExecutor.shutdown();
    }
}
