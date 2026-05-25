package com.example.mgdemoplus.npc.engine;

import com.example.mgdemoplus.common.bo.DpRoomBO;
import com.example.mgdemoplus.common.entity.DpPlayer;
import com.example.mgdemoplus.common.entity.DpPlayerStats;
import com.example.mgdemoplus.npc.strategy.DpNpcCallStrategy;
import com.example.mgdemoplus.npc.strategy.DpNpcCustomStrategy;
import com.example.mgdemoplus.npc.strategy.DpNpcFishStrategy;
import com.example.mgdemoplus.npc.strategy.DpNpcLagStrategy;
import com.example.mgdemoplus.npc.strategy.DpNpcManiacStrategy;
import com.example.mgdemoplus.npc.strategy.DpNpcNitStrategy;
import com.example.mgdemoplus.npc.strategy.DpNpcRuleDecisionParams;
import com.example.mgdemoplus.npc.strategy.DpNpcTagStrategy;
import com.example.mgdemoplus.npc.strategy.DpNpcUnifiedPreflopStrategy;
import com.example.mgdemoplus.npc.llm.LlmNpcGameContext;
import com.example.mgdemoplus.npc.rulethink.DpNpcRuleThinkSampler;
import com.example.mgdemoplus.utils.DpUtilHandEvaluator;
import com.example.mgdemoplus.utils.DpUtilHandEvaluator.HandStrength;
import static com.example.mgdemoplus.utils.DpUtilHandEvaluator.isPlayingBoardPairOnly;
import com.example.mgdemoplus.utils.DpUtilNpcDisplayNickname;
import com.example.mgdemoplus.utils.DpUtilSmartContext;

import ch.qos.logback.classic.Logger;

import static com.example.mgdemoplus.utils.DpUtilHandEvaluator.CARD_RANK_MAP;
import static com.example.mgdemoplus.utils.DpUtilHandEvaluator.SimpleStrength;
import static com.example.mgdemoplus.utils.DpUtilHandEvaluator.evaluateBestHand;
import static com.example.mgdemoplus.utils.DpUtilHandEvaluator.getRankFromCard;
import static com.example.mgdemoplus.utils.DpUtilHandEvaluator.toSimpleStrength;

import java.util.*;

import org.slf4j.LoggerFactory;

/**
 * 德扑 NPC 行为模块。
 * 负责：
 * - 判断某个玩家是否为机器人
 * - 不同昵称映射到不同机器人类型
 * - 依据牌力 / 牌面 / 位置 / 情绪等为机器人做下注决策
 * - 模拟机器人“思考时间”，按节奏在定时任务中出手
 */
public class DpNpcEngine {
    private static final Logger log = (Logger) LoggerFactory.getLogger(DpNpcEngine.class);

    private DpNpcEngine() {
    }

    /**
     * 规则 NPC 共用系数：短/深码、equity×底池赔率、c-bet/河牌尺度等（集中便于调参）。
     */
    public static final class NpcRuleCoeffs {
        /**
         * 典型短码阈值（单位：BB）：
         * - 建议范围：15~30，默认 20；
         * - 调小：更多玩家被视为 short stack，Shark 更常用“压短码”的下注；
         * - 调大：只有极短筹码才被当成 short stack，整体对短码的针对性减弱。
         */
        final double shortStackBB;

        /**
         * 深码局判断阈值（单位：BB）：
         * - deepStackMinBB：单个玩家筹码 > 该值即视为 deep stack；
         * - deepTableAvgBB：全桌平均筹码 > 该值视为 deep table。
         * 建议范围：deepStackMinBB 50~80，deepTableAvgBB 70~100。
         * 调大：更少牌局被视为深码局，Shark 在 turn/river 的 value 压制略收敛；
         * 调小：更多场景按深码处理，强牌 value 尺度和多街压制会略放大。
         */
        final double deepStackMinBB;
        final double deepTableAvgBB;

        /**
         * 赢率 vs 赔率容忍区间：
         * - equityPotOddsEps：认为“几乎持平”的 epsilon（建议 0.03~0.08）；
         * 调大：对小幅不利/有利不敏感，减少频繁的轻微调整；
         * 调小：对小幅差异也会更快放大或缩小弃牌意愿。
         * - equityPotOddsMargin：认为“明显划算”的差值阈值（建议 0.08~0.15）；
         * 调大：需要更明显的好赔率才会大幅降低弃牌率；
         * 调小：在略好赔率下也更倾向坚持到底。
         */
        final double equityPotOddsEps;
        final double equityPotOddsMargin;

        /**
         * 赢率 vs 赔率修正幅度：
         * - equityFoldBoost：当估计赢率明显小于底池赔率时，对基础弃牌率的附加值（建议 0.08~0.25，默认 0.15）；
         * 调大：Shark 更敏感于“不划算”的跟注，更容易直接弃牌；
         * 调小：面对不太划算的赔率也更愿意“看一张”。
         * - equityFoldShrinkFactor：当估计赢率明显大于底池赔率时，对基础弃牌率的缩放因子（建议 0.6~0.9，默认 0.8）；
         * 调小（如 0.6）：好赔率下更不容易被吓走；
         * 调大（接近 1）：对好赔率敏感度降低。
         */
        final double equityFoldBoost;
        final double equityFoldShrinkFactor;

        /**
         * multi-way（多人底池）基础弃牌加成：
         * - 建议范围：0.10~0.25，默认 0.15；
         * - 最终会结合玩家自身 callStation / foldToPressure（风格层经 checkRaiseFear 兼容口径）进行缩放；
         * - 调大：3 人以上底池时整体弃牌率显著提升；
         * - 调小：即使多人底池也更敢继续拼。
         */
        final double multiwayFoldBoostBase;

        /**
         * 河牌极强牌 overbet 策略：
         * - riverOverbetProb：在 river + 干燥牌面 + 怪兽牌时采用 overbet 的概率（建议 0.05~0.4，默认 0.2）；
         * - riverOverbetMinFactor / MaxFactor：overbet 相对 pot 的倍数，例如 1.2~1.5 表示
         * 1.2×pot~1.5×pot。
         * 调大 riverOverbetProb 或 MaxFactor：Shark 河牌会更凶、更常见明显大注；
         * 调小：Shark 的河牌价值下注更趋于常规大小。
         */
        final double riverOverbetProb;
        final double riverOverbetMinFactor;
        final double riverOverbetMaxFactor;

        /**
         * 河牌 medium 牌 block bet 策略：
         * - riverBlockProb：无人下注时用中等牌做小额 block/thin value 的概率（建议 0.1~0.6，默认 0.35）；
         * - riverBlockFactor：block bet 相对 pot 的基准比例（建议 0.2~0.5，默认约 1/3）。
         * 调大 riverBlockProb / riverBlockFactor：Shark 更常在 river 用中等牌下注抢薄价值；
         * 调小：更偏向 check 控池、少做 thin value。
         */
        final double riverBlockProb;
        final double riverBlockFactor;

        /**
         * 翻后 c-bet / value bet 尺度：
         * - cbetBaseWeak / Medium / Strong：无人下注时以 pot×factor 作为基准下注比例；
         * 建议：弱牌 0.3~0.5，中牌 0.4~0.7，强牌 0.6~0.9；
         * - cbetRandomMin / Max：在基准上乘以 [min, max] 的随机因子（例如 0.9~1.2）；
         * 调小区间：下注量更接近固定值；调大区间：尺度多样性更高。
         */
        final double cbetBaseWeak;
        final double cbetBaseMedium;
        final double cbetBaseStrong;
        final double cbetRandomMin;
        final double cbetRandomMax;

        NpcRuleCoeffs(double shortStackBB,
                double deepStackMinBB,
                double deepTableAvgBB,
                double equityPotOddsEps,
                double equityPotOddsMargin,
                double equityFoldBoost,
                double equityFoldShrinkFactor,
                double multiwayFoldBoostBase,
                double riverOverbetProb,
                double riverOverbetMinFactor,
                double riverOverbetMaxFactor,
                double riverBlockProb,
                double riverBlockFactor,
                double cbetBaseWeak,
                double cbetBaseMedium,
                double cbetBaseStrong,
                double cbetRandomMin,
                double cbetRandomMax) {
            this.shortStackBB = shortStackBB;
            this.deepStackMinBB = deepStackMinBB;
            this.deepTableAvgBB = deepTableAvgBB;
            this.equityPotOddsEps = equityPotOddsEps;
            this.equityPotOddsMargin = equityPotOddsMargin;
            this.equityFoldBoost = equityFoldBoost;
            this.equityFoldShrinkFactor = equityFoldShrinkFactor;
            this.multiwayFoldBoostBase = multiwayFoldBoostBase;
            this.riverOverbetProb = riverOverbetProb;
            this.riverOverbetMinFactor = riverOverbetMinFactor;
            this.riverOverbetMaxFactor = riverOverbetMaxFactor;
            this.riverBlockProb = riverBlockProb;
            this.riverBlockFactor = riverBlockFactor;
            this.cbetBaseWeak = cbetBaseWeak;
            this.cbetBaseMedium = cbetBaseMedium;
            this.cbetBaseStrong = cbetBaseStrong;
            this.cbetRandomMin = cbetRandomMin;
            this.cbetRandomMax = cbetRandomMax;
        }

        /**
         * 默认值：与历史工程中的主规则 NPC 系数一致。
         */
        static final NpcRuleCoeffs DEFAULT = new NpcRuleCoeffs(
                20.0, // shortStackBB
                60.0, // deepStackMinBB
                80.0, // deepTableAvgBB
                0.06, // equityPotOddsEps — 略放宽与赔率比较的抖动
                0.08, // equityPotOddsMargin — 更容易判定「赔率划算」而少弃
                0.10, // equityFoldBoost — 只在明显落后赔率时才加码弃牌
                0.68, // equityFoldShrinkFactor — 赔率划算时更敢跟
                0.11, // multiwayFoldBoostBase — 多人池略少过度弃牌
                0.22, // riverOverbetProb — 坚果略多拿一点上限
                1.20, // riverOverbetMinFactor
                1.50, // riverOverbetMaxFactor
                0.46, // riverBlockProb — 河牌中等牌多抢薄价值（针对爱跟）
                0.38, // riverBlockFactor — block 略大一点
                0.42, // cbetBaseWeak — 弱牌 c-bet 略收敛，少对鱼送
                0.66, // cbetBaseMedium
                0.84, // cbetBaseStrong
                0.92, // cbetRandomMin
                1.18 // cbetRandomMax
        );
    }

    /**
     * 规则 NPC 决策里引用的常量（数值来自 {@link NpcRuleCoeffs#DEFAULT}）。
     */
    public static final class RuleNpcConfig {
        private static final NpcRuleCoeffs P = NpcRuleCoeffs.DEFAULT;

        public static final double SHORT_STACK_BB = P.shortStackBB;
        public static final double DEEP_STACK_MIN_BB = P.deepStackMinBB;
        public static final double DEEP_TABLE_AVG_BB = P.deepTableAvgBB;
        static final double EQUITY_POTODDS_EPS = P.equityPotOddsEps;
        static final double EQUITY_POTODDS_MARGIN = P.equityPotOddsMargin;
        static final double EQUITY_FOLD_BOOST = P.equityFoldBoost;
        static final double EQUITY_FOLD_SHRINK = P.equityFoldShrinkFactor;
        static final double MULTIWAY_FOLD_BOOST = P.multiwayFoldBoostBase;
        static final double RIVER_OVERBET_PROB = P.riverOverbetProb;
        static final double RIVER_BLOCK_PROB = P.riverBlockProb;
        static final double RIVER_BLOCK_FACTOR = P.riverBlockFactor;
        /** 翻后无人加注时，按 pot×factor 下注的基准（弱/中/强），见 {@link NpcRuleCoeffs} */
        static final double CBET_BASE_WEAK = P.cbetBaseWeak;
        static final double CBET_BASE_MEDIUM = P.cbetBaseMedium;
        static final double CBET_BASE_STRONG = P.cbetBaseStrong;
        static final double CBET_RANDOM_MIN = P.cbetRandomMin;
        static final double CBET_RANDOM_MAX = P.cbetRandomMax;
    }

    /** 规则 NPC 昵称前缀 + {@code _<uuid>}；一桌可多实例。 */
    public static final String PREFIX_BOT_LAG = "BOT_LAG";
    public static final String PREFIX_BOT_TAG = "BOT_TAG";
    public static final String PREFIX_BOT_NIT = "BOT_NIT";
    public static final String PREFIX_BOT_FISH = "BOT_FISH";
    public static final String PREFIX_BOT_CALL = "BOT_CALL";
    public static final String PREFIX_BOT_MANIAC = "BOT_MANIAC";
    /** 房主自定义调参 NPC：{@code BOT_CUSTOM_<序号>} */
    public static final String PREFIX_BOT_CUSTOM = "BOT_CUSTOM";
    /**
     * 大模型机器人前缀：{@code BOT_LLM} 或 {@code BOT_LLM_<uuid>}。
     */
    public static final String PREFIX_BOT_LLM = "BOT_LLM";
    /**
     * 多轮整条牌局叙事前缀：{@code BOT_LLM_GLOBAL_<序号>}；须排除被 {@link #PREFIX_BOT_LLM} 误匹配。
     */
    public static final String PREFIX_BOT_LLM_GLOBAL = "BOT_LLM_GLOBAL";

    /** 兼容旧前端；决策映射为 {@link BotType#TAG}（紧凶）。 */
    public static final String LEGACY_BOT_SHARK_NICKNAME = "BOT_Shark";
    /** @deprecated 兼容旧前端固定昵称 */
    public static final String LEGACY_BOT_TAG_NICKNAME = "BOT_Tag";
    /** @deprecated 兼容旧前端固定昵称 */
    public static final String LEGACY_BOT_FISH_NICKNAME = "BOT_Fish";
    /** @deprecated 兼容旧前端固定昵称 */
    public static final String LEGACY_BOT_MANIAC_NICKNAME = "BOT_Maniac";

    /** 与 {@link #PREFIX_BOT_LLM} 一致，供文档与精确匹配旧逻辑引用。 */
    public static final String LLM_BOT_NICKNAME = PREFIX_BOT_LLM;

    /**
     * 为 false：规则 NPC 决策里 mood 恒按 0；思考延迟也不按 mood 缩放；结算不再改写机器人 mood。
     */
    public static final boolean NPC_MOOD_ENABLED = false;

    /**
     * 为 true：机器人 fold/call/raise 等抽样使用 {@code currentHandSeed ^ 座位} 固定种子，便于同一手牌复现决策序列。
     * 为 false：每次决策 {@code new Random()}，与 handSeed 无关。
     * <p>
     * 当前工程默认值见字段字面量（勿依赖本注释中的「默认」字样与实际不符）。
     * </p>
     */
    public static final boolean NPC_HAND_SEED_FOR_DECISIONS = true;

    /**
     * 机器人类型：与 {@link NpcStyle} 一一对应，决策参数取自 {@link #STYLE_PROFILE_MAP}，
     * 翻后大分支仍按 {@code BotType} 分岔（LAG/MANIAC、TAG/NIT、FISH/CALL）。
     */
    public enum BotType {
        /** 松凶 LAG */
        LAG,
        /** 紧凶 TAG */
        TAG,
        /** 紧弱 Nit */
        NIT,
        /** 松弱 / 娱乐鱼 */
        FISH,
        /** 跟注站 */
        CALL,
        /** 疯子 */
        MANIAC
    }

    /**
     * 整手牌计划类型：在 flop 首次行动时为 TAG / NIT 生成，
     * 后续 turn/river 通过剩余“barrels”数量控制继续进攻的积极程度。
     */
    public enum HandPlanType {
        VALUE, // 价值下注为主：强牌多街 value
        BLUFF, // 诈唬为主：弱牌/听牌在合适牌面多街持续施压
        POT_CONTROL, // 控池：中等牌为主，更多 check / 小额 probing
        GIVE_UP // 放弃：除非牌力大幅提升，否则优先过牌/弃牌
    }

    /**
     * NPC 风格旋钮（六档）：与 {@link BotType} 一一对应；{@link BotType#CALL} 对应 {@link #CALL_STATION}。
     * 数值驱动翻前 {@link DpNpcUnifiedPreflopStrategy} 与翻后概率缩放。
     */
    public enum NpcStyle {
        /** 松凶 */
        LAG,
        /** 紧凶 */
        TAG,
        /** 紧弱 Nit */
        NIT,
        /** 松弱 / 鱼 */
        FISH,
        /** 跟注站 */
        CALL_STATION,
        /** 疯子 */
        MANIAC
    }

    /**
     * NPC 决策动作类型。
     */
    public enum BotActionType {
        FOLD, CALL_OR_CHECK, RAISE, ALL_IN
    }

    /**
     * LLM 桌边话术：{@link #RULE} 由 {@link com.example.mgdemoplus.npc.tabletalk.DpNpcTableTalkService} 走台词池；
     * {@link #SILENT} 不推送；{@link #SAY} 推送 {@link #llmTableTalkText}。
     */
    public enum LlmTableTalkMode {
        RULE,
        SILENT,
        SAY
    }

    /**
     * NPC 决策结果：动作 + 金额；可选 LLM 桌边话术（规则 NPC 恒为 {@link LlmTableTalkMode#RULE}）。
     */
    public static class BotAction {
        private final BotActionType type;
        private final int amount;
        private final LlmTableTalkMode llmTableTalkMode;
        private final String llmTableTalkText;

        public BotAction(BotActionType type, int amount) {
            this(type, amount, LlmTableTalkMode.RULE, null);
        }

        public BotAction(BotActionType type, int amount, LlmTableTalkMode llmTableTalkMode, String llmTableTalkText) {
            this.type = type;
            this.amount = amount;
            this.llmTableTalkMode = llmTableTalkMode != null ? llmTableTalkMode : LlmTableTalkMode.RULE;
            this.llmTableTalkText = llmTableTalkText;
        }

        public BotActionType getType() {
            return type;
        }

        public int getAmount() {
            return amount;
        }

        public LlmTableTalkMode getLlmTableTalkMode() {
            return llmTableTalkMode;
        }

        public String getLlmTableTalkText() {
            return llmTableTalkText;
        }

        public boolean isRuleTableTalk() {
            return llmTableTalkMode == LlmTableTalkMode.RULE;
        }

        /** 规范化执行后保留 LLM 桌边话术字段。 */
        public static BotAction withExecution(BotAction source, BotActionType type, int amount) {
            if (source == null || source.isRuleTableTalk()) {
                return new BotAction(type, amount);
            }
            return new BotAction(type, amount, source.llmTableTalkMode, source.llmTableTalkText);
        }
    }

    /**
     * 公共牌干湿度：用于控制“怕被听牌 / 抓诈唬”的程度。
     */
    public enum BoardDanger {
        DRY,
        WET
    }

    /**
     * 座位位置意识：早位紧，晚位凶，盲注位有独立策略。
     */
    public enum TablePosition {
        EARLY,
        MIDDLE,
        LATE,
        BLINDS
    }

    /**
     * 将玩家当前字段映射为 HandPlanType。
     */
    public static HandPlanType getHandPlanType(DpPlayer bot) {
        if (bot == null)
            return null;
        String t = bot.getNpcHandPlanType();
        if (t == null || t.isEmpty()) {
            return null;
        }
        try {
            return HandPlanType.valueOf(t);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    /**
     * 在 flop 首次行动时为 TAG 初始化整手牌计划。
     * 仅在当前还没有计划（npcHandPlanType 为空）时才会生成一次。
     */
    public static void initHandPlanIfNeededForPostflop(
            DpRoomBO room,
            DpPlayer bot,
            BotType type,
            SimpleStrength strength,
            BoardDanger boardDanger,
            TablePosition position,
            DpUtilSmartContext ctx,
            Random random) {
        if (room == null || bot == null || ctx == null)
            return;
        if (!"flop".equals(room.getCurrentStage()))
            return;
        if (bot.getNpcHandPlanType() != null && !bot.getNpcHandPlanType().isEmpty()) {
            return;
        }

        HandPlanType planType;
        int maxBarrels;
        double aggression;

        int activeVillains = ctx.activeVillains;
        VillainRangeTier villainTier = ctx.villainTier;
        double equityEst = ctx.equityEst;

        // 公共牌主导时下调自评牌力，避免把「board 很强」误当自己很强
        boolean boardDominant = isBoardDominantBestHand(room, bot);

        // 基于牌力 + 牌面 + 参与人数的简单规则生成计划
        SimpleStrength planStrength = strength;
        if (boardDominant) {
            if (planStrength == SimpleStrength.MONSTER)
                planStrength = SimpleStrength.STRONG;
            else if (planStrength == SimpleStrength.STRONG)
                planStrength = SimpleStrength.MEDIUM;
        }

        switch (planStrength) {
            case MONSTER:
            case STRONG:
                if (boardDanger == BoardDanger.DRY) {
                    planType = HandPlanType.VALUE;
                } else {
                    planType = (boardDanger == BoardDanger.WET && activeVillains >= 3)
                            ? HandPlanType.POT_CONTROL
                            : HandPlanType.VALUE;
                }
                break;
            case MEDIUM:
                if (boardDanger == BoardDanger.WET || activeVillains >= 3) {
                    planType = HandPlanType.POT_CONTROL;
                } else {
                    double thinProb = 0.4;
                    if (type == BotType.LAG) {
                        thinProb = 0.52;
                    } else if (type == BotType.NIT) {
                        thinProb = 0.22;
                    }
                    boolean thinValue = activeVillains <= 2 && random.nextDouble() < thinProb;
                    planType = thinValue ? HandPlanType.VALUE : HandPlanType.POT_CONTROL;
                }
                break;
            case WEAK:
            default:
                if (boardDanger == BoardDanger.DRY && activeVillains <= 2) {
                    double bluffProb;
                    if (position == TablePosition.LATE) {
                        bluffProb = (activeVillains <= 1) ? 0.58 : 0.42;
                    } else if (position == TablePosition.BLINDS) {
                        bluffProb = (activeVillains <= 1) ? 0.46 : 0.32;
                    } else {
                        bluffProb = (villainTier == VillainRangeTier.NIT || villainTier == VillainRangeTier.TIGHT)
                                ? 0.28
                                : 0.18;
                    }
                    if (activeVillains >= 2) {
                        bluffProb *= 0.85;
                    }
                    if (equityEst >= 0.30 && !boardDominant) {
                        bluffProb = Math.min(0.72, bluffProb + 0.10);
                    }
                    // 类型差异：LAG 更偏诈唬；NIT/CALL 显著少诈唬；FISH 略收
                    if (type == BotType.NIT) {
                        bluffProb *= 0.38;
                    } else if (type == BotType.LAG) {
                        bluffProb = Math.min(0.82, bluffProb * 1.22);
                    } else if (type == BotType.CALL) {
                        bluffProb *= 0.30;
                    } else if (type == BotType.FISH) {
                        bluffProb *= 0.88;
                    }
                    double potProbeFrac = 0.22;
                    double x = random.nextDouble();
                    if (x < bluffProb) {
                        planType = HandPlanType.BLUFF;
                    } else if (x < bluffProb + potProbeFrac) {
                        planType = HandPlanType.POT_CONTROL;
                    } else {
                        planType = HandPlanType.GIVE_UP;
                    }
                } else {
                    planType = HandPlanType.GIVE_UP;
                }
                break;
        }

        if (boardDominant) {
            if (planType == HandPlanType.VALUE) {
                planType = HandPlanType.POT_CONTROL;
            }
            if (planStrength == SimpleStrength.WEAK && planType == HandPlanType.BLUFF) {
                planType = HandPlanType.POT_CONTROL;
            }
        }

        switch (planType) {
            case VALUE:
                maxBarrels = 2;
                aggression = 0.7;
                break;
            case BLUFF:
                maxBarrels = 1;
                aggression = 0.6;
                break;
            case POT_CONTROL:
                maxBarrels = 1;
                aggression = 0.35;
                break;
            case GIVE_UP:
            default:
                maxBarrels = 0;
                aggression = 0.15;
                break;
        }

        // 在 TAG 基准上按 BotType 微调整手计划（调用方传入 LAG/NIT/CALL/FISH/TAG）
        switch (type) {
            case NIT:
                maxBarrels = Math.max(0, maxBarrels - 1);
                aggression = Math.min(1.0, aggression * 0.72);
                break;
            case LAG:
                if (planType == HandPlanType.BLUFF) {
                    maxBarrels = Math.min(3, maxBarrels + 1);
                }
                aggression = Math.min(1.0, aggression * 1.14);
                break;
            case CALL:
                if (planType == HandPlanType.BLUFF) {
                    planType = HandPlanType.POT_CONTROL;
                    maxBarrels = 1;
                    aggression = 0.34;
                } else {
                    aggression = Math.min(1.0, aggression * 0.86);
                }
                break;
            case FISH:
                aggression = Math.min(1.0, aggression * 1.08);
                break;
            default:
                break;
        }

        if (aggression < 0.0)
            aggression = 0.0;
        if (aggression > 1.0)
            aggression = 1.0;

        DpPlayer aggressor = ctx.aggressor;
        String targetVillain = aggressor != null ? aggressor.getNickname() : null;

        bot.setNpcHandPlanType(planType.name());
        bot.setNpcHandPlanMaxBarrels(maxBarrels);
        bot.setNpcHandPlanAggression(aggression);
        bot.setNpcHandPlanTargetVillain(targetVillain);
    }

    /**
     * 判断在当前阶段是否应当因为 HandPlan 放弃主动大额进攻（例如 GIVE_UP 或已用尽 barrels）。
     */
    public static boolean shouldSkipAggressiveActionByPlan(DpPlayer bot, String stage) {
        if (bot == null || stage == null)
            return false;
        HandPlanType plan = getHandPlanType(bot);
        if (plan == null)
            return false;
        if (!"flop".equals(stage) && !"turn".equals(stage) && !"river".equals(stage)) {
            return false;
        }
        if (plan == HandPlanType.GIVE_UP) {
            return true;
        }
        int barrels = bot.getNpcHandPlanMaxBarrels();
        return barrels <= 0;
    }

    /**
     * 在成功执行一次主动下注/加注后消耗一个“barrel”。
     */
    public static void consumeOneBarrelIfAny(DpPlayer bot, String stage) {
        if (bot == null || stage == null)
            return;
        HandPlanType plan = getHandPlanType(bot);
        if (plan == null)
            return;
        if (!"flop".equals(stage) && !"turn".equals(stage) && !"river".equals(stage)) {
            return;
        }
        int barrels = bot.getNpcHandPlanMaxBarrels();
        if (barrels > 0) {
            bot.setNpcHandPlanMaxBarrels(barrels - 1);
        }
    }

    /**
     * 翻牌生成 HandPlan 之后，允许在 turn/river 根据“牌力明显升级”纠正过度保守的计划。
     *
     * <p>
     * 目的：修复“flop 打了一枪，然后 barrels 消耗到 0，turn/river 仍然全部 check”的观感问题。
     * 当 st==STRONG/MONSTER 且 equityEst 足够高时，强制把计划切回 VALUE，并给至少 1 个 barrel
     * （turn 给 2，保证还能落在河牌拿价值）。
     * </p>
     */
    public static void updateHandPlanForLaterStreetIfNeeded(
            DpRoomBO room,
            DpPlayer bot,
            BotType type,
            SimpleStrength st,
            BoardDanger boardDanger,
            DpUtilSmartContext ctx) {
        if (room == null || bot == null || ctx == null)
            return;
        String stage = room.getCurrentStage();
        if (!"turn".equals(stage) && !"river".equals(stage))
            return;
        if (type != BotType.TAG && type != BotType.NIT && type != BotType.LAG
                && type != BotType.FISH && type != BotType.CALL) {
            return;
        }

        HandPlanType currentPlan = getHandPlanType(bot);
        if (currentPlan == null)
            return;

        // 尽量避免把“公共牌主导的牌面”当成 hero 价值线来乱抬，但仍允许强听/强成时回到 value
        boolean boardDominant = isBoardDominantBestHand(room, bot);

        double equityEst = ctx.equityEst;
        boolean isStrong = (st == SimpleStrength.STRONG || st == SimpleStrength.MONSTER);

        // st 升级到“强牌”才触发纠正：避免弱牌把计划硬改成价值线
        double minEq = (st == SimpleStrength.MONSTER) ? 0.60 : 0.45;
        if (type == BotType.LAG) {
            minEq -= 0.05;
        }
        if (type == BotType.FISH) {
            minEq -= 0.03;
        }
        if (type == BotType.CALL) {
            minEq -= 0.04;
        }
        if (!isStrong || equityEst + RuleNpcConfig.EQUITY_POTODDS_EPS < minEq)
            return;

        // 如果你已经是 VALUE 且 barrels 还够，那就没必要强行改
        int curBarrels = bot.getNpcHandPlanMaxBarrels();
        if (currentPlan == HandPlanType.VALUE && curBarrels > 0)
            return;

        // 给到最小合理 value 进攻额度：turn 争取“还能打到河牌”，river 至少能下注一次
        int newMaxBarrels = "turn".equals(stage) ? 2 : 1;
        double aggression = isBoardDominantBestHand(room, bot)
                ? 0.55
                : 0.8;

        // 若公共牌主导，value 也降级为 POT_CONTROL，避免过度“为公共牌下注”
        HandPlanType newPlan = boardDominant ? HandPlanType.POT_CONTROL : HandPlanType.VALUE;

        // 只在计划太保守/无法再主动出手时才覆盖，否则让原计划保持风格
        boolean needFix = currentPlan == HandPlanType.GIVE_UP
                || currentPlan == HandPlanType.POT_CONTROL
                || (currentPlan == HandPlanType.BLUFF && curBarrels <= 0)
                || curBarrels <= 0;
        if (!needFix)
            return;

        bot.setNpcHandPlanType(newPlan.name());
        bot.setNpcHandPlanMaxBarrels(newMaxBarrels);
        bot.setNpcHandPlanAggression(aggression);
    }

    /**
     * 对手整体牌范围强度（不是具体牌）。
     */
    public enum VillainRangeTier {
        NIT, // 极紧
        TIGHT, // 稍紧
        BALANCED, // 正常
        LOOSE, // 偏松
        MANIAC // 疯狗
    }

    /**
     * 当前一轮动作的可信度：是价值下注还是诈唬的概率感知。
     */
    public enum ActionCredibility {
        HIGH,
        MEDIUM,
        LOW
    }

    /**
     * 规则 NPC 风格参数（0~1），以六个<strong>原始字段</strong>为准：{@code vpip}、{@code pfr}、{@code cbetFreq}、
     * {@code bluffFreq}、{@code callStation}、{@code foldToPressure}。
     *
     * <p>
     * <b>翻前</b>：{@link DpNpcUnifiedPreflopStrategy} 只读 {@code vpip}、{@code pfr}、{@code callStation}、
     * {@code foldToPressure}（外加 mood、座位与局面），<strong>不</strong>经过 {@link #preflopTightness()}、
     * {@link #aggression()} 等兼容 getter。
     * </p>
     * <p>
     * <b>翻后</b>：部分分支仍通过下方 deprecated getter 读「合成口径」，便于旧决策树少改公式。
     * </p>
     * <p>
     * {@code foldToPressure}：面对下注/加注压力时倾向弃牌的程度（越高越「怂」、范围略收紧）；历史上对应旧名
     * {@code checkRaiseFear}。
     * </p>
     * <p>
     * <b>迁移（旧 → 新）</b>
     * </p>
     * <ul>
     * <li>{@code preflopTightness} → 用 {@code 1 - vpip} 近似「翻前紧度」</li>
     * <li>{@code aggression} → 用 {@code (pfr + cbetFreq) / 2} 近似「整体激进」</li>
     * <li>{@code bluffFrequency} → {@code bluffFreq}</li>
     * <li>{@code callStation} → 同名保留</li>
     * <li>{@code stealBlindFrequency} → 删除；由 {@code 0.35*vpip + 0.65*pfr} 合成偷盲/开局侵略近似</li>
     * <li>{@code checkRaiseFear} → {@code foldToPressure}</li>
     * </ul>
     */
    private static final class StyleProfile {
        final double vpip;
        final double pfr;
        final double cbetFreq;
        final double bluffFreq;
        final double callStation;
        final double foldToPressure;

        StyleProfile(double vpip, double pfr, double cbetFreq, double bluffFreq, double callStation,
                double foldToPressure) {
            this.vpip = clamp01(vpip);
            this.pfr = clamp01(pfr);
            this.cbetFreq = clamp01(cbetFreq);
            this.bluffFreq = clamp01(bluffFreq);
            this.callStation = clamp01(callStation);
            this.foldToPressure = clamp01(foldToPressure);
        }

        private static double clamp01(double v) {
            if (v < 0.0)
                return 0.0;
            if (v > 1.0)
                return 1.0;
            return v;
        }

        /** @deprecated 语义兼容：≈ {@code 1 - vpip} */
        double preflopTightness() {
            return clamp01(1.0 - vpip);
        }

        /** @deprecated 语义兼容：≈ {@code (pfr + cbetFreq) / 2} */
        double aggression() {
            return clamp01(0.5 * (pfr + cbetFreq));
        }

        /** @deprecated 语义兼容：即 {@link #bluffFreq} */
        double bluffFrequency() {
            return bluffFreq;
        }

        /** @deprecated 语义兼容：偷盲倾向改由 vpip/pfr 合成 */
        double stealBlindFrequency() {
            return clamp01(0.35 * vpip + 0.65 * pfr);
        }

        /** @deprecated 语义兼容：即 {@link #foldToPressure} */
        double checkRaiseFear() {
            return foldToPressure;
        }

        /** 松凶 LAG：介于疯子与 TAG 之间 */
        static StyleProfile presetLag() {
            return new StyleProfile(0.41, 0.74, 0.79, 0.47, 0.24, 0.22);
        }

        /** 紧凶 TAG */
        static StyleProfile presetTag() {
            return new StyleProfile(0.24, 0.76, 0.82, 0.36, 0.18, 0.22);
        }

        /** 紧弱 Nit */
        static StyleProfile presetNit() {
            return new StyleProfile(0.11, 0.15, 0.28, 0.035, 0.37, 0.92);
        }

        /** 松弱 / 娱乐鱼 */
        static StyleProfile presetFish() {
            return new StyleProfile(0.52, 0.15, 0.20, 0.09, 0.74, 0.38);
        }

        /** 跟注站：极少 fold、极少主动加注 */
        static StyleProfile presetCallStation() {
            return new StyleProfile(0.64, 0.05, 0.10, 0.025, 0.96, 0.10);
        }

        /** 疯子：极松极凶 */
        static StyleProfile presetManiac() {
            return new StyleProfile(0.48, 0.90, 0.88, 0.68, 0.22, 0.10);
        }
    }

    /** 由 {@link com.example.mgdemoplus.npc.CustomNpcStyleSnapshot} 或入座后的 {@link DpPlayer} 六维字段构建。 */
    public static StyleProfile styleProfileFromValues(
            double vpip,
            double pfr,
            double cbetFreq,
            double bluffFreq,
            double callStation,
            double foldToPressure) {
        return new StyleProfile(vpip, pfr, cbetFreq, bluffFreq, callStation, foldToPressure);
    }

    private static final Map<NpcStyle, StyleProfile> STYLE_PROFILE_MAP = new EnumMap<>(NpcStyle.class);

    static {
        STYLE_PROFILE_MAP.put(NpcStyle.LAG, StyleProfile.presetLag());
        STYLE_PROFILE_MAP.put(NpcStyle.TAG, StyleProfile.presetTag());
        STYLE_PROFILE_MAP.put(NpcStyle.NIT, StyleProfile.presetNit());
        STYLE_PROFILE_MAP.put(NpcStyle.FISH, StyleProfile.presetFish());
        STYLE_PROFILE_MAP.put(NpcStyle.CALL_STATION, StyleProfile.presetCallStation());
        STYLE_PROFILE_MAP.put(NpcStyle.MANIAC, StyleProfile.presetManiac());
    }

    /** {@code BOT_LLM_GLOBAL_<seq>} 或单独约定名（与同系列 LLM Bot 一起走 {@link com.example.mgdemoplus.npc.llm.DpLlmNpcDecisionService}）。 */
    public static boolean isGlobalLlmBotNickname(String name) {
        return name != null
                && (PREFIX_BOT_LLM_GLOBAL.equals(name) || name.startsWith(PREFIX_BOT_LLM_GLOBAL + "_"));
    }

    /** {@code BOT_LLM}、{@code BOT_LLM_<seq>} 及 {@linkplain #PREFIX_BOT_LLM_GLOBAL 全局多轮} 变种。 */
    public static boolean isLlmBotNickname(String name) {
        if (name == null) {
            return false;
        }
        if (isGlobalLlmBotNickname(name)) {
            return true;
        }
        return PREFIX_BOT_LLM.equals(name) || name.startsWith(PREFIX_BOT_LLM + "_");
    }

    /**
     * 规则 NPC 昵称：{@code BOT_TAG_<序号>} 等；序号由 {@link com.example.mgdemoplus.common.bo.DpRoomBO#allocateBotNicknameSeqBatch(int)} 分配。
     */
    public static String ruleBotNickname(BotType type, int seq) {
        if (type == null) {
            throw new IllegalArgumentException("type");
        }
        if (seq <= 0) {
            throw new IllegalArgumentException("seq must be positive");
        }
        String prefix;
        switch (type) {
            case LAG:
                prefix = PREFIX_BOT_LAG;
                break;
            case TAG:
                prefix = PREFIX_BOT_TAG;
                break;
            case NIT:
                prefix = PREFIX_BOT_NIT;
                break;
            case FISH:
                prefix = PREFIX_BOT_FISH;
                break;
            case CALL:
                prefix = PREFIX_BOT_CALL;
                break;
            case MANIAC:
                prefix = PREFIX_BOT_MANIAC;
                break;
            default:
                prefix = PREFIX_BOT_TAG;
                break;
        }
        return prefix + "_" + seq;
    }

    /**
     * 根据档位关键字生成昵称；{@code key} 可为 {@code TAG}、{@code BOT_TAG}、{@code lag} 等。
     */
    public static String ruleBotNicknameForKey(String archetypeKey, int seq) {
        return ruleBotNickname(botTypeFromArchetypeKey(archetypeKey), seq);
    }

    private static BotType botTypeFromArchetypeKey(String key) {
        if (key == null || key.isEmpty()) {
            throw new IllegalArgumentException("archetypeKey");
        }
        String k = key.trim().toUpperCase(Locale.ROOT);
        if (k.startsWith("BOT_")) {
            k = k.substring(4);
        }
        switch (k) {
            case "LAG":
                return BotType.LAG;
            case "TAG":
                return BotType.TAG;
            case "NIT":
                return BotType.NIT;
            case "FISH":
                return BotType.FISH;
            case "CALL":
                return BotType.CALL;
            case "MANIAC":
                return BotType.MANIAC;
            default:
                throw new IllegalArgumentException("unknown npc archetype: " + key);
        }
    }

    /** LLM 多实例昵称：{@code BOT_LLM_<序号>} */
    public static String llmBotNickname(int seq) {
        if (seq <= 0) {
            throw new IllegalArgumentException("seq must be positive");
        }
        return PREFIX_BOT_LLM + "_" + seq;
    }

    /** 全局叙事 LLM：{@code BOT_LLM_GLOBAL_<序号>}（与 {@link #llmBotNickname} 共用房间序号生成器）。 */
    public static String llmGlobalBotNickname(int seq) {
        if (seq <= 0) {
            throw new IllegalArgumentException("seq must be positive");
        }
        return PREFIX_BOT_LLM_GLOBAL + "_" + seq;
    }

    /** 自定义调参 NPC：{@code BOT_CUSTOM_<序号>} */
    public static String customBotNickname(int seq) {
        if (seq <= 0) {
            throw new IllegalArgumentException("seq must be positive");
        }
        return PREFIX_BOT_CUSTOM + "_" + seq;
    }

    /** {@code BOT_CUSTOM} 或 {@code BOT_CUSTOM_<seq>}；与 LLM 线互斥。 */
    public static boolean isCustomBotNickname(String name) {
        return name != null
                && (PREFIX_BOT_CUSTOM.equals(name) || name.startsWith(PREFIX_BOT_CUSTOM + "_"));
    }

    private static BotType resolveRuleBotType(String nickname) {
        if (nickname == null || !nickname.startsWith("BOT_")) {
            return null;
        }
        if (LEGACY_BOT_SHARK_NICKNAME.equals(nickname) || LEGACY_BOT_TAG_NICKNAME.equals(nickname)) {
            return BotType.TAG;
        }
        if (LEGACY_BOT_FISH_NICKNAME.equals(nickname)) {
            return BotType.FISH;
        }
        if (LEGACY_BOT_MANIAC_NICKNAME.equals(nickname)) {
            return BotType.MANIAC;
        }
        // 最长前缀优先，避免 BOT_MANIAC 被 BOT_MAN 误匹配
        if (nickname.startsWith(PREFIX_BOT_MANIAC + "_") || PREFIX_BOT_MANIAC.equals(nickname)) {
            return BotType.MANIAC;
        }
        if (nickname.startsWith(PREFIX_BOT_CALL + "_") || PREFIX_BOT_CALL.equals(nickname)) {
            return BotType.CALL;
        }
        if (nickname.startsWith(PREFIX_BOT_LAG + "_") || PREFIX_BOT_LAG.equals(nickname)) {
            return BotType.LAG;
        }
        if (nickname.startsWith(PREFIX_BOT_TAG + "_") || PREFIX_BOT_TAG.equals(nickname)) {
            return BotType.TAG;
        }
        if (nickname.startsWith(PREFIX_BOT_NIT + "_") || PREFIX_BOT_NIT.equals(nickname)) {
            return BotType.NIT;
        }
        if (nickname.startsWith(PREFIX_BOT_FISH + "_") || PREFIX_BOT_FISH.equals(nickname)) {
            return BotType.FISH;
        }
        return null;
    }

    /**
     * UI / JSON 短显；实现见 {@link com.example.mgdemoplus.utils.DpUtilNpcDisplayNickname}。
     */
    public static String displayNicknameForUi(String nickname) {
        return DpUtilNpcDisplayNickname.shortenForUi(nickname);
    }

    /** 与 {@link #isBotPlayer(DpPlayer)} 一致，仅按昵称判断（观众席只有昵称无 DpPlayer）。 */
    public static boolean isBotNickname(String name) {
        if (name == null || !name.startsWith("BOT_")) {
            return false;
        }
        if (isLlmBotNickname(name)) {
            return true;
        }
        if (isCustomBotNickname(name)) {
            return true;
        }
        return resolveRuleBotType(name) != null;
    }

    public static boolean isBotPlayer(DpPlayer p) {
        if (p == null)
            return false;
        return isBotNickname(p.getNickname());
    }

    public static BotType getBotTypeByNickname(String nickname) {
        if (nickname == null || isLlmBotNickname(nickname) || isCustomBotNickname(nickname)) {
            return null;
        }
        return resolveRuleBotType(nickname);
    }

    /** {@link BotType} → {@link NpcStyle}（{@link BotType#CALL} → {@link NpcStyle#CALL_STATION}）。 */
    private static NpcStyle npcStyleForBotType(BotType type) {
        if (type == null) {
            return NpcStyle.TAG;
        }
        switch (type) {
            case LAG:
                return NpcStyle.LAG;
            case TAG:
                return NpcStyle.TAG;
            case NIT:
                return NpcStyle.NIT;
            case FISH:
                return NpcStyle.FISH;
            case CALL:
                return NpcStyle.CALL_STATION;
            case MANIAC:
                return NpcStyle.MANIAC;
            default:
                return NpcStyle.TAG;
        }
    }

    /**
     * 将粗粒度牌力 + 当前阶段映射为一个 0~1 的“赢率估计桶”（供规则 NPC / BOT_LLM 与底池赔率对照）。
     * 在原有 rk 四档基础上，增加：翻前手牌结构、翻后真实成牌类别（{@link HandStrength}）校正，缓解「凡 WEAK 皆 0.25」的卡死问题。
     */
    private static double estimateEquityBucket(SimpleStrength st, String stage,
            List<String> hole, List<String> community, HandStrength hsMade) {
        if (st == null) {
            return clampEquityEstimate(0.25);
        }
        boolean preflop = "preflop".equals(stage);
        double base;
        switch (st) {
            case MONSTER:
                base = preflop ? 0.75 : 0.85;
                break;
            case STRONG:
                base = preflop ? 0.60 : 0.65;
                break;
            case MEDIUM:
                base = preflop ? 0.40 : 0.45;
                break;
            case WEAK:
            default:
                base = preflop ? 0.25 : 0.20;
                break;
        }
        if (preflop) {
            base = applyPreflopHoleEquityAdjustments(base, hole);
            return clampEquityEstimate(base);
        }
        if (hsMade != null) {
            base = applyPostflopMadeHandEquityAdjustments(base, st, hsMade, hole, community);
        }
        // 翻后在 flop / turn 结合听牌微调：有强听牌时弱/中等牌适度抬高估计赢率
        if ("flop".equals(stage) || "turn".equals(stage)) {
            boolean strongDraw = hasStrongFlushDraw(hole, community) || hasOpenEndedStraightDraw(hole, community);
            if (strongDraw) {
                if (st == SimpleStrength.MEDIUM) {
                    base = Math.max(base, 0.50);
                } else if (st == SimpleStrength.WEAK) {
                    base = Math.max(base, 0.30);
                }
            }
        }
        return clampEquityEstimate(base);
    }

    private static double clampEquityEstimate(double v) {
        if (v < 0.06) {
            return 0.06;
        }
        if (v > 0.93) {
            return 0.93;
        }
        return Math.round(v * 1000.0) / 1000.0;
    }

    /** 翻前：小对隐含赔率、同花连张/结构牌、AXs / 宽张等细调（不改变 rk，只调胜率桶）。 */
    private static double applyPreflopHoleEquityAdjustments(double base, List<String> hole) {
        if (hole == null || hole.size() < 2) {
            return base;
        }
        int r1 = getRankFromCard(hole.get(0));
        int r2 = getRankFromCard(hole.get(1));
        if (r1 <= 0 || r2 <= 0) {
            return base;
        }
        boolean suited = holeSuitsEqual(hole.get(0), hole.get(1));
        int hi = Math.max(r1, r2);
        int lo = Math.min(r1, r2);
        int gap = hi - lo;
        double b = base;
        if (gap == 0) {
            if (hi <= 6) {
                b = Math.max(b, 0.37);
            } else if (hi <= 8) {
                b = Math.max(b, 0.39);
            }
            return b;
        }
        if (suited) {
            if (gap <= 2 && hi >= 5 && hi <= 12 && lo >= 4) {
                b += 0.045;
            }
            if (hi == 14 && lo >= 10) {
                b += 0.04;
            } else if (hi == 14 && lo >= 5 && lo <= 9) {
                b += 0.03;
            }
        } else {
            if (hi >= 12 && lo >= 10 && gap <= 2) {
                b += 0.035;
            }
            if (hi == 14 && lo >= 11) {
                b += 0.03;
            }
        }
        return b;
    }

    private static boolean holeSuitsEqual(String c1, String c2) {
        if (c1 == null || c2 == null || !c1.contains("_") || !c2.contains("_")) {
            return false;
        }
        return Objects.equals(c1.split("_", 2)[0], c2.split("_", 2)[0]);
    }

    private static int maxRankOnBoard(List<String> community) {
        if (community == null) {
            return 0;
        }
        int m = 0;
        for (String c : community) {
            m = Math.max(m, getRankFromCard(c));
        }
        return m;
    }

    /**
     * 翻后：按真实牌型大类 + 是否板对、是否超对/顶对等校正胜率桶（仍为估算，非蒙特卡洛）。
     */
    private static double applyPostflopMadeHandEquityAdjustments(double base, SimpleStrength st,
            HandStrength hs, List<String> hole, List<String> community) {
        if (hs == null || hole == null || community == null) {
            return base;
        }
        int cat = hs.rankCategory;
        List<Integer> rk = hs.ranks;
        int boardHigh = maxRankOnBoard(community);

        if (cat >= 7) {
            return Math.max(base, 0.82);
        }
        if (cat == 6 || cat == 5) {
            return Math.max(base, 0.68);
        }
        if (cat == 4) {
            return Math.max(base, 0.52);
        }
        if (cat == 3) {
            return Math.max(base, 0.44);
        }
        if (cat == 2 && rk != null && !rk.isEmpty()) {
            int pr = rk.get(0);
            if (DpUtilHandEvaluator.isPlayingBoardPairOnly(hs, hole, community)) {
                return Math.min(base, 0.19);
            }
            if (pr > boardHigh) {
                return Math.max(base, 0.62);
            }
            if (pr == boardHigh) {
                return Math.max(base, 0.53);
            }
            if (pr >= 10) {
                return Math.max(base, 0.37);
            }
            return Math.max(base, 0.28);
        }
        if (cat == 1 && rk != null && !rk.isEmpty()) {
            int k0 = rk.get(0);
            if (k0 == 14) {
                return Math.max(base, 0.24);
            }
        }
        return base;
    }

    /**
     * 简化版：检测是否有较强的同花听牌（任一花色达到 4 张，且 hero 至少拥有其中 1 张）。
     */
    private static boolean hasStrongFlushDraw(List<String> hole, List<String> community) {
        if (hole == null || community == null)
            return false;
        Map<String, Integer> suitCount = new HashMap<>();
        for (String c : community) {
            if (c == null || !c.contains("_"))
                continue;
            String suit = c.split("_", 2)[0];
            suitCount.put(suit, suitCount.getOrDefault(suit, 0) + 1);
        }
        if (suitCount.isEmpty())
            return false;
        // 找到公共牌中出现最多的花色
        String bestSuit = null;
        int max = 0;
        for (Map.Entry<String, Integer> e : suitCount.entrySet()) {
            if (e.getValue() > max) {
                max = e.getValue();
                bestSuit = e.getKey();
            }
        }
        if (bestSuit == null || max < 3) {
            return false;
        }
        // 查看 hero 是否至少有一张该花色的牌
        int heroSuitCount = 0;
        for (String c : hole) {
            if (c == null || !c.contains("_"))
                continue;
            String suit = c.split("_", 2)[0];
            if (bestSuit.equals(suit)) {
                heroSuitCount++;
            }
        }
        return (max + heroSuitCount) >= 4 && heroSuitCount >= 1;
    }

    /**
     * 简化版：检测是否有开放式顺子听牌（综合 hole+community，点数上存在长度为 4 的连续结构）。
     */
    private static boolean hasOpenEndedStraightDraw(List<String> hole, List<String> community) {
        List<String> all = new ArrayList<>();
        if (hole != null)
            all.addAll(hole);
        if (community != null)
            all.addAll(community);
        if (all.size() < 4)
            return false;
        Set<Integer> ranks = new HashSet<>();
        for (String c : all) {
            int r = getRankFromCard(c);
            if (r > 0) {
                ranks.add(r);
            }
        }
        if (ranks.size() < 4)
            return false;
        List<Integer> list = new ArrayList<>(ranks);
        Collections.sort(list);
        int run = 1;
        int maxRun = 1;
        for (int i = 1; i < list.size(); i++) {
            if (list.get(i) == list.get(i - 1) + 1) {
                run++;
                maxRun = Math.max(maxRun, run);
                if (run >= 4) {
                    return true;
                }
            } else if (list.get(i) > list.get(i - 1) + 1) {
                run = 1;
            }
        }
        return maxRun >= 4;
    }

    /**
     * 是否属于“公共牌主导”或等同牌力几乎全来自公面：
     * - 河牌及以后：hero 的最佳 5 张牌完全不使用任何手牌（纯公共牌成牌）。
     * - Flop/Turn：无法出现「5 张全在公面」，则退化为「公对面仅拼踢脚」等弱相对牌力情形。
     *
     * <p>
     * 用于翻后 HandPlan 避免误判「板很强 = 我很强」。
     * </p>
     */
    static boolean isBoardDominantBestHand(DpRoomBO room, DpPlayer hero) {
        if (room == null || hero == null)
            return false;
        List<String> board = room.getCommunityCards();
        List<String> hole = hero.getHoleCards();
        if (board == null || board.size() < 3 || hole == null || hole.size() < 2) {
            return false;
        }
        List<String> all = new ArrayList<>(hole);
        all.addAll(board);
        HandStrength hs = evaluateBestHand(all);
        if (board.size() >= 5) {
            List<String> best5 = DpUtilHandEvaluator.getBestHandCards(all);
            if (best5 == null || best5.size() != 5) {
                return false;
            }
            String h1 = hole.get(0);
            String h2 = hole.get(1);
            boolean usedHole = (h1 != null && best5.contains(h1)) || (h2 != null && best5.contains(h2));
            return !usedHole;
        }
        return hs != null && isPlayingBoardPairOnly(hs, hole, board);
    }

    /**
     * 多人底池（含 hero 在内 ≥3 人仍在局）时，在基础弃牌概率上追加的增量，随 {@code activeVillains}（仅 villains 人数）
     * 与 {@code foldToPressure} 缩放；0 表示不加。
     */
    public static double multiwayFoldProbBoost(int activeVillains, double foldToPressure) {
        if (activeVillains < 2) {
            return 0.0;
        }
        double depth = Math.min(1.0, (activeVillains - 1) / 3.0);
        double fear = Math.max(0.0, Math.min(1.0, foldToPressure));
        return RuleNpcConfig.MULTIWAY_FOLD_BOOST * depth * (0.45 + 0.55 * fear);
    }

    private static SimpleStrength estimateCurrentStrength(DpRoomBO room, DpPlayer bot) {
        List<String> hole = bot.getHoleCards();
        List<String> community = room.getCommunityCards();
        if (hole == null || hole.size() < 2) {
            return SimpleStrength.WEAK;
        }
        List<String> all = new ArrayList<>(hole);
        if (community != null) {
            all.addAll(community);
        }
        if (all.size() < 5) {
            // 公共牌不足 3 张时，用简单 preflop 规则
            return toSimpleStrength(null, room.getCurrentStage(), hole, community);
        }
        DpUtilHandEvaluator.HandStrength hs = evaluateBestHand(all);
        return toSimpleStrength(hs, room.getCurrentStage(), hole, community);
    }

    /**
     * 判断公共牌面是否“湿”（容易成大牌/花顺危险）还是“干”（较安全）:
     * - 如果未发足够公共牌（少于3张），直接判为干牌面（DRY）。
     * - 如果有任意花色出现3张及以上（同花潜力），判为湿牌面（WET）。
     * - 如果有至少3张不同点数且其中有3+张是连续的（顺子潜力），也判为湿牌面（WET）。
     * - 否则，认为是干牌面（DRY）。
     */
    private static BoardDanger evaluateBoardDanger(List<String> communityCards) {
        // 公共牌为空或不足3张必定是干燥牌面
        if (communityCards == null || communityCards.size() < 3) {
            return BoardDanger.DRY;
        }
        Set<Integer> ranks = new HashSet<>();        // 记录已出现的点数
        Map<String, Integer> suitCount = new HashMap<>(); // 记录每种花色的数量
        for (String c : communityCards) {
            if (c == null || !c.contains("_"))
                continue;
            String[] parts = c.split("_", 2);
            if (parts.length != 2)
                continue;
            String suit = parts[0];
            String rankStr = parts[1];
            int r = CARD_RANK_MAP.getOrDefault(rankStr, 0);
            if (r > 0) {
                ranks.add(r);
            }
            suitCount.put(suit, suitCount.getOrDefault(suit, 0) + 1);
        }
        // 【同花危险】只要某种花色数量>=3，则有同花潜力视为湿牌面
        for (Integer cnt : suitCount.values()) {
            if (cnt >= 3) {
                return BoardDanger.WET;
            }
        }
        // 【顺子危险】只要有3张及以上不同点数，且其中有3+张是连续的也判为湿牌面
        if (ranks.size() >= 3) {
            List<Integer> list = new ArrayList<>(ranks);
            Collections.sort(list);
            int maxRun = 1;
            int currentRun = 1;
            for (int i = 1; i < list.size(); i++) {
                if (list.get(i) == list.get(i - 1) + 1) {
                    currentRun++;
                    maxRun = Math.max(maxRun, currentRun);
                } else if (list.get(i) > list.get(i - 1) + 1) {
                    currentRun = 1;
                }
            }
            // 只要有连续3张牌（潜在顺子结构），也视为湿牌面
            if (maxRun >= 3) {
                return BoardDanger.WET;
            }
        }
        // 都没有则为干燥牌面
        return BoardDanger.DRY;
    }

    /**
     * 计算机器人在牌桌上的大致位置：早位 / 中位 / 晚位 / 盲注。
     */
    private static TablePosition getTablePosition(DpRoomBO room, DpPlayer bot) {
        List<DpPlayer> ps = room.getPlayers();
        if (ps == null || ps.isEmpty())
            return TablePosition.MIDDLE;
        int idx = ps.indexOf(bot);
        if (idx < 0)
            return TablePosition.MIDDLE;

        // 盲注位单独处理
        if (bot.getBlind() == 1 || bot.getBlind() == 2) {
            return TablePosition.BLINDS;
        }

        int dealerIdx = -1;
        for (int i = 0; i < ps.size(); i++) {
            if (ps.get(i).isDealer()) {
                dealerIdx = i;
                break;
            }
        }
        if (dealerIdx < 0)
            return TablePosition.MIDDLE;

        int size = ps.size();
        int relative = (idx - dealerIdx + size) % size;
        if (relative == 0 || relative == 1) {
            return TablePosition.LATE;
        }
        if (relative <= size / 3) {
            return TablePosition.EARLY;
        }
        return TablePosition.MIDDLE;
    }

    /**
     * 利用最近 10 手，读出对手大致牌范围强度。
     */
    private static VillainRangeTier estimateVillainRangeTier(DpPlayerStats stats) {
        if (stats == null) {
            return VillainRangeTier.BALANCED;
        }
        double vpip = stats.getOverallParticipationRate();
        double raise = stats.getOverallRaiseRate();
        if (vpip < 0.15 && raise < 0.08) {
            return VillainRangeTier.NIT;
        }
        if (vpip < 0.28 && raise < 0.15) {
            return VillainRangeTier.TIGHT;
        }
        if (vpip > 0.55 && raise > 0.35) {
            return VillainRangeTier.MANIAC;
        }
        if (vpip > 0.45) {
            return VillainRangeTier.LOOSE;
        }
        return VillainRangeTier.BALANCED;
    }

    /**
     * multi-way 牌局中每个对手的简要信息，仅供 Shark/TAG 在多人底池下做更细的决策调整。
     */
    public static class MultiwayVillainInfo {
        public final String nickname;
        public final boolean behindHero;
        public final double stackBB;
        public final VillainRangeTier tier;
        public final boolean hasRaisedThisStreet;

        public MultiwayVillainInfo(String nickname,
                boolean behindHero,
                double stackBB,
                VillainRangeTier tier,
                boolean hasRaisedThisStreet) {
            this.nickname = nickname;
            this.behindHero = behindHero;
            this.stackBB = stackBB;
            this.tier = tier;
            this.hasRaisedThisStreet = hasRaisedThisStreet;
        }
    }

    /**
     * 针对某个主要对手的简单 counter-strategy 建议：
     * 由该玩家最近若干手的行为统计推导，不追求 GTO，只用于微调 Shark/TAG 的整体倾向。
     */
    public static class CounterStrategyProfile {
        /**
         * 是否适合对该玩家做更多 thin value（例如：他 limp/宽范围明显偏多）。
         * 调用方：true 时可略微增加中等牌的 value bet 频率与下注尺度。
         */
        public final boolean moreThinValue;
        /**
         * 是否适合对该玩家在 turn/river 更愿意 call down：
         * 典型触发：该玩家在进攻线中经常以中弱牌摊牌（finalRankCategory <= 4）。
         */
        public final boolean callDownMore;
        /**
         * 是否在面对该玩家的大额下注时整体更偏向弃牌：
         * 典型触发：该玩家大注摊牌几乎总是强牌。
         */
        public final boolean foldMoreToBigBets;
        /**
         * 是否建议整体减少对该玩家的 bluff 频率：
         * 通常与 foldMoreToBigBets 联动，用于对“诚实且少 bluff”的玩家。
         */
        public final boolean bluffLess;
        /**
         * 是否建议在 river 增加轻量 bluff catch 的频率：
         * 通常与 callDownMore 联动，用于对“进攻线经常只是中弱牌”的玩家。
         */
        public final boolean bluffCatchMore;

        CounterStrategyProfile(boolean moreThinValue,
                boolean callDownMore,
                boolean foldMoreToBigBets,
                boolean bluffLess,
                boolean bluffCatchMore) {
            this.moreThinValue = moreThinValue;
            this.callDownMore = callDownMore;
            this.foldMoreToBigBets = foldMoreToBigBets;
            this.bluffLess = bluffLess;
            this.bluffCatchMore = bluffCatchMore;
        }

        static final CounterStrategyProfile NEUTRAL = new CounterStrategyProfile(false, false, false, false, false);
    }

    /**
     * 根据最近若干手对局的统计，为单个对手生成一个粗略的 counter-strategy 建议。
     * - windowSize 约为 10 手，样本过少时自动退化为中性配置。
     */
    private static CounterStrategyProfile analyzeVillainCounterStrategy(DpPlayerStats stats) {
        if (stats == null || stats.getRecentHands().isEmpty()) {
            return CounterStrategyProfile.NEUTRAL;
        }
        int window = 10;
        int limpCount = 0;
        int gaveUpTurn = 0;
        int gaveUpRiver = 0;
        int aggroShowdownSample = 0;
        int weakShowdown = 0;

        int processed = 0;
        for (DpPlayerStats.SingleHandStats h : stats.getRecentHands()) {
            if (processed++ >= window)
                break;
            if (h.isLimpedPreflop())
                limpCount++;
            if (h.isGaveUpTurnAfterRaise())
                gaveUpTurn++;
            if (h.isGaveUpRiverAfterRaise())
                gaveUpRiver++;
            if (h.isRaised() && h.isWentToShowdown()) {
                aggroShowdownSample++;
                if (h.getFinalRankCategory() > 0 && h.getFinalRankCategory() <= 4) {
                    weakShowdown++;
                }
            }
        }
        double hands = Math.max(1.0, Math.min(window, stats.getSampleCount()));
        double limpRate = limpCount / hands;
        double turnGiveUpRate = gaveUpTurn / hands;
        double riverGiveUpRate = gaveUpRiver / hands;
        double weakShowdownRate = aggroShowdownSample == 0 ? 0.0 : (weakShowdown * 1.0 / aggroShowdownSample);

        boolean moreThinValue = limpRate > 0.25;
        boolean callDownMore = weakShowdownRate > 0.4;
        boolean foldMoreToBigBets = (weakShowdownRate < 0.1) && (turnGiveUpRate < 0.05) && (riverGiveUpRate < 0.05);
        boolean bluffLess = foldMoreToBigBets;
        boolean bluffCatchMore = callDownMore;

        return new CounterStrategyProfile(moreThinValue, callDownMore, foldMoreToBigBets, bluffLess, bluffCatchMore);
    }

    /**
     * 读当前加注动作的可信度：是价值下注还是偏诈唬。
     */
    private static ActionCredibility estimateActionCredibility(DpRoomBO room, DpPlayer bot, DpPlayer aggressor) {
        if (aggressor == null || aggressor == bot) {
            return ActionCredibility.MEDIUM;
        }
        Map<String, DpPlayerStats> statsMap = room.getPlayerStatsMap();
        DpPlayerStats stats = statsMap != null ? statsMap.get(aggressor.getNickname()) : null;
        VillainRangeTier tier = estimateVillainRangeTier(stats);

        if (tier == VillainRangeTier.NIT || tier == VillainRangeTier.TIGHT) {
            return ActionCredibility.HIGH;
        }
        if (tier == VillainRangeTier.MANIAC || tier == VillainRangeTier.LOOSE) {
            return ActionCredibility.LOW;
        }
        if (stats != null && !stats.getRecentHands().isEmpty()) {
            DpPlayerStats.SingleHandStats last = stats.getRecentHands().peekLast();
            if (last != null && last.isRaised() && !last.isWentToShowdown()) {
                return ActionCredibility.LOW;
            }
        }
        return ActionCredibility.MEDIUM;
    }

    /**
     * 底池赔率：call / (pot + call)。规则型 NPC 一律使用真值（不做人为噪声）。
     */
    private static double computePotOdds(DpRoomBO room, DpPlayer bot, int callAmount) {
        int pot = room.getPot();
        int denom = pot + callAmount;
        if (callAmount <= 0 || denom <= 0) {
            return 0.0;
        }
        return callAmount * 1.0 / denom;
    }

    /**
     * Hero 剩余可投入筹码相对当前底池的比值（粗略可玩深度代理；非与单一对手对齐的严格 SPR）。
     * 用于翻后压注/过控尺度和投入意愿微调。
     */
    public static double computeHeroPotSpr(DpRoomBO room, DpPlayer hero) {
        if (room == null || hero == null) {
            return 8.0;
        }
        int pot = room.getPot();
        int denom = Math.max(1, pot);
        return hero.getChips() * 1.0 / denom;
    }

    /**
     * 用粗粒度胜率桶与底池赔率对齐，修正弃牌概率（TAG/NIT/Fish/Call 以及 LAG/Maniac 面对下注或全下时的轻量接入）。
     *
     * @param equityBehindBoostScale 当我们「明显落后赔率」时，对 {@link RuleNpcConfig#EQUITY_FOLD_BOOST} 的缩放：
     *                               TAG≈1.0，NIT≈1.12，Fish≈0.55，Call≈0.72，LAG≈0.78，Maniac≈0.52
     */
    public static double adjustFoldProbForEquityVsPotOdds(
            double baseFold,
            int callAmount,
            double potOdds,
            double equityEst,
            int activeVillains,
            double equityBehindBoostScale) {
        if (callAmount <= 0 || potOdds <= 0) {
            return baseFold;
        }
        if (equityEst <= 0) {
            return baseFold;
        }
        double eps = RuleNpcConfig.EQUITY_POTODDS_EPS;
        double margin = RuleNpcConfig.EQUITY_POTODDS_MARGIN;
        double mw = 1.0 + 0.22 * Math.max(0, activeVillains - 1);
        equityBehindBoostScale = Math.max(0.0, Math.min(1.5, equityBehindBoostScale));
        if (equityEst + eps < potOdds - margin) {
            return Math.min(1.0, baseFold + RuleNpcConfig.EQUITY_FOLD_BOOST * mw * equityBehindBoostScale);
        }
        if (equityEst > potOdds + margin + eps) {
            return Math.max(0.0, baseFold * RuleNpcConfig.EQUITY_FOLD_SHRINK);
        }
        return baseFold;
    }

    /**
     * 超阈值/大额投入随机支路：按胜率桶 vs 底池赔率平移 [0,1) 均匀抽样 y，弱化数学上离谱的 jam/fold/call 组合。
     *
     * @param badIncreasesY {@code true}：落后赔率时增大 y（适用于「y 越大越偏向弃牌/靠后支路」，如 LAG 尾树）；<br>
     *                        {@code false}：落后赔率时减小 y（适用于「y 越小越保守」，如 TAG 强牌 y&lt;0.8 为跟注控池）。
     */
    public static double skewCommitThresholdRandom(
            double y,
            DpUtilSmartContext ctx,
            int callAmount,
            double shiftScale,
            boolean badIncreasesY) {
        if (callAmount <= 0 || ctx == null) {
            return y;
        }
        double po = ctx.potOdds;
        double eq = ctx.equityEst;
        if (po <= 0 || eq <= 0) {
            return y;
        }
        shiftScale = Math.max(0.0, Math.min(1.5, shiftScale));
        double eps = RuleNpcConfig.EQUITY_POTODDS_EPS;
        double margin = RuleNpcConfig.EQUITY_POTODDS_MARGIN;
        double mw = 1.0 + 0.12 * Math.max(0, ctx.activeVillains - 1);
        double d = 0.11 * shiftScale * mw;
        if (eq + eps < po - margin) {
            if (badIncreasesY) {
                y = Math.min(1.0, y + d);
            } else {
                y = Math.max(0.0, y - d);
            }
        } else if (eq > po + margin + eps) {
            if (badIncreasesY) {
                y = Math.max(0.0, y - d);
            } else {
                y = Math.min(1.0, y + d);
            }
        }
        return y;
    }

    /**
     * 本手牌场上其他未弃牌玩家的筹码分布（后手 stack 视角）。
     * 用于区分短码（short stack）和深码（deep stack），指导翻后尺度与投入倾向。
     */
    public static class StackContext {
        public final int minStack;
        public final int maxStack;
        public final int avgStack;
        public final double minStackBB;
        public final double maxStackBB;
        public final double avgStackBB;

        StackContext(int minStack, int maxStack, int avgStack, int bb) {
            this.minStack = minStack;
            this.maxStack = maxStack;
            this.avgStack = avgStack;
            this.minStackBB = bb > 0 ? minStack * 1.0 / bb : 0.0;
            this.maxStackBB = bb > 0 ? maxStack * 1.0 / bb : 0.0;
            this.avgStackBB = bb > 0 ? avgStack * 1.0 / bb : 0.0;
        }
    }

    /**
     * 统计除自己外、所有“未弃牌且未离桌”的玩家后手筹码。
     * - minStackBB <= 20 视为有典型短码病人
     * - minStackBB > 60 & avgStackBB > 80 视为整体深码桌
     */
    private static StackContext analyzeStacks(DpRoomBO room, DpPlayer hero) {
        if (room == null || hero == null) {
            return new StackContext(0, 0, 0, DpRoomBO.DEFAULT_BIG_BLIND_CHIPS);
        }
        int bb = room.getBigBlindChips();
        int min = Integer.MAX_VALUE;
        int max = 0;
        int sum = 0;
        int count = 0;
        List<DpPlayer> players = room.getPlayers();
        if (players != null) {
            for (DpPlayer p : players) {
                if (p == null)
                    continue;
                if (p.isFold() || p.isLeftThisHand())
                    continue;
                if (p == hero)
                    continue;
                int stack = p.getChips();
                if (stack <= 0)
                    continue;
                if (stack < min) {
                    min = stack;
                }
                if (stack > max) {
                    max = stack;
                }
                sum += stack;
                count++;
            }
        }
        if (count == 0 || min == Integer.MAX_VALUE) {
            return new StackContext(0, 0, 0, bb);
        }
        int avg = sum / count;
        return new StackContext(min, max, avg, bb);
    }

    /**
     * 计算 hero 与单个对手的筹码比率（heroChips / villainChips），双方筹码均 >0 时才有意义。
     */
    static double computeHeroVsVillainStackRatio(DpPlayer hero, DpPlayer villain) {
        if (hero == null || villain == null) {
            return 1.0;
        }
        int heroChips = hero.getChips();
        int vilChips = villain.getChips();
        if (heroChips <= 0 || vilChips <= 0) {
            return 1.0;
        }
        return heroChips * 1.0 / vilChips;
    }

    /**
     * 根据最近若干手的摊牌结果，估计一个 0~1 的“摊牌 bluff 倾向”：
     * - 只统计「本手有主动加注 + 走到摊牌」的局；
     * - 若这类局中，摊牌时牌力大多数只是高牌/一对/两对（三类中等偏弱），则视为 bluff 倾向偏高。
     */
    private static double estimateShowdownBluffiness(DpPlayerStats stats) {
        if (stats == null || stats.getRecentHands().isEmpty()) {
            return 0.0;
        }
        int sample = 0;
        int weakOrMediumShowdown = 0;
        // 只看最近 N 手（例如 10 手）即可
        int limit = 10;
        int processed = 0;
        for (DpPlayerStats.SingleHandStats hand : stats.getRecentHands()) {
            if (processed >= limit) {
                break;
            }
            processed++;
            if (!hand.isWentToShowdown() || !hand.isRaised()) {
                continue;
            }
            sample++;
            int cat = hand.getFinalRankCategory();
            // 参考 HandStrength.rankCategory：1 高牌, 2 一对, 3 两对, 4 三条, ...
            if (cat <= 4) {
                weakOrMediumShowdown++;
            }
        }
        if (sample == 0) {
            return 0.0;
        }
        double v = weakOrMediumShowdown * 1.0 / sample;
        if (v < 0)
            v = 0;
        if (v > 1)
            v = 1;
        return v;
    }

    /**
     * 在定时任务中调用：轮到规则 NPC 且校验通过则给出 {@link BotAction}。
     * {@code enabled=true} 时先经 {@code nextBotActionTime} 两阶段排期；返回 null 表示尚未到点或不该由该机器人行动。
     */
    public static BotAction decideActionIfReady(DpRoomBO room, DpPlayer bot) {
        if (room == null || bot == null || !room.isPlaying()) {
            return null;
        }
        int actorIndex = room.getCurrentActorIndex();
        if (actorIndex < 0 || actorIndex >= room.getPlayers().size()) {
            return null;
        }
        // 防御：当前行动位必须就是这个 bot，且未弃牌/未 all-in
        DpPlayer current = room.getPlayers().get(actorIndex);
        if (current != bot) {
            return null;
        }
        if (bot.isFold() || bot.isAllIn() || bot.isLeftThisHand()) {
            return null;
        }

        if (isLlmBotNickname(bot.getNickname())) {
            return null;
        }

        long now = System.currentTimeMillis();
        long deadline = bot.getNextBotActionTime();

        if (deadline <= 0) {
            long delay = DpNpcRuleThinkSampler.sampleDelayMs(
                    buildHandRandomForRuleThink(room, bot));
            if (delay > 0) {
                bot.setNextBotActionTime(now + delay);
                return null;
            }
        } else if (now < deadline) {
            return null;
        }

        BotAction action;
        if (isCustomBotNickname(bot.getNickname())) {
            action = decideCustomBotAction(room, bot);
        } else {
            BotType type = getBotTypeByNickname(bot.getNickname());
            if (type == null) {
                bot.setNextBotActionTime(0L);
                return null;
            }
            action = decideBotAction(room, bot, type);
        }
        bot.setNextBotActionTime(0L);
        return action;
    }

    /**
     * 规则 NPC 决策用的 {@link Random}（仅用于概率抽样，不参与洗牌发牌）。
     *
     * @see #NPC_HAND_SEED_FOR_DECISIONS
     */
    private static Random buildHandRandom(DpRoomBO room, DpPlayer bot) {
        if (!NPC_HAND_SEED_FOR_DECISIONS) {
            return new Random();
        }
        long seed = room != null ? room.getCurrentHandSeed() : System.currentTimeMillis();
        int seatIndex = -1;
        if (room != null && room.getPlayers() != null) {
            seatIndex = room.getPlayers().indexOf(bot);
        }
        if (seatIndex < 0) {
            seatIndex = 0;
        }
        seed ^= (long) (31 * seatIndex + 17);
        return new Random(seed);
    }

    /**
     * 规则 NPC 思考延时抽样 RNG：与决策 RNG 同源但 salt 区分，避免与牌力决策共用同一 {@code nextDouble()} 序列。
     */
    public static Random buildHandRandomForRuleThink(DpRoomBO room, DpPlayer bot) {
        return buildHandRandomForTableTalk(room, bot, 0x5448494E4B5F4E50L); // "THINK_NP"
    }

    /**
     * 桌边话术抽样：与决策 RNG 同源但混入 {@code rollSalt}，避免每手牌每次行动都抽到相同的第一个 {@code nextDouble()}。
     */
    public static Random buildHandRandomForTableTalk(DpRoomBO room, DpPlayer bot, long rollSalt) {
        if (!NPC_HAND_SEED_FOR_DECISIONS) {
            return new Random(rollSalt ^ System.nanoTime());
        }
        long seed = room != null ? room.getCurrentHandSeed() : System.currentTimeMillis();
        int seatIndex = -1;
        if (room != null && room.getPlayers() != null) {
            seatIndex = room.getPlayers().indexOf(bot);
        }
        if (seatIndex < 0) {
            seatIndex = 0;
        }
        seed ^= (long) (31 * seatIndex + 17);
        seed ^= 0x54414C4B5F4E5043L; // "TALK_NPC" — 与决策 RNG 区分
        seed ^= rollSalt;
        if (room != null) {
            seed ^= room.getLastActionTime();
            seed ^= (long) room.getCurrentActorIndex() * 997L;
        }
        return new Random(seed);
    }

    /**
     * 规则 / CUSTOM NPC 桌边台词风格：Legacy 与 {@link #getBotTypeByNickname} 映射；CUSTOM 六维最近邻预设。
     */
    public static NpcStyle resolveNpcStyleForTableTalk(DpPlayer bot) {
        if (bot == null) {
            return null;
        }
        String nick = bot.getNickname();
        if (isCustomBotNickname(nick)) {
            return nearestNpcStyleForCustomSixAxis(bot);
        }
        BotType type = getBotTypeByNickname(nick);
        return type == null ? null : npcStyleForBotType(type);
    }

    private static NpcStyle nearestNpcStyleForCustomSixAxis(DpPlayer bot) {
        double vpip = bot.getNpcStyleVpip();
        double pfr = bot.getNpcStylePfr();
        double cbet = bot.getNpcStyleCbetFreq();
        double bluff = bot.getNpcStyleBluffFreq();
        double callStation = bot.getNpcStyleCallStation();
        double foldPressure = bot.getNpcStyleFoldToPressure();
        if (vpip < 0 && pfr < 0) {
            return NpcStyle.TAG;
        }
        NpcStyle best = NpcStyle.TAG;
        double bestDist = Double.MAX_VALUE;
        for (NpcStyle style : NpcStyle.values()) {
            StyleProfile preset = STYLE_PROFILE_MAP.get(style);
            if (preset == null) {
                continue;
            }
            double d = sqAxis(vpip - preset.vpip)
                    + sqAxis(pfr - preset.pfr)
                    + sqAxis(cbet - preset.cbetFreq)
                    + sqAxis(bluff - preset.bluffFreq)
                    + sqAxis(callStation - preset.callStation)
                    + sqAxis(foldPressure - preset.foldToPressure);
            if (d < bestDist) {
                bestDist = d;
                best = style;
            }
        }
        return best;
    }

    private static double sqAxis(double x) {
        return x * x;
    }

    private static int countActiveVillains(DpRoomBO room, DpPlayer hero) {
        if (room == null || hero == null || room.getPlayers() == null) {
            return 0;
        }
        int count = 0;
        for (DpPlayer p : room.getPlayers()) {
            if (p == null)
                continue;
            if (p == hero)
                continue;
            if (p.isFold() || p.isLeftThisHand())
                continue;
            count++;
        }
        return count;
    }

    /**
     * 为当前 hero 构建统一的“聪明决策上下文”。
     * 不改变任何底层工具函数的行为，只做一次集中调用与打包。
     */
    public static DpUtilSmartContext buildSmartContext(
            DpRoomBO room,
            DpPlayer hero,
            SimpleStrength strength,
            String stage,
            int callAmount,
            Random random) {
        if (room == null || hero == null) {
            StackContext emptyStacks = analyzeStacks(room, hero);
            return new DpUtilSmartContext(
                    null,
                    null,
                    VillainRangeTier.BALANCED,
                    ActionCredibility.MEDIUM,
                    0.0,
                    0.0,
                    0.25,
                    emptyStacks,
                    0,
                    Collections.emptyList(),
                    0,
                    0,
                    0,
                    CounterStrategyProfile.NEUTRAL);
        }

        // 1. 找当前主要 aggressor（最大 bet 且不是 hero，未弃牌未离桌）
        DpPlayer aggressor = null;
        Map<String, DpPlayerStats> statsMap = room.getPlayerStatsMap();
        if (room.getPlayers() != null) {
            int maxBet = 0;
            for (DpPlayer p : room.getPlayers()) {
                if (p == null)
                    continue;
                if (p == hero)
                    continue;
                if (p.isFold() || p.isLeftThisHand())
                    continue;
                if (p.getBet() > maxBet) {
                    maxBet = p.getBet();
                    aggressor = p;
                }
            }
        }

        // 2. 从 statsMap 取该玩家的 PlayerStats（可为 null）
        DpPlayerStats aggressorStats = null;
        if (aggressor != null && statsMap != null) {
            aggressorStats = statsMap.get(aggressor.getNickname());
        }

        // 3. 估计对手整体牌范围风格
        VillainRangeTier villainTier = estimateVillainRangeTier(aggressorStats);

        // 4. 当前动作可信度
        ActionCredibility credibility = estimateActionCredibility(room, hero, aggressor);

        // 5. 摊牌 bluff 倾向
        double showdownBluffiness = estimateShowdownBluffiness(aggressorStats);

        // 6. 底池赔率（仅在有跟注成本时）
        double potOdds = 0.0;
        if (callAmount > 0) {
            potOdds = computePotOdds(room, hero, callAmount);
        }

        // 7. 粗略赢率估计桶（翻后在可评估 5+ 张牌时用真实成牌类型校正）
        List<String> hole = hero.getHoleCards();
        List<String> community = room.getCommunityCards();
        HandStrength hsForEquity = null;
        if (hole != null && community != null && !"preflop".equals(stage)) {
            List<String> allForEq = new ArrayList<>(hole);
            allForEq.addAll(community);
            if (allForEq.size() >= 5) {
                hsForEquity = evaluateBestHand(allForEq);
            }
        }
        double equityEst = estimateEquityBucket(strength, stage, hole, community, hsForEquity);

        // 8. 筹码深度上下文
        StackContext stackCtx = analyzeStacks(room, hero);

        // 9. 还在牌局中的其他玩家数量
        int activeVillains = countActiveVillains(room, hero);

        // 10. 统计 multi-way 相关信息：后位紧玩家、短码/深码玩家等
        List<MultiwayVillainInfo> multiway = new ArrayList<>();
        int tightBehind = 0;
        int deepBehind = 0;
        int shortBehind = 0;
        int bb = room.getBigBlindChips();
        List<DpPlayer> players = room.getPlayers();
        int heroIndex = players != null ? players.indexOf(hero) : -1;
        if (players != null && heroIndex >= 0) {
            int size = players.size();
            for (int i = 0; i < size; i++) {
                DpPlayer p = players.get(i);
                if (p == null || p == hero || p.isFold() || p.isLeftThisHand()) {
                    continue;
                }
                boolean behind = ((i - heroIndex + size) % size) > 0;
                DpPlayerStats ps = statsMap != null ? statsMap.get(p.getNickname()) : null;
                VillainRangeTier tier = estimateVillainRangeTier(ps);
                double stackBB = bb > 0 ? (p.getChips() * 1.0 / bb) : 0.0;
                boolean raisedThisStreet = (aggressor == p);
                multiway.add(new MultiwayVillainInfo(p.getNickname(), behind, stackBB, tier, raisedThisStreet));
                if (behind) {
                    if (tier == VillainRangeTier.NIT || tier == VillainRangeTier.TIGHT) {
                        tightBehind++;
                    }
                    if (stackBB > NpcRuleCoeffs.DEFAULT.deepStackMinBB) {
                        deepBehind++;
                    }
                    if (stackBB > 0 && stackBB <= NpcRuleCoeffs.DEFAULT.shortStackBB) {
                        shortBehind++;
                    }
                }
            }
        }

        // 10. 针对主要对手生成 counter-strategy 建议
        CounterStrategyProfile counter = analyzeVillainCounterStrategy(aggressorStats);

        // 11. 封装返回
        return new DpUtilSmartContext(
                aggressor,
                aggressorStats,
                villainTier,
                credibility,
                showdownBluffiness,
                potOdds,
                equityEst,
                stackCtx,
                activeVillains,
                multiway,
                tightBehind,
                deepBehind,
                shortBehind,
                counter);
    }

    // 已学习，这里是把smartContext的情报转成llm的情报
    /**
     * 仅给 {@code BOT_LLM} 使用：打包当前局面摘要，供大模型阅读。
     * 不进入 {@link #decideBotAction}，与普通规则 NPC 分离。
     */
    public static LlmNpcGameContext buildLlmNpcGameSnapshot(DpRoomBO room, DpPlayer bot) {
        if (room == null || bot == null) {
            return null;
        }
        String stage = room.getCurrentStage() != null ? room.getCurrentStage() : "";
        int callAmount = Math.max(0, room.getCurrentBetToCall() - bot.getBet());
        SimpleStrength strength = estimateCurrentStrength(room, bot);
        TablePosition position = getTablePosition(room, bot);
        Random random = buildHandRandom(room, bot);
        DpUtilSmartContext ctx = buildSmartContext(room, bot, strength, stage, callAmount, random);
        return LlmNpcGameContext.map(room, bot, ctx, stage, callAmount, strength, position);
    }

    private static BotAction decideCustomBotAction(DpRoomBO room, DpPlayer bot) {
        if (!bot.isNpcCustomStyleReady()) {
            log.error("CUSTOM bot {} seated without style profile", bot.getNickname());
            return null;
        }
        StyleProfile style = styleProfileFromValues(
                bot.getNpcStyleVpip(),
                bot.getNpcStylePfr(),
                bot.getNpcStyleCbetFreq(),
                bot.getNpcStyleBluffFreq(),
                bot.getNpcStyleCallStation(),
                bot.getNpcStyleFoldToPressure());
        int chips = bot.getChips();
        if (chips <= 0) {
            return new BotAction(BotActionType.FOLD, 0);
        }
        int callAmount = Math.max(0, room.getCurrentBetToCall() - bot.getBet());
        double callRatio = chips == 0 || callAmount >= chips ? 1.0 : (callAmount * 1.0 / chips);
        TablePosition position = getTablePosition(room, bot);
        String stageForNpc = room.getCurrentStage() != null ? room.getCurrentStage() : "";
        Random random = buildHandRandom(room, bot);
        BoardDanger boardDanger = evaluateBoardDanger(room.getCommunityCards());
        double mood = NPC_MOOD_ENABLED ? bot.getMood() : 0.0;
        if ("preflop".equals(stageForNpc)) {
            BotAction preUnified = DpNpcUnifiedPreflopStrategy.decide(
                    room,
                    bot,
                    callAmount,
                    callRatio,
                    style.vpip,
                    style.pfr,
                    style.callStation,
                    style.foldToPressure,
                    mood,
                    random,
                    null);
            if (preUnified != null) {
                return preUnified;
            }
            return new BotAction(BotActionType.CALL_OR_CHECK, 0);
        }
        SimpleStrength strength = estimateCurrentStrength(room, bot);
        DpNpcRuleDecisionParams ruleParams = new DpNpcRuleDecisionParams(
                room,
                bot,
                null,
                chips,
                callAmount,
                callRatio,
                position,
                stageForNpc,
                random,
                boardDanger,
                mood,
                strength,
                style.preflopTightness(),
                style.aggression(),
                style.bluffFrequency(),
                style.callStation,
                style.stealBlindFrequency(),
                style.checkRaiseFear());
        return DpNpcCustomStrategy.decide(ruleParams);
    }

    private static BotAction decideBotAction(DpRoomBO room, DpPlayer bot, BotType type) {
        int chips = bot.getChips();
        if (chips <= 0) {
            return new BotAction(BotActionType.FOLD, 0);
        }
        /// 这里是获取跟注额，位置信息，牌力信息的模块
        int callAmount = Math.max(0, room.getCurrentBetToCall() - bot.getBet());
        double callRatio = chips == 0 || callAmount >= chips ? 1.0 : (callAmount * 1.0 / chips);// 把要跟的大于自己的部分算作1，因为超出去部分没意义，要跟的已经占百分百了
        TablePosition position = getTablePosition(room, bot);// 看位置方法
        String stageForNpc = room.getCurrentStage() != null ? room.getCurrentStage() : "";
        Random random = buildHandRandom(room, bot);
        // log.info("random: {}", random);
        /// 这里判断牌面危险度、风格配置（翻前范围、凶度、偷盲率、诈唬频率等）；mood 见 {@link #NPC_MOOD_ENABLED}
        BoardDanger boardDanger = evaluateBoardDanger(room.getCommunityCards());
        double mood = NPC_MOOD_ENABLED ? bot.getMood() : 0.0;
        // 统一根据 BotType 拿到风格配置，后续各 case 里不再写死“紧/松、凶/弱”等魔法数字。
        StyleProfile style = STYLE_PROFILE_MAP.get(npcStyleForBotType(type));
        if (style == null) {// 如果没有对应风格默认紧凶
            style = STYLE_PROFILE_MAP.get(NpcStyle.TAG);
        }
        // 翻前仅 {@link DpNpcUnifiedPreflopStrategy}（G1–G8 + rangeLevel）；不再由各 BotType 策略单独处理翻前。
        if ("preflop".equals(stageForNpc)) {
            BotAction preUnified = DpNpcUnifiedPreflopStrategy.decide(
                    room,
                    bot,
                    callAmount,
                    callRatio,
                    style.vpip,
                    style.pfr,
                    style.callStation,
                    style.foldToPressure,
                    mood,
                    random,
                    type);
            if (preUnified != null) {
                return preUnified;
            }
            return new BotAction(BotActionType.CALL_OR_CHECK, 0);
        }
        SimpleStrength strength = estimateCurrentStrength(room, bot);
        double preflopTight = style.preflopTightness();
        double aggression = style.aggression();
        double bluffFrequency = style.bluffFrequency();
        double callStation = style.callStation;
        double stealBlindFrequency = style.stealBlindFrequency();
        double checkRaiseFear = style.checkRaiseFear();
        /// 整合好上面全部参数，进入具体类型的决策
        DpNpcRuleDecisionParams ruleParams = new DpNpcRuleDecisionParams(
                room,
                bot,
                type,
                chips,
                callAmount,
                callRatio,
                position,
                stageForNpc,
                random,
                boardDanger,
                mood,
                strength,
                preflopTight,
                aggression,
                bluffFrequency,
                callStation,
                stealBlindFrequency,
                checkRaiseFear);
        switch (type) {
            case FISH:
                return DpNpcFishStrategy.decide(ruleParams);
            case CALL:
                return DpNpcCallStrategy.decide(ruleParams);
            case LAG:
                return DpNpcLagStrategy.decide(ruleParams);
            case MANIAC:
                return DpNpcManiacStrategy.decide(ruleParams);
            case TAG:
                return DpNpcTagStrategy.decide(ruleParams);
            case NIT:
                return DpNpcNitStrategy.decide(ruleParams);
            default:
                return new BotAction(BotActionType.CALL_OR_CHECK, 0);
        }
    }
}
