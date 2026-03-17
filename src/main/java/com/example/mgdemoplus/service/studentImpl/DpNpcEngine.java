package com.example.mgdemoplus.service.studentImpl;

import com.example.mgdemoplus.entity.DpPlayer;
import com.example.mgdemoplus.entity.DpRoom;
import com.example.mgdemoplus.entity.PlayerStats;

import java.util.*;

import static com.example.mgdemoplus.service.studentImpl.DpHandEvaluator.CARD_RANK_MAP;
import static com.example.mgdemoplus.service.studentImpl.DpHandEvaluator.SimpleStrength;
import static com.example.mgdemoplus.service.studentImpl.DpHandEvaluator.getRankFromCard;
import static com.example.mgdemoplus.service.studentImpl.DpHandEvaluator.toSimpleStrength;
import static com.example.mgdemoplus.service.studentImpl.DpHandEvaluator.evaluateBestHand;

/**
 * 德扑 NPC 行为模块。
 * 负责：
 * - 判断某个玩家是否为机器人
 * - 不同昵称映射到不同机器人类型
 * - 依据牌力 / 牌面 / 位置 / 情绪等为机器人做下注决策
 * - 模拟机器人“思考时间”，按节奏在定时任务中出手
 */
public final class DpNpcEngine {

    private DpNpcEngine() {
    }

    // ===== Shark 行为关键阈值与可调参数，集中在配置类中便于调参 =====
    private static final class SharkConfig {
        /** 典型短码阈值：≤ 20BB 视为 short stack 病人 */
        private static final double SHORT_STACK_BB = 20.0;
        /** 深码单人或者整体深码的最小 BB 阈值 */
        private static final double DEEP_STACK_MIN_BB = 60.0;
        /** 深码桌平均筹码阈值：> 80BB 视为 deep stack table */
        private static final double DEEP_TABLE_AVG_BB = 80.0;
        /** 赢率 vs 赔率的“无差异区间”下边缘（防抖动） */
        private static final double EQUITY_POTODDS_EPS = 0.05;
        /** 赢率 vs 赔率的“强烈划算”边缘（加大跟注/抗压意愿） */
        private static final double EQUITY_POTODDS_MARGIN = 0.10;
        /** 估计赢率略逊于赔率时，对基础弃牌率的附加修正 */
        private static final double EQUITY_FOLD_BOOST = 0.15;
        /** 估计赢率显著好于赔率时，对基础弃牌率的衰减系数 */
        private static final double EQUITY_FOLD_SHRINK = 0.8;
        /** multi-way pot 额外弃牌加成 */
        private static final double MULTIWAY_FOLD_BOOST = 0.15;
        /** 每个概率应用软噪声的最大扰动幅度 */
        private static final double PROB_NOISE_DELTA = 0.05;
        /** 河牌极强牌 overbet 概率 */
        private static final double RIVER_OVERBET_PROB = 0.20;
        /** 河牌中等牌 block bet 概率 */
        private static final double RIVER_BLOCK_PROB = 0.35;
    }

    // 显示昵称：后端/前端都以这些字符串识别不同 NPC
    public static final String DEMO_BOT_NICKNAME = "BOT_Fish";   // 原 BOT_Demo → 现在命名为 BOT_Fish（鱼式简单玩家）
    public static final String MANIAC_BOT_NICKNAME = "BOT_Maniac";
    public static final String SHARK_BOT_NICKNAME = "BOT_Shark";
    public static final String TAG_BOT_NICKNAME   = "BOT_Tag";   // 新增：紧凶型 TAG（Tight-Aggressive），困难难度

    /**
     * 机器人类型（现有昵称入口），后续只用来映射「风格 + 难度」。
     * 真正的决策逻辑不按类型分叉，只按参数表工作。
     */
    private enum BotType {
        DEMO,
        MANIAC,
        SHARK,
        TAG   // 紧凶型
    }

    /**
     * NPC 风格：紧凶 / 松凶 / 紧弱 / 松散娱乐。
     * 所有风格通过参数差异体现，而不是多套逻辑。
     */
    private enum NpcStyle {
        TIGHT_AGGRO,    // 紧凶型：紧、凶、会诈唬、读牌准
        LOOSE_AGGRO,    // 松凶型：入池宽、极凶、高诈唬、爱偷盲
        TIGHT_PASSIVE,  // 紧弱型：只玩好牌、被加注就弃、不诈唬
        LOOSE_FUN       // 松散娱乐型：乱入池、爱跟注、易被诈
    }

    /**
     * 难度等级：通过“削弱系数 + 随机失误”来控制强弱。
     */
    private enum NpcDifficulty {
        EASY,
        MEDIUM,
        HARD,
        PRO   // 专业档：关闭所有“失误型噪声”，尽量发挥策略上限
    }

    /**
     * NPC 决策动作类型。
     */
    public enum BotActionType {
        FOLD, CALL_OR_CHECK, RAISE, ALL_IN
    }

    /**
     * NPC 决策结果：动作 + 金额。
     */
    public static class BotAction {
        private final BotActionType type;
        private final int amount;

        BotAction(BotActionType type, int amount) {
            this.type = type;
            this.amount = amount;
        }

        public BotActionType getType() {
            return type;
        }

        public int getAmount() {
            return amount;
        }
    }

    /**
     * 公共牌干湿度：用于控制“怕被听牌 / 抓诈唬”的程度。
     */
    private enum BoardDanger {
        DRY,
        WET
    }

    /**
     * 座位位置意识：早位紧，晚位凶，盲注位有独立策略。
     */
    private enum TablePosition {
        EARLY,
        MIDDLE,
        LATE,
        BLINDS
    }

    /**
     * 对手整体牌范围强度（不是具体牌）。
     */
    enum VillainRangeTier {
        NIT,        // 极紧
        TIGHT,      // 稍紧
        BALANCED,   // 正常
        LOOSE,      // 偏松
        MANIAC      // 疯狗
    }

    /**
     * 当前一轮动作的可信度：是价值下注还是诈唬的概率感知。
     */
    enum ActionCredibility {
        HIGH,
        MEDIUM,
        LOW
    }

    /**
     * 风格参数表：控制范围/激进度/诈唬频率等。
     */
    private static class StyleProfile {
        final double preflopTightness;
        final double aggression;
        final double bluffFrequency;
        final double callStation;
        final double stealBlindFrequency;
        final double checkRaiseFear;

        StyleProfile(double preflopTightness,
                     double aggression,
                     double bluffFrequency,
                     double callStation,
                     double stealBlindFrequency,
                     double checkRaiseFear) {
            this.preflopTightness = preflopTightness;
            this.aggression = aggression;
            this.bluffFrequency = bluffFrequency;
            this.callStation = callStation;
            this.stealBlindFrequency = stealBlindFrequency;
            this.checkRaiseFear = checkRaiseFear;
        }
    }

    /**
     * 难度削弱参数：控制“看错牌 / 算错赔率 / 偶尔乱来”。
     */
    private static class DifficultyProfile {
        final double strengthNoise;
        final double potOddsNoise;
        final double ignorePositionProb;
        final double ignorePotOddsProb;
        final double missBluffCatchProb;
        final double randomSpewProb;

        DifficultyProfile(double strengthNoise,
                          double potOddsNoise,
                          double ignorePositionProb,
                          double ignorePotOddsProb,
                          double missBluffCatchProb,
                          double randomSpewProb) {
            this.strengthNoise = strengthNoise;
            this.potOddsNoise = potOddsNoise;
            this.ignorePositionProb = ignorePositionProb;
            this.ignorePotOddsProb = ignorePotOddsProb;
            this.missBluffCatchProb = missBluffCatchProb;
            this.randomSpewProb = randomSpewProb;
        }
    }

    private static final Map<NpcStyle, StyleProfile> STYLE_PROFILE_MAP = new EnumMap<>(NpcStyle.class);
    private static final Map<NpcDifficulty, DifficultyProfile> DIFFICULTY_PROFILE_MAP = new EnumMap<>(NpcDifficulty.class);

    static {
        // 4 种风格参数配置
        STYLE_PROFILE_MAP.put(NpcStyle.TIGHT_AGGRO, new StyleProfile(
                0.85,  // preflopTightness
                0.80,  // aggression
                0.35,  // bluffFrequency
                0.20,  // callStation
                0.60,  // stealBlindFrequency
                0.70   // checkRaiseFear
        ));
        STYLE_PROFILE_MAP.put(NpcStyle.LOOSE_AGGRO, new StyleProfile(
                0.30,
                0.95,
                0.60,
                0.35,
                0.90,
                0.25
        ));
        STYLE_PROFILE_MAP.put(NpcStyle.TIGHT_PASSIVE, new StyleProfile(
                0.90,
                0.20,
                0.05,
                0.35,
                0.15,
                0.90
        ));
        STYLE_PROFILE_MAP.put(NpcStyle.LOOSE_FUN, new StyleProfile(
                0.25,
                0.30,
                0.10,
                0.80,
                0.40,
                0.40
        ));

        // 3 级难度削弱规则
        DIFFICULTY_PROFILE_MAP.put(NpcDifficulty.EASY, new DifficultyProfile(
                0.35,
                0.40,
                0.90,
                0.90,
                0.90,
                0.25
        ));
        DIFFICULTY_PROFILE_MAP.put(NpcDifficulty.MEDIUM, new DifficultyProfile(
                0.15,
                0.15,
                0.30,
                0.35,
                0.40,
                0.08
        ));
        DIFFICULTY_PROFILE_MAP.put(NpcDifficulty.HARD, new DifficultyProfile(
                0.05,
                0.05,
                0.05,
                0.05,
                0.10,
                0.02
        ));
        // PRO 档：关闭所有“读牌/赔率/位置/乱来”等削弱噪声，让策略尽量纯粹
        DIFFICULTY_PROFILE_MAP.put(NpcDifficulty.PRO, new DifficultyProfile(
                0.0, // strengthNoise：不再主动“看错牌力”
                0.0, // potOddsNoise：不对底池赔率加入误差
                0.0, // ignorePositionProb：始终考虑位置
                0.0, // ignorePotOddsProb：始终考虑赔率
                0.0, // missBluffCatchProb：不再额外制造“错过抓诈唬”的弱化
                0.0  // randomSpewProb：不再触发“完全乱来一把”的行为
        ));
    }

    public static boolean isBotPlayer(DpPlayer p) {
        if (p == null) return false;
        String name = p.getNickname();
        return DEMO_BOT_NICKNAME.equals(name)
                || MANIAC_BOT_NICKNAME.equals(name)
                || SHARK_BOT_NICKNAME.equals(name)
                || TAG_BOT_NICKNAME.equals(name);
    }

    public static BotType getBotTypeByNickname(String nickname) {
        if (DEMO_BOT_NICKNAME.equals(nickname)) {
            return BotType.DEMO;
        }
        if (MANIAC_BOT_NICKNAME.equals(nickname)) {
            return BotType.MANIAC;
        }
        if (SHARK_BOT_NICKNAME.equals(nickname)) {
            return BotType.SHARK;
        }
        if (TAG_BOT_NICKNAME.equals(nickname)) {
            return BotType.TAG;
        }
        return null;
    }

    /**
     * 通过 BotType 映射到一个抽象的风格枚举。
     * 后续大部分“紧/松 + 凶/弱”行为都应尽量只依赖 NpcStyle，而不是在各个 case 里写死。
     */
    private static NpcStyle getStyleByBotType(BotType type) {
        if (type == null) {
            return NpcStyle.TIGHT_AGGRO;
        }
        switch (type) {
            case DEMO:
                // Fish 型：范围偏松、整体偏娱乐/被动
                return NpcStyle.LOOSE_FUN;
            case MANIAC:
                // 疯子：松、凶、爱加注和 all-in
                return NpcStyle.LOOSE_AGGRO;
            case TAG:
                // 紧凶常客
                return NpcStyle.TIGHT_AGGRO;
            case SHARK:
            default:
                // 目前 Shark 也先视为紧凶，后续有需要可以为其单独增加一个风格枚举
                return NpcStyle.TIGHT_AGGRO;
        }
    }

    /**
     * 将粗粒度牌力 + 当前阶段映射为一个 0~1 的“赢率估计桶”。
     * 这里只做非常粗略的分档，目的是让 SHARK 在有跟注成本时能先对比「赢率 vs 底池赔率」再决定是否弃牌。
     */
    private static double estimateEquityBucket(SimpleStrength st, String stage,
                                               List<String> hole, List<String> community) {
        if (st == null) {
            return 0.25;
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
        // 翻后在 flop / turn 结合听牌稍微微调：有强听牌时弱/中等牌适度抬高估计赢率
        if (!preflop && ("flop".equals(stage) || "turn".equals(stage))) {
            boolean strongDraw =
                    hasStrongFlushDraw(hole, community) || hasOpenEndedStraightDraw(hole, community);
            if (strongDraw) {
                if (st == SimpleStrength.MEDIUM) {
                    base = Math.max(base, 0.50);
                } else if (st == SimpleStrength.WEAK) {
                    base = Math.max(base, 0.30);
                }
            }
        }
        return base;
    }

    /**
     * 简化版：检测是否有较强的同花听牌（任一花色达到 4 张，且 hero 至少拥有其中 1 张）。
     */
    private static boolean hasStrongFlushDraw(List<String> hole, List<String> community) {
        if (hole == null || community == null) return false;
        Map<String, Integer> suitCount = new HashMap<>();
        for (String c : community) {
            if (c == null || !c.contains("_")) continue;
            String suit = c.split("_", 2)[0];
            suitCount.put(suit, suitCount.getOrDefault(suit, 0) + 1);
        }
        if (suitCount.isEmpty()) return false;
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
            if (c == null || !c.contains("_")) continue;
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
        if (hole != null) all.addAll(hole);
        if (community != null) all.addAll(community);
        if (all.size() < 4) return false;
        Set<Integer> ranks = new HashSet<>();
        for (String c : all) {
            int r = getRankFromCard(c);
            if (r > 0) {
                ranks.add(r);
            }
        }
        if (ranks.size() < 4) return false;
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

    private static SimpleStrength estimateCurrentStrength(DpRoom room, DpPlayer bot) {
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
        DpHandEvaluator.HandStrength hs = evaluateBestHand(all);
        return toSimpleStrength(hs, room.getCurrentStage(), hole, community);
    }

    /**
     * 根据难度在牌力上增加适度噪声，用于 EASY/MEDIUM 弱化“读牌准确度”。
     */
    private static SimpleStrength applyStrengthNoise(SimpleStrength raw, NpcDifficulty difficulty, Random random) {
        DifficultyProfile profile = DIFFICULTY_PROFILE_MAP.get(difficulty);
        if (profile == null || profile.strengthNoise <= 0) {
            return raw;
        }
        if (random.nextDouble() > profile.strengthNoise) {
            return raw;
        }
        switch (raw) {
            case MONSTER:
                return random.nextBoolean() ? SimpleStrength.STRONG : SimpleStrength.MONSTER;
            case STRONG:
                return random.nextBoolean() ? SimpleStrength.MEDIUM : SimpleStrength.MONSTER;
            case MEDIUM:
                return random.nextBoolean() ? SimpleStrength.WEAK : SimpleStrength.STRONG;
            case WEAK:
            default:
                return random.nextBoolean() ? SimpleStrength.MEDIUM : SimpleStrength.WEAK;
        }
    }

    private static BoardDanger evaluateBoardDanger(List<String> communityCards) {
        if (communityCards == null || communityCards.size() < 3) {
            return BoardDanger.DRY;
        }
        Set<Integer> ranks = new HashSet<>();
        Map<String, Integer> suitCount = new HashMap<>();
        for (String c : communityCards) {
            if (c == null || !c.contains("_")) continue;
            String[] parts = c.split("_", 2);
            if (parts.length != 2) continue;
            String suit = parts[0];
            String rankStr = parts[1];
            int r = CARD_RANK_MAP.getOrDefault(rankStr, 0);
            if (r > 0) {
                ranks.add(r);
            }
            suitCount.put(suit, suitCount.getOrDefault(suit, 0) + 1);
        }
        // 同花危险：某一花色数量 >= 3
        for (Integer cnt : suitCount.values()) {
            if (cnt >= 3) {
                return BoardDanger.WET;
            }
        }
        // 顺子危险：有 3 张以上接近的连张结构
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
            if (maxRun >= 3) {
                return BoardDanger.WET;
            }
        }
        return BoardDanger.DRY;
    }

    /**
     * 计算机器人在牌桌上的大致位置：早位 / 中位 / 晚位 / 盲注。
     */
    private static TablePosition getTablePosition(DpRoom room, DpPlayer bot) {
        List<DpPlayer> ps = room.getPlayers();
        if (ps == null || ps.isEmpty()) return TablePosition.MIDDLE;
        int idx = ps.indexOf(bot);
        if (idx < 0) return TablePosition.MIDDLE;

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
        if (dealerIdx < 0) return TablePosition.MIDDLE;

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
    private static VillainRangeTier estimateVillainRangeTier(PlayerStats stats) {
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
     * 读当前加注动作的可信度：是价值下注还是偏诈唬。
     */
    private static ActionCredibility estimateActionCredibility(DpRoom room, DpPlayer bot, DpPlayer aggressor) {
        if (aggressor == null || aggressor == bot) {
            return ActionCredibility.MEDIUM;
        }
        Map<String, PlayerStats> statsMap = room.getPlayerStatsMap();
        PlayerStats stats = statsMap != null ? statsMap.get(aggressor.getNickname()) : null;
        VillainRangeTier tier = estimateVillainRangeTier(stats);

        if (tier == VillainRangeTier.NIT || tier == VillainRangeTier.TIGHT) {
            return ActionCredibility.HIGH;
        }
        if (tier == VillainRangeTier.MANIAC || tier == VillainRangeTier.LOOSE) {
            return ActionCredibility.LOW;
        }
        if (stats != null && !stats.getRecentHands().isEmpty()) {
            PlayerStats.SingleHandStats last = stats.getRecentHands().peekLast();
            if (last != null && last.isRaised() && !last.isWentToShowdown()) {
                return ActionCredibility.LOW;
            }
        }
        return ActionCredibility.MEDIUM;
    }

    /**
     * 底池赔率：call / (pot + call)，并根据难度加入轻微噪声。
     * SHARK 在决定弃牌/跟注时会参考此值（赔率差时更易弃牌，赔率好时略减弃牌率）。
     */
    private static double computePotOdds(DpRoom room, DpPlayer bot, int callAmount, Random random, NpcDifficulty difficulty) {
        int pot = room.getPot();
        int denom = pot + callAmount;
        if (callAmount <= 0 || denom <= 0) {
            return 0.0;
        }
        double base = callAmount * 1.0 / denom;
        DifficultyProfile profile = DIFFICULTY_PROFILE_MAP.get(difficulty);
        if (profile == null || profile.potOddsNoise <= 0) {
            return base;
        }
        double noise = (random.nextDouble() * 2 - 1) * profile.potOddsNoise;
        double v = base + noise;
        if (v < 0) v = 0;
        if (v > 1) v = 1;
        return v;
    }

    /**
     * 本手牌场上其他未弃牌玩家的筹码分布（后手 stack 视角）。
     * 用于区分短码（short stack）和深码（deep stack），指导 SHARK 调整下注压力。
     */
    static class StackContext {
        final int minStack;
        final int maxStack;
        final int avgStack;
        final double minStackBB;
        final double maxStackBB;
        final double avgStackBB;

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
    private static StackContext analyzeStacks(DpRoom room, DpPlayer hero) {
        if (room == null || hero == null) {
            return new StackContext(0, 0, 0, DpRoom.getBBChips());
        }
        int bb = DpRoom.getBBChips();
        int min = Integer.MAX_VALUE;
        int max = 0;
        int sum = 0;
        int count = 0;
        List<DpPlayer> players = room.getPlayers();
        if (players != null) {
            for (DpPlayer p : players) {
                if (p == null) continue;
                if (p.isFold() || p.isLeftThisHand()) continue;
                if (p == hero) continue;
                int stack = p.getChips();
                if (stack <= 0) continue;
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
    private static double computeHeroVsVillainStackRatio(DpPlayer hero, DpPlayer villain) {
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
    private static double estimateShowdownBluffiness(PlayerStats stats) {
        if (stats == null || stats.getRecentHands().isEmpty()) {
            return 0.0;
        }
        int sample = 0;
        int weakOrMediumShowdown = 0;
        // 只看最近 N 手（例如 10 手）即可
        int limit = 10;
        int processed = 0;
        for (PlayerStats.SingleHandStats hand : stats.getRecentHands()) {
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
        if (v < 0) v = 0;
        if (v > 1) v = 1;
        return v;
    }

    /**
     * 在定时任务中调用：如果轮到机器人行动，则根据思考时间和当前局面给出一个 BotAction。
     * 返回 null 表示暂时不需要行动（例如正在“思考中”或不该由该机器人行动）。
     */
    public static BotAction decideActionIfReady(DpRoom room, DpPlayer bot) {
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

        // 先根据当前局面为机器人设置一次“思考时间”，到点前不真正出手
        long now = System.currentTimeMillis();
        long nextTime = bot.getNextBotActionTime();
        if (nextTime <= 0L) {
            long delayMs = calculateBotThinkDelay(room, bot);
            bot.setNextBotActionTime(now + delayMs);
            return null;
        }
        if (now < nextTime) {
            // 还在思考时间窗口内，暂不行动
            return null;
        }
        // 本轮行动完成后清零，下次成为行动者时重新计算
        bot.setNextBotActionTime(0L);

        BotType type = getBotTypeByNickname(bot.getNickname());
        if (type == null) {
            return null;
        }
//决策逻辑
        return decideBotAction(room, bot, type);
    }

    /**
     * 机器人“思考时间”配置。
     */
    private static class BotThinkProfile {
        final long weakMinMs;
        final long weakMaxMs;
        final long mediumMinMs;
        final long mediumMaxMs;
        final long strongMinMs;
        final long strongMaxMs;

        final long cheapMinMs;
        final long cheapMaxMs;

        final double cheapSnapProb;
        final double globalSnapProb;
        final long maxCapMs;

        BotThinkProfile(long weakMinMs, long weakMaxMs,
                        long mediumMinMs, long mediumMaxMs,
                        long strongMinMs, long strongMaxMs,
                        long cheapMinMs, long cheapMaxMs,
                        double cheapSnapProb,
                        double globalSnapProb,
                        long maxCapMs) {
            this.weakMinMs = weakMinMs;
            this.weakMaxMs = weakMaxMs;
            this.mediumMinMs = mediumMinMs;
            this.mediumMaxMs = mediumMaxMs;
            this.strongMinMs = strongMinMs;
            this.strongMaxMs = strongMaxMs;
            this.cheapMinMs = cheapMinMs;
            this.cheapMaxMs = cheapMaxMs;
            this.cheapSnapProb = cheapSnapProb;
            this.globalSnapProb = globalSnapProb;
            this.maxCapMs = maxCapMs;
        }
    }

    private static final BotThinkProfile THINK_DEMO = new BotThinkProfile(
            250, 1700,   // WEAK
            1200, 4200,  // MEDIUM
            2800, 7500,  // STRONG
            0, 450,      // cheap snap range
            0.85,        // cheapSnapProb
            0.03,        // globalSnapProb
            12000        // max cap
    );

    private static final BotThinkProfile THINK_MANIAC = new BotThinkProfile(
            0, 500,      // WEAK：基本秒出
            200, 900,    // MEDIUM：也很快
            500, 1800,   // STRONG
            0, 250,      // cheap snap range
            0.95,        // cheapSnapProb
            0.18,        // globalSnapProb
            6000         // max cap
    );

    private static final BotThinkProfile THINK_SHARK = new BotThinkProfile(
            200, 1600,   // WEAK
            1400, 5200,  // MEDIUM
            3200, 9000,  // STRONG
            0, 500,      // cheap snap range
            0.70,        // cheapSnapProb
            0.08,        // globalSnapProb
            12000        // max cap
    );

    private static BotThinkProfile getThinkProfile(BotType type) {
        if (type == null) return THINK_DEMO;
        switch (type) {
            case MANIAC:
                return THINK_MANIAC;
            case SHARK:
                return THINK_SHARK;
            case DEMO:
            default:
                return THINK_DEMO;
        }
    }

    private static long randomBetween(long minInclusive, long maxInclusive, Random random) {
        long min = Math.max(0L, minInclusive);
        long max = Math.max(0L, maxInclusive);
        if (max <= min) return min;
        long span = max - min + 1;
        long v = Math.floorMod(random.nextLong(), span);
        return min + v;
    }

    /**
     * 为机器人当前这一手行动计算一个“思考时间”延迟。
     */
    private static long calculateBotThinkDelay(DpRoom room, DpPlayer bot) {
        int chips = bot.getChips();
        int callAmount = Math.max(0, room.getCurrentBetToCall() - bot.getBet());
        double callRatio = chips == 0 ? 1.0 : (callAmount * 1.0 / Math.max(1, chips));

        SimpleStrength strength = estimateCurrentStrength(room, bot);
        BotType type = getBotTypeByNickname(bot.getNickname());
        double mood = bot.getMood();
        Random random = buildHandRandom(room, bot);

        BotThinkProfile profile = getThinkProfile(type);

        // 全局“秒出手”
        if (profile.globalSnapProb > 0 && random.nextDouble() < profile.globalSnapProb) {
            return randomBetween(0, Math.max(0L, profile.cheapMaxMs), random);
        }

        // cheap snap：free check 或者跟注很便宜
        boolean isCheap = (callAmount == 0) || (callRatio <= 0.2);
        if (isCheap && profile.cheapSnapProb > 0 && random.nextDouble() < profile.cheapSnapProb) {
            return randomBetween(profile.cheapMinMs, profile.cheapMaxMs, random);
        }

        long minMs;
        long maxMs;
        switch (strength) {
            case STRONG:
                minMs = profile.strongMinMs;
                maxMs = profile.strongMaxMs;
                break;
            case MEDIUM:
                minMs = profile.mediumMinMs;
                maxMs = profile.mediumMaxMs;
                break;
            default:
                minMs = profile.weakMinMs;
                maxMs = profile.weakMaxMs;
                break;
        }

        // 情绪：高兴时更快，低落时更慢
        double moodFactor = 1.0 - mood * 0.2;
        if (moodFactor < 0.8) moodFactor = 0.8;
        if (moodFactor > 1.2) moodFactor = 1.2;

        long base = randomBetween(minMs, maxMs, random);
        long result = (long) (base * moodFactor);

        if (result < 0L) result = 0L;
        long cap = profile.maxCapMs > 0 ? profile.maxCapMs : 12000L;
        if (cap > 20000L) cap = 20000L;
        if (result > cap) result = cap;
        return result;
    }

    /**
     * 统一的机器人决策函数。
     */
    private static Random buildHandRandom(DpRoom room, DpPlayer bot) {
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
     * 对概率应用一个 [-delta, +delta] 的软噪声，并裁剪在 [0,1]。
     */
    private static double applySoftNoise(double prob, double delta, Random random) {
        if (delta <= 0 || random == null) {
            if (prob < 0) return 0.0;
            if (prob > 1) return 1.0;
            return prob;
        }
        double noise = (random.nextDouble() * 2.0 - 1.0) * delta;
        double v = prob + noise;
        if (v < 0) v = 0;
        if (v > 1) v = 1;
        return v;
    }

    private static int countActiveVillains(DpRoom room, DpPlayer hero) {
        if (room == null || hero == null || room.getPlayers() == null) {
            return 0;
        }
        int count = 0;
        for (DpPlayer p : room.getPlayers()) {
            if (p == null) continue;
            if (p == hero) continue;
            if (p.isFold() || p.isLeftThisHand()) continue;
            count++;
        }
        return count;
    }

    /**
     * 为当前 hero 构建统一的“聪明决策上下文”。
     * 不改变任何底层工具函数的行为，只做一次集中调用与打包。
     */
    private static SmartContext buildSmartContext(
            DpRoom room,
            DpPlayer hero,
            SimpleStrength strength,
            String stage,
            int callAmount,
            NpcDifficulty difficulty,
            Random random
    ) {
        if (room == null || hero == null) {
            StackContext emptyStacks = analyzeStacks(room, hero);
            return new SmartContext(
                    null,
                    null,
                    VillainRangeTier.BALANCED,
                    ActionCredibility.MEDIUM,
                    0.0,
                    0.0,
                    0.25,
                    emptyStacks,
                    0
            );
        }

        // 1. 找当前主要 aggressor（最大 bet 且不是 hero，未弃牌未离桌）
        DpPlayer aggressor = null;
        Map<String, PlayerStats> statsMap = room.getPlayerStatsMap();
        if (room.getPlayers() != null) {
            int maxBet = 0;
            for (DpPlayer p : room.getPlayers()) {
                if (p == null) continue;
                if (p == hero) continue;
                if (p.isFold() || p.isLeftThisHand()) continue;
                if (p.getBet() > maxBet) {
                    maxBet = p.getBet();
                    aggressor = p;
                }
            }
        }

        // 2. 从 statsMap 取该玩家的 PlayerStats（可为 null）
        PlayerStats aggressorStats = null;
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
            potOdds = computePotOdds(room, hero, callAmount, random, difficulty);
        }

        // 7. 粗略赢率估计桶
        List<String> hole = hero.getHoleCards();
        List<String> community = room.getCommunityCards();
        double equityEst = estimateEquityBucket(strength, stage, hole, community);

        // 8. 筹码深度上下文
        StackContext stackCtx = analyzeStacks(room, hero);

        // 9. 还在牌局中的其他玩家数量
        int activeVillains = countActiveVillains(room, hero);

        // 10. 封装返回
        return new SmartContext(
                aggressor,
                aggressorStats,
                villainTier,
                credibility,
                showdownBluffiness,
                potOdds,
                equityEst,
                stackCtx,
                activeVillains
        );
    }

    private static BotAction decideBotAction(DpRoom room, DpPlayer bot, BotType type) {
        int chips = bot.getChips();
        if (chips <= 0) {
            return new BotAction(BotActionType.FOLD, 0);
        }

        int callAmount = Math.max(0, room.getCurrentBetToCall() - bot.getBet());
        double callRatio = chips == 0 ? 1.0 : (callAmount * 1.0 / chips);
        TablePosition position = getTablePosition(room, bot);
        SimpleStrength rawStrength = estimateCurrentStrength(room, bot);
        // 难度分配：
        // - FISH（DEMO）：EASY（简单）
        // - MANIAC：MEDIUM（中等）
        // - TAG：HARD（困难，紧凶风格但不做复杂“读对手”）
        // - SHARK：PRO（在当前策略逻辑下关闭难度噪声，发挥上限）
        NpcDifficulty difficulty;
        switch (type) {
            case SHARK:
                difficulty = NpcDifficulty.PRO;
                break;
            case TAG:
                difficulty = NpcDifficulty.HARD;
                break;
            case MANIAC:
                difficulty = NpcDifficulty.MEDIUM;
                break;
            case DEMO:
            default:
                difficulty = NpcDifficulty.EASY;
                break;
        }
        Random random = buildHandRandom(room, bot);
        SimpleStrength strength = applyStrengthNoise(rawStrength, difficulty, random);
        BoardDanger boardDanger = evaluateBoardDanger(room.getCommunityCards());
        double mood = bot.getMood();

        // 统一根据 BotType 拿到风格配置，后续各 case 里不再写死“紧/松、凶/弱”等魔法数字。
        StyleProfile style = STYLE_PROFILE_MAP.get(getStyleByBotType(type));
        if (style == null) {
            style = STYLE_PROFILE_MAP.get(NpcStyle.TIGHT_AGGRO);
        }
        double preflopTight = style.preflopTightness;
        double aggression = style.aggression;
        double bluffFrequency = style.bluffFrequency;
        double callStation = style.callStation;
        double stealBlindFrequency = style.stealBlindFrequency;
        double checkRaiseFear = style.checkRaiseFear;

        switch (type) {
            case DEMO: {
                String stage = room.getCurrentStage();

                // 翻前弱牌弃牌概率由 preflopTightness 驱动：0.4 ~ 0.95 之间线性变化
                if ("preflop".equals(stage) && callAmount > 0 && strength == SimpleStrength.WEAK) {
                    double baseFoldWeakPreflop = 0.4 + 0.55 * preflopTight;
                    if (callRatio > 0.5) {
                        baseFoldWeakPreflop += 0.1;
                    } else if (callRatio < 0.15) {
                        baseFoldWeakPreflop -= 0.1;
                    }
                    double foldProbPre = Math.min(1.0, Math.max(0.0, baseFoldWeakPreflop + (-mood) * 0.2));
                    foldProbPre = applySoftNoise(foldProbPre, SharkConfig.PROB_NOISE_DELTA, random);
                    if (random.nextDouble() < foldProbPre) {
                        return new BotAction(BotActionType.FOLD, 0);
                    }
                }

                // 免费看牌时（对手过牌、无需跟注）绝不弃牌，至少过牌看摊牌
                if (callAmount > 0 && !"preflop".equals(stage)) {
                    double foldBase = 0.0;
                    if (strength == SimpleStrength.WEAK && callRatio > 0.5) {
                        foldBase = 0.6;
                        if (boardDanger == BoardDanger.WET) {
                            foldBase += 0.2;
                        }
                    }
                    double foldProb = Math.min(1.0, Math.max(0.0, foldBase + (-mood) * 0.2));
                    if (foldProb > 0 && random.nextDouble() < foldProb) {
                        return new BotAction(BotActionType.FOLD, 0);
                    }
                }

                double r = random.nextDouble();

                // 翻后激进度由 aggression 调节：在原有基础上 0.7x~1.3x 拉偏，从而控制“凶不凶”
                double callOrCheckProb = 0.75 - mood * 0.1;
                if (!"preflop".equals(stage)) {
                    double baseRaiseProb = 1.0 - callOrCheckProb;
                    double raiseProb = baseRaiseProb * (0.7 + 0.6 * aggression);
                    if (raiseProb < 0.0) raiseProb = 0.0;
                    if (raiseProb > 0.9) raiseProb = 0.9;
                    callOrCheckProb = 1.0 - raiseProb;
                }
                // DEMO 的弱牌 bluff：翻后无人下注、牌力 WEAK/MEDIUM 时，用 bluffFrequency 决定是否“装作有牌”小额下注
                if (callAmount == 0
                        && !"preflop".equals(stage)
                        && (strength == SimpleStrength.WEAK || strength == SimpleStrength.MEDIUM)) {
                    double baseBluffProb = (strength == SimpleStrength.MEDIUM) ? 0.20 : 0.12;
                    double bluffProb = baseBluffProb * (0.5 + 1.0 * bluffFrequency);
                    bluffProb = applySoftNoise(
                            Math.min(0.6, Math.max(0.0, bluffProb)),
                            SharkConfig.PROB_NOISE_DELTA,
                            random
                    );
                    if (random.nextDouble() < bluffProb) {
                        int pot = room.getPot();
                        int bb = DpRoom.getBBChips();
                        int sb = DpRoom.getSBChips();
                        double factor = 0.3; // 约 1/3 pot 的 c-bet bluff
                        int target = (int) Math.round(pot * factor);
                        int minBet = bb * 2;
                        if (target < minBet) {
                            target = minBet;
                        }
                        int raiseAmount = Math.min(chips, target);
                        if (sb > 0 && raiseAmount > 0) {
                            int units = Math.max(1, Math.round(raiseAmount * 1.0f / sb));
                            raiseAmount = units * sb;
                            if (raiseAmount > chips) {
                                units = Math.max(1, chips / sb);
                                raiseAmount = units * sb;
                            }
                        }
                        if (raiseAmount > 0) {
                            return new BotAction(BotActionType.RAISE, raiseAmount);
                        }
                    }
                }
                callOrCheckProb = applySoftNoise(
                        Math.min(0.95, Math.max(0.05, callOrCheckProb)),
                        SharkConfig.PROB_NOISE_DELTA,
                        random
                );

                if (r < callOrCheckProb) {
                    return new BotAction(BotActionType.CALL_OR_CHECK, 0);
                }

                if (!"preflop".equals(stage) && chips > callAmount) {
                    int raiseBase = callAmount;
                    int extraBB;
                    if (strength == SimpleStrength.STRONG) {
                        extraBB = 2;
                    } else if (strength == SimpleStrength.MEDIUM) {
                        extraBB = 1;
                    } else {
                        extraBB = 1;
                    }
                    int minExtra = DpRoom.getBBChips() * extraBB;
                    int raiseAmount = Math.min(chips, raiseBase + minExtra);
                    return new BotAction(BotActionType.RAISE, raiseAmount);
                }

                return new BotAction(BotActionType.CALL_OR_CHECK, 0);
            }
            case MANIAC: {
                String stage = room.getCurrentStage();
                SmartContext ctx = buildSmartContext(
                        room,
                        bot,
                        strength,
                        stage,
                        callAmount,
                        difficulty,
                        random
                );

                if (callAmount >= chips) {
                    double rAllIn = random.nextDouble();
                    // 疯子在“全压 or 放弃”场景下，仍然以全压为主，但会稍微参考牌力与对手可信度
                    double allInProb = 0.9;
                    double foldProb = 0.1;
                    if (strength == SimpleStrength.WEAK) {
                        allInProb -= 0.25;
                        foldProb += 0.25;
                    }
                    // 若当前主要 aggressor 被识别为“诚实且少 bluff”的类型，则略微提高弃牌概率
                    ActionCredibility maniCred = ctx.credibility;
                    double maniShowdownBluff = ctx.showdownBluffiness;
                    if (maniCred == ActionCredibility.HIGH || maniShowdownBluff < 0.1) {
                        // 认为对手更偏价值下注时，疯子略微降低 all-in 倾向
                        allInProb -= 0.15;
                        foldProb += 0.15;
                    } else if (maniCred == ActionCredibility.LOW || maniShowdownBluff > 0.4) {
                        // 认为对手常 bluff：用更多 all-in 回击
                        allInProb += 0.1;
                        foldProb = Math.max(0.05, foldProb - 0.05);
                    }
                    if (allInProb < 0.5) allInProb = 0.5;
                    if (allInProb > 0.98) allInProb = 0.98;
                    double x = random.nextDouble();
                    if (x < foldProb) {
                        return new BotAction(BotActionType.FOLD, 0);
                    }
                    if (x < foldProb + allInProb) {
                        return new BotAction(BotActionType.ALL_IN, chips);
                    }
                    // 极少数情况下，仅跟注保留一点“犹豫”的味道
                    return new BotAction(BotActionType.CALL_OR_CHECK, 0);
                }

                double r = random.nextDouble();

                if (callAmount == 0) {
                    // 后位无人入池（简单近似：当前为 LATE 且底池仍接近起始盲注）时的偷盲：由 stealBlindFrequency 控制
                    if ("preflop".equals(stage)
                            && position == TablePosition.LATE
                            && room.getPot() <= (DpRoom.getBBChips() + DpRoom.getSBChips())) { // 视为还没人真正入池
                        double stealProb = stealBlindFrequency;
                        stealProb = applySoftNoise(
                                Math.min(0.95, Math.max(0.0, stealProb)),
                                SharkConfig.PROB_NOISE_DELTA,
                                random
                        );
                        if (random.nextDouble() < stealProb) {
                            int bbPost = DpRoom.getBBChips();
                            int sbPost = DpRoom.getSBChips();
                            int openSizeBB = 3;
                            int raiseAmount = Math.min(chips, openSizeBB * bbPost);
                            if (sbPost > 0 && raiseAmount > 0) {
                                int units = Math.max(1, Math.round(raiseAmount * 1.0f / sbPost));
                                raiseAmount = units * sbPost;
                                if (raiseAmount > chips) {
                                    units = Math.max(1, chips / sbPost);
                                    raiseAmount = units * sbPost;
                                }
                            }
                            if (raiseAmount > 0) {
                                return new BotAction(BotActionType.RAISE, raiseAmount);
                            }
                        }
                    }
                    // 翻前弱牌弃牌概率轻微由 preflopTightness 控制（但整体仍保持 Maniac 很松）
                    if ("preflop".equals(stage) && strength == SimpleStrength.WEAK) {
                        double baseFoldWeakPreflop = 0.05 + 0.25 * preflopTight;
                        if (callRatio > 0.5) {
                            baseFoldWeakPreflop += 0.05;
                        }
                        double foldProbPre = Math.min(0.4, Math.max(0.0, baseFoldWeakPreflop + (-mood) * 0.1));
                        foldProbPre = applySoftNoise(foldProbPre, SharkConfig.PROB_NOISE_DELTA, random);
                        if (random.nextDouble() < foldProbPre) {
                            return new BotAction(BotActionType.FOLD, 0);
                        }
                    }

                    double raiseProb = 0.75 + mood * 0.15;
                    // 用 aggression 统一调节 Maniac 翻前/翻后“凶猛程度”
                    double aggroFactor = 0.7 + 0.6 * aggression; // ≈0.7~1.3
                    raiseProb *= aggroFactor;
                    raiseProb = Math.max(0.5, Math.min(0.98, raiseProb));

                    if (r < raiseProb) {
                        int maxMulti;
                        int minMulti;
                        if (strength == SimpleStrength.STRONG) {
                            minMulti = 3;
                            maxMulti = 5;
                        } else if (strength == SimpleStrength.MEDIUM) {
                            minMulti = 2;
                            maxMulti = 4;
                        } else {
                            minMulti = 1;
                            maxMulti = 3;
                        }
                        int range = Math.max(1, maxMulti - minMulti + 1);
                        int multiplier = minMulti + random.nextInt(range);
                        int raiseAmount = Math.min(chips, DpRoom.getBBChips() * multiplier);
                        if (raiseAmount <= 0) {
                            return new BotAction(BotActionType.CALL_OR_CHECK, 0);
                        }
                        return new BotAction(BotActionType.RAISE, raiseAmount);
                    }
                    return new BotAction(BotActionType.CALL_OR_CHECK, 0);
                }

                double maniFoldBase = 0.03;
                if (strength == SimpleStrength.WEAK && callRatio > 0.85 && boardDanger == BoardDanger.WET) {
                    maniFoldBase = 0.2;
                }
                // 结合对手风格与历史 bluff 倾向，稍微调整疯子的弃牌基准
                ActionCredibility maniCred2 = ctx.credibility;
                double maniShowdownBluff2 = ctx.showdownBluffiness;
                if (maniCred2 == ActionCredibility.HIGH || maniShowdownBluff2 < 0.1) {
                    // 认为对手相对诚实时，疯子在极端差牌 + 巨大压力下会多一点放弃
                    maniFoldBase += 0.05;
                } else if (maniCred2 == ActionCredibility.LOW || maniShowdownBluff2 > 0.4) {
                    // 认为对手经常 bluff：在同等条件下降低弃牌意愿
                    maniFoldBase *= 0.5;
                }
                double maniFoldProb = maniFoldBase - mood * 0.2;
                // callStation 越高，越不愿意弃牌：在同样条件下降低弃牌率
                maniFoldProb *= (1.0 - 0.4 * callStation);
                if (maniFoldProb < 0.0) maniFoldProb = 0.0;
                if (maniFoldProb > 0.5) maniFoldProb = 0.5;
                // 免费看牌时不弃牌
                if (callAmount > 0 && random.nextDouble() < maniFoldProb) {
                    return new BotAction(BotActionType.FOLD, 0);
                }

                if (r < 0.25 + mood * 0.05) {
                    return new BotAction(BotActionType.CALL_OR_CHECK, 0);
                }
                if (r < 0.85 + mood * 0.05) {
                    int minMulti;
                    int maxMulti;
                    if (strength == SimpleStrength.STRONG) {
                        minMulti = 3;
                        maxMulti = 6;
                    } else if (strength == SimpleStrength.MEDIUM) {
                        // 至少确保再次加注时额外筹码不少于 3BB，避免出现多轮“只加一点点”的局面
                        minMulti = 3;
                        maxMulti = 5;
                    } else {
                        minMulti = 2;
                        maxMulti = 4;
                    }
                    int range2 = Math.max(1, maxMulti - minMulti + 1);
                    int multiplier2 = minMulti + random.nextInt(range2);
                    int extra = DpRoom.getBBChips() * multiplier2;
                    int target = callAmount + extra;
                    int raiseAmount = Math.min(chips, target);
                    if (raiseAmount <= 0) {
                        return new BotAction(BotActionType.CALL_OR_CHECK, 0);
                    }
                    // 疯子型玩家：当底池已经被抬到较高水平时，容易“失去耐心”，直接一把梭哈
                    int bb = DpRoom.getBBChips();
                    if (bb > 0 && room.getPot() > bb * 20 && random.nextDouble() < 0.4) {
                        return new BotAction(BotActionType.ALL_IN, chips);
                    }
                    return new BotAction(BotActionType.RAISE, raiseAmount);
                }
                return new BotAction(BotActionType.ALL_IN, chips);
            }
            case SHARK: {
                BoardDanger bd = boardDanger;
                SimpleStrength st = strength;
                String stage = room.getCurrentStage();
                SmartContext ctx = buildSmartContext(
                        room,
                        bot,
                        st,
                        stage,
                        callAmount,
                        difficulty,
                        random
                );
                StackContext stackCtx = ctx.stackCtx;
                int activeVillains = ctx.activeVillains;
                List<String> hole = bot.getHoleCards();
                List<String> community = room.getCommunityCards();

                double baseFold = 0.0;
                if (st == SimpleStrength.WEAK && callAmount > 0 && callRatio > 0.4) {
                    baseFold = (bd == BoardDanger.WET) ? 0.7 : 0.5;
                }

                DpPlayer aggressor = ctx.aggressor;
                PlayerStats aggressorStats = ctx.aggressorStats;
                PlayerStats.SingleHandStats lastAggressorStats = null;
                if (aggressorStats != null && !aggressorStats.getRecentHands().isEmpty()) {
                    lastAggressorStats = aggressorStats.getRecentHands().peekLast();
                }
                VillainRangeTier villainTier = ctx.villainTier;
                ActionCredibility credibility = ctx.credibility;
                double showdownBluffiness = ctx.showdownBluffiness;
                String villainName = aggressor != null ? aggressor.getNickname() : "-";

                // 仅在有跟注额时根据对手风格调整弃牌率（免费看牌时不因“石头型”而弃牌）
                if (callAmount > 0 && aggressorStats != null) {
                    double overallPart = aggressorStats.getOverallParticipationRate();
                    double overallRaise = aggressorStats.getOverallRaiseRate();

                    boolean looseAggressiveLong = overallPart > 0.5 && overallRaise > 0.3;
                    boolean tightPassiveLong = overallPart < 0.25 && overallRaise < 0.15;

                    boolean looseAggressiveRecent = lastAggressorStats != null
                            && lastAggressorStats.isParticipated() && lastAggressorStats.isRaised();
                    boolean tightPassiveRecent = lastAggressorStats != null
                            && !lastAggressorStats.isParticipated() && !lastAggressorStats.isRaised();

                    if (looseAggressiveLong || looseAggressiveRecent) {
                        baseFold *= 0.4;
                    } else if (tightPassiveLong || tightPassiveRecent) {
                        baseFold = Math.min(1.0, baseFold + 0.35);
                    }
                }

                // 整合：根据对手动作可信度 + 摊牌 bluff 倾向调整——更相信“石头”，更质疑“常拿空气摊牌”的人
                if (callAmount > 0) {
                    if (credibility == ActionCredibility.LOW) {
                        baseFold *= 0.6;
                    } else if (credibility == ActionCredibility.HIGH) {
                        baseFold = Math.min(1.0, baseFold * 1.2);
                    }
                    // showdown bluffiness：对经常拿一般牌摊牌到河牌的玩家，进一步降低弃牌率；对摊牌大多是强牌的玩家，略微提高弃牌率
                    if (showdownBluffiness > 0.4) {
                        baseFold *= 0.7;
                    } else if (showdownBluffiness < 0.1) {
                        baseFold = Math.min(1.0, baseFold * 1.2);
                    }
                }

                // 底池赔率：跟注成本相对底池越大，弱牌越倾向弃牌；赔率好时略降低弃牌率
                double potOdds = ctx.potOdds;
                if (callAmount > 0) {
                    if (potOdds > 0.5) {
                        baseFold = Math.min(1.0, baseFold * 1.15);
                    } else if (potOdds < 0.25) {
                        baseFold = Math.max(0.0, baseFold * 0.85);
                    }
                }

                // 赢率 vs 赔率：在底池赔率基础上，再用一个非常粗粒度的赢率桶做一次 sanity check
                double equityEst = ctx.equityEst;
                if (callAmount > 0) {
                    // 赢率明显逊于赔率：更倾向弃牌
                    if (equityEst + SharkConfig.EQUITY_POTODDS_EPS < potOdds) {
                        baseFold = Math.min(1.0, baseFold + SharkConfig.EQUITY_FOLD_BOOST);
                    }
                    // 赢率显著好于赔率：更乐意承担跟注 / 不轻易 fold
                    if (equityEst > potOdds + SharkConfig.EQUITY_POTODDS_MARGIN) {
                        baseFold = Math.max(0.0, baseFold * SharkConfig.EQUITY_FOLD_SHRINK);
                    }
                    // 大额跟注保护：弱牌 + 高跟注成本 + 较差赔率时，强力倾向 FOLD，避免被疯狗一把大锅带走
                    if (st == SimpleStrength.WEAK && callRatio > 0.5 && potOdds > 0.45) {
                        baseFold = Math.min(1.0, Math.max(baseFold, 0.85));
                    }
                }

                // multi-way pot：当同时对抗 3 人及以上并且有跟注成本时，整体更偏向弃牌（但会受 callStation / checkRaiseFear 影响）
                if (callAmount > 0 && activeVillains >= 3) {
                    // checkRaiseFear 越高，在多人底池里越容易被吓退
                    double boost = SharkConfig.MULTIWAY_FOLD_BOOST * (0.5 + 0.5 * checkRaiseFear);
                    // callStation 越高，越不容易在多人底池直接弃牌
                    boost *= (1.0 - 0.4 * callStation);
                    baseFold = Math.min(1.0, baseFold + boost);
                }

                double foldProbShark = Math.min(1.0, Math.max(0.0, baseFold + (-mood) * 0.1));
                foldProbShark = applySoftNoise(foldProbShark, SharkConfig.PROB_NOISE_DELTA, random);
                // 免费看牌时（对手过牌）绝不弃牌，至少过牌看摊牌
                if (callAmount > 0 && foldProbShark > 0 && random.nextDouble() < foldProbShark) {
                    System.out.println("[BOT_Shark] 决策=FOLD"
                            + " room=" + room.getRoomId()
                            + " hero=" + bot.getNickname()
                            + " stage=" + room.getCurrentStage()
                            + " pos=" + getTablePosition(room, bot)
                            + " strength=" + st
                            + " board=" + bd
                            + " chips=" + chips
                            + " call=" + callAmount
                            + " mood=" + mood
                            + " foldProb=" + String.format(java.util.Locale.ENGLISH, "%.2f", foldProbShark));
                    System.out.println("[BOT_Shark-Explain] "
                            + "阶段=" + room.getCurrentStage()
                            + " 牌力=" + st
                            + " 牌面=" + bd
                            + " 主要对手=" + villainName
                            + " 对手风格=" + villainTier
                            + " 动作可信度=" + credibility
                            + " showdownBluffiness=" + String.format(java.util.Locale.ENGLISH, "%.2f", showdownBluffiness)
                            + " equityEst=" + String.format(java.util.Locale.ENGLISH, "%.2f", equityEst)
                            + " 底池赔率=" + String.format(java.util.Locale.ENGLISH, "%.2f", potOdds)
                            + " 跟注成本占筹码=" + String.format(java.util.Locale.ENGLISH, "%.2f", callRatio)
                            + " 情绪=" + String.format(java.util.Locale.ENGLISH, "%.2f", mood)
                            + " minStackBB=" + String.format(java.util.Locale.ENGLISH, "%.2f", stackCtx.minStackBB)
                            + " avgStackBB=" + String.format(java.util.Locale.ENGLISH, "%.2f", stackCtx.avgStackBB)
                            + " activeVillains=" + activeVillains
                            + " → 选择=FOLD");
                    return new BotAction(BotActionType.FOLD, 0);
                }

                double r = random.nextDouble();

                // 位置意识：翻后无人下注时，晚位在干燥牌面上有一定概率用弱/中牌做小额 c-bet
//                TablePosition position = getTablePosition(room, bot);
                if (callAmount == 0
                        && ("flop".equals(stage) || "turn".equals(stage))
                        && position == TablePosition.LATE
                        && bd == BoardDanger.DRY
                        && (st == SimpleStrength.WEAK || st == SimpleStrength.MEDIUM)) {
                    double baseCbetProb = (st == SimpleStrength.MEDIUM) ? 0.55 : 0.45;
                    // 根据对手类型和筹码环境微调：面对疯狗/短码时稍微谨慎，面对紧弱/全体深码时更爱 c-bet
                    if (villainTier == VillainRangeTier.MANIAC || villainTier == VillainRangeTier.LOOSE) {
                        baseCbetProb -= 0.05;
                    } else if (villainTier == VillainRangeTier.NIT || villainTier == VillainRangeTier.TIGHT) {
                        baseCbetProb += 0.05;
                    }
                    if (stackCtx.avgStackBB >= SharkConfig.DEEP_TABLE_AVG_BB) {
                        baseCbetProb += 0.05;
                    }
                    // 情绪高时更愿意主动下注，情绪低时略微收缩
                    baseCbetProb += mood * 0.05;
                    if (baseCbetProb < 0.2) baseCbetProb = 0.2;
                    if (baseCbetProb > 0.8) baseCbetProb = 0.8;
                    baseCbetProb = applySoftNoise(baseCbetProb, SharkConfig.PROB_NOISE_DELTA, random);

                    if (random.nextDouble() < baseCbetProb) {
                        int sb = DpRoom.getSBChips();
                        int pot = room.getPot();
                        double sizeFactor = 0.33 + random.nextDouble() * 0.17; // 约 1/3 ~ 1/2 pot
                        int target = (int) Math.round(pot * sizeFactor);
                        int minBet = DpRoom.getBBChips() * 2;
                        if (target < minBet) {
                            target = minBet;
                        }
                        int raiseAmount = Math.min(chips, target);
                        if (sb > 0 && raiseAmount > 0) {
                            int units = Math.max(1, Math.round(raiseAmount * 1.0f / sb));
                            raiseAmount = units * sb;
                            if (raiseAmount > chips) {
                                units = Math.max(1, chips / sb);
                                raiseAmount = units * sb;
                            }
                        }
                        System.out.println("[BOT_Shark] 决策=RAISE(CBET)"
                                + " room=" + room.getRoomId()
                                + " hero=" + bot.getNickname()
                                + " stage=" + stage
                                + " pos=" + position
                                + " strength=" + st
                                + " board=" + bd
                                + " chips=" + chips
                                + " call=" + callAmount
                                + " mood=" + mood
                                + " raiseAmount=" + raiseAmount);
                        System.out.println("[BOT_Shark-Explain] "
                                + "阶段=" + stage
                                + " 牌力=" + st
                                + " 牌面=" + bd
                                + " 主要对手=" + villainName
                                + " 对手风格=" + villainTier
                                + " 动作可信度=" + credibility
                                + " showdownBluffiness=" + String.format(java.util.Locale.ENGLISH, "%.2f", showdownBluffiness)
                                + " 底池赔率=" + String.format(java.util.Locale.ENGLISH, "%.2f", potOdds)
                                + " 跟注成本占筹码=" + String.format(java.util.Locale.ENGLISH, "%.2f", callRatio)
                                + " 情绪=" + String.format(java.util.Locale.ENGLISH, "%.2f", mood)
                                + " minStackBB=" + String.format(java.util.Locale.ENGLISH, "%.2f", stackCtx.minStackBB)
                                + " avgStackBB=" + String.format(java.util.Locale.ENGLISH, "%.2f", stackCtx.avgStackBB)
                                + " → 选择=RAISE(CBET)"
                                + " 金额=" + raiseAmount);
                        return new BotAction(BotActionType.RAISE, raiseAmount);
                    }
                }

                boolean isLateStreet = "river".equals(stage);

                double callProb = 0.6 + mood * 0.05;
                if (st == SimpleStrength.STRONG) {
                    callProb -= 0.25;
                    if (!isLateStreet) {
                        callProb += 0.15;
                    }
                } else if (st == SimpleStrength.MEDIUM) {
                    callProb += 0.05;
                }

                // 弱牌 + 有人下注时，大致视为诈唬候选，根据对手后手筹码调整 bluff 频率
                if (st == SimpleStrength.WEAK && callAmount > 0) {
                    int bbForBluff = DpRoom.getBBChips();
                    int villainStack = aggressor != null ? aggressor.getChips() : 0;
                    double villainBB = bbForBluff > 0 ? villainStack * 1.0 / bbForBluff : 0.0;
                    // 对方和桌面整体都很深码 → 适当提升跟注/弃牌倾向，减少 bluff
                    if (villainBB > SharkConfig.DEEP_STACK_MIN_BB && stackCtx.avgStackBB > SharkConfig.DEEP_TABLE_AVG_BB) {
                        callProb += 0.10; // 更少 raise，更多 call
                    }
                    // 典型短码面前也不做特别夸张 bluff，略微偏向跟注
                    if (villainBB > 0 && villainBB <= SharkConfig.SHORT_STACK_BB) {
                        callProb += 0.05;
                    }
                }

                // 早位 + 湿牌面 + 弱牌：整体更趋向防守 —— 免费看牌时更少主动下注，有人下注时略微提高弃牌倾向
                if (position == TablePosition.EARLY && bd == BoardDanger.WET && st == SimpleStrength.WEAK) {
                    // 有人下注时：在前面基础上再叠加一个轻微弃牌意愿
                    if (callAmount > 0) {
                        callProb -= 0.08;
                    }
                }

                callProb = applySoftNoise(callProb, SharkConfig.PROB_NOISE_DELTA, random);
                if (r < Math.max(0.1, Math.min(0.9, callProb))) {
                    System.out.println("[BOT_Shark] 决策=CALL_OR_CHECK"
                            + " room=" + room.getRoomId()
                            + " hero=" + bot.getNickname()
                            + " stage=" + room.getCurrentStage()
                            + " pos=" + getTablePosition(room, bot)
                            + " strength=" + st
                            + " board=" + bd
                            + " chips=" + chips
                            + " call=" + callAmount
                            + " mood=" + mood
                            + " callProb=" + String.format(java.util.Locale.ENGLISH, "%.2f", callProb));
                    System.out.println("[BOT_Shark-Explain] "
                            + "阶段=" + stage
                            + " 牌力=" + st
                            + " 牌面=" + bd
                            + " 主要对手=" + villainName
                            + " 对手风格=" + villainTier
                            + " 动作可信度=" + credibility
                                + " showdownBluffiness=" + String.format(java.util.Locale.ENGLISH, "%.2f", showdownBluffiness)
                                + " equityEst=" + String.format(java.util.Locale.ENGLISH, "%.2f", equityEst)
                            + " 底池赔率=" + String.format(java.util.Locale.ENGLISH, "%.2f", potOdds)
                            + " 跟注成本占筹码=" + String.format(java.util.Locale.ENGLISH, "%.2f", callRatio)
                            + " 情绪=" + String.format(java.util.Locale.ENGLISH, "%.2f", mood)
                            + " minStackBB=" + String.format(java.util.Locale.ENGLISH, "%.2f", stackCtx.minStackBB)
                                + " avgStackBB=" + String.format(java.util.Locale.ENGLISH, "%.2f", stackCtx.avgStackBB)
                                + " activeVillains=" + activeVillains
                            + " → 选择=CALL_OR_CHECK");
                    return new BotAction(BotActionType.CALL_OR_CHECK, 0);
                }

                if (!"preflop".equals(stage) && chips > callAmount) {
                    int sb = DpRoom.getSBChips();
                    int bb = DpRoom.getBBChips();
                    int raiseAmount;
                    if (callAmount == 0) {
                        int pot = room.getPot();
                        double baseFactor;
                        if (st == SimpleStrength.STRONG || st == SimpleStrength.MONSTER) {
                            baseFactor = 0.8;
                        } else if (st == SimpleStrength.MEDIUM) {
                            baseFactor = 0.6;
                        } else {
                            baseFactor = 0.45;
                        }
                        double randomFactor = 0.9 + random.nextDouble() * 0.3; // 0.9~1.2
                        int target = (int) Math.round(pot * baseFactor * randomFactor);

                        // 短码桌：若存在典型短码且自己为强牌/怪兽牌，适当提高下注倍数，让短码一跟就接近 all-in
                        if (stackCtx.minStackBB > 0 && stackCtx.minStackBB <= SharkConfig.SHORT_STACK_BB
                                && (st == SimpleStrength.STRONG || st == SimpleStrength.MONSTER)) {
                            int effectiveBase = pot > 0 ? Math.min(pot, stackCtx.minStack) : stackCtx.minStack;
                            // 在 40%~80% 区间内平滑随机，模拟“让短码做生死决定”
                            double shortFactor = 0.4 + random.nextDouble() * 0.4;
                            int shortTarget = (int) Math.round(effectiveBase * shortFactor);
                            if (shortTarget > target) {
                                target = shortTarget;
                            }
                        }

                        // 深码桌 + 弱牌：翻后用更小的 c-bet 维护范围，避免在深码面前过度 bloating pot
                        if (stackCtx.avgStackBB >= SharkConfig.DEEP_TABLE_AVG_BB && st == SimpleStrength.WEAK
                                && ("flop".equals(stage) || "turn".equals(stage))) {
                            target = (int) Math.round(target * 0.8); // 稍微缩小到接近 1/3~1/2 pot
                        }

                        int minBet = bb * 2;
                        if (target < minBet) {
                            target = minBet;
                        }
                        raiseAmount = Math.min(chips, target);
                    } else {
                        int strengthFactor;
                        if (st == SimpleStrength.STRONG || st == SimpleStrength.MONSTER) {
                            strengthFactor = 5;
                        } else if (st == SimpleStrength.MEDIUM) {
                            strengthFactor = 4;
                        } else {
                            strengthFactor = 3;
                        }
                        int streetFactor = isLateStreet ? 2 : 1;
                        int extraMin = bb * strengthFactor;
                        int extraMax = bb * (strengthFactor + streetFactor);
                        if (extraMax < extraMin) {
                            extraMax = extraMin;
                        }
                        int extraRange = extraMax - extraMin + 1;
                        int extra = extraMin + random.nextInt(Math.max(1, extraRange));

                        int target = callAmount + extra;

                        // 根据主要对手 stack 深度微调加注额，体现“短码 all-in 压迫”与“深码多街 maneuver”
                        int aggressorStack = (aggressor != null) ? aggressor.getChips() : 0;
                        double aggressorStackBB = (bb > 0) ? (aggressorStack * 1.0 / bb) : 0.0;

                        // 对典型短码：强牌时把加注量拉近其 all-in 区，弱牌时控制 bluff 尺度
                        if (aggressorStackBB > 0 && aggressorStackBB <= SharkConfig.SHORT_STACK_BB) {
                            if (st == SimpleStrength.STRONG || st == SimpleStrength.MONSTER) {
                                // 让短码一跟就接近 all-in：在其后手 60%~100% 之间平滑选择一个阈值
                                double pressureFactor = 0.6 + random.nextDouble() * 0.4;
                                int desired = callAmount + (int) Math.round(aggressorStack * pressureFactor);
                                if (desired > target) {
                                    target = desired;
                                }
                            } else if (st == SimpleStrength.WEAK) {
                                // 弱牌 bluff 面对短码：限制在少量额外 BB，避免被轻松跟 all-in
                                int maxBluffExtra = bb * 3;
                                int bluffCap = callAmount + maxBluffExtra;
                                if (target > bluffCap) {
                                    target = bluffCap;
                                }
                            }
                        } else if (stackCtx.minStackBB > SharkConfig.DEEP_STACK_MIN_BB && stackCtx.avgStackBB > SharkConfig.DEEP_TABLE_AVG_BB) {
                            // 全体深码：强牌/怪兽在 turn/river 利用筹码优势做一点“价值压制”，弱牌 bluff 略微缩小
                            if (st == SimpleStrength.STRONG || st == SimpleStrength.MONSTER) {
                                // 价值下注 + 筹码优势时略微放大目标加注额
                                if ("turn".equals(stage) || "river".equals(stage)) {
                                    double hvRatio = computeHeroVsVillainStackRatio(bot, aggressor);
                                    if (hvRatio >= 1.5 && aggressorStackBB >= 25 && aggressorStackBB <= 60) {
                                        target = (int) Math.round(target * 1.15);
                                    }
                                }
                            } else if (st == SimpleStrength.WEAK) {
                                target = (int) Math.round(target * 0.9);
                            }
                        }

                        // multi-way pot 下的 bluff 收缩：弱牌 + 有人下注 + 多人参与时缩小目标加注量
                        if (activeVillains >= 3 && st == SimpleStrength.WEAK && callAmount > 0) {
                            target = (int) Math.round(target * 0.85);
                        }

                        raiseAmount = Math.min(chips, target);
                        // 有跟注时，为避免“只多加一点点”的反加，至少在当前跟注额基础上再加 3~4 个大盲
                        if (callAmount > 0 && bb > 0) {
                            int minExtraBB = 3;
                            // 若底池已经较大，说明可能已经多次加注，适当再多加一个 BB
                            if (room.getPot() > bb * 20) {
                                minExtraBB = 4;
                            }
                            int minRaise = callAmount + minExtraBB * bb;
                            if (raiseAmount < minRaise) {
                                raiseAmount = Math.min(chips, minRaise);
                            }
                        }
                    }
                    // 统一将加注额调整为「小盲筹码」的整数倍，避免出现 78 这类不规则金额
                    if (sb > 0 && raiseAmount > 0) {
                        int units = Math.max(1, Math.round(raiseAmount * 1.0f / sb));
                        raiseAmount = units * sb;
                        if (raiseAmount > chips) {
                            // 若四舍五入后超过筹码上限，则向下取整到最近的小盲倍数
                            units = Math.max(1, chips / sb);
                            raiseAmount = units * sb;
                        }
                    }
                    // 河牌极强牌：在干燥牌面上以一定概率选择 overbet 模式
                    boolean riverOverbet = false;
                    if ("river".equals(stage)
                            && st == SimpleStrength.MONSTER
                            && bd == BoardDanger.DRY
                            && room.getPot() > 0
                            && random.nextDouble() < SharkConfig.RIVER_OVERBET_PROB) {
                        int pot = room.getPot();
                        double factor = 1.2 + random.nextDouble() * 0.3; // 1.2~1.5
                        int overTarget = (int) Math.round(pot * factor);
                        if (overTarget > raiseAmount) {
                            raiseAmount = Math.min(chips, overTarget);
                            riverOverbet = true;
                        }
                    }

                    String raiseTag = riverOverbet ? "RAISE(OVERBET)" : "RAISE";

                    System.out.println("[BOT_Shark] 决策=" + raiseTag
                            + " room=" + room.getRoomId()
                            + " hero=" + bot.getNickname()
                            + " stage=" + room.getCurrentStage()
                            + " pos=" + getTablePosition(room, bot)
                            + " strength=" + st
                            + " board=" + bd
                            + " chips=" + chips
                            + " call=" + callAmount
                            + " raiseAmount=" + raiseAmount
                            + " mood=" + mood);
                    System.out.println("[BOT_Shark-Explain] "
                            + "阶段=" + stage
                            + " 牌力=" + st
                            + " 牌面=" + bd
                            + " 主要对手=" + villainName
                            + " 对手风格=" + villainTier
                            + " 动作可信度=" + credibility
                            + " showdownBluffiness=" + String.format(java.util.Locale.ENGLISH, "%.2f", showdownBluffiness)
                            + " 底池赔率=" + String.format(java.util.Locale.ENGLISH, "%.2f", potOdds)
                            + " 跟注成本占筹码=" + String.format(java.util.Locale.ENGLISH, "%.2f", callRatio)
                            + " 情绪=" + String.format(java.util.Locale.ENGLISH, "%.2f", mood)
                            + " minStackBB=" + String.format(java.util.Locale.ENGLISH, "%.2f", stackCtx.minStackBB)
                            + " avgStackBB=" + String.format(java.util.Locale.ENGLISH, "%.2f", stackCtx.avgStackBB)
                            + " → 选择=" + raiseTag
                            + " 金额=" + raiseAmount);
                    return new BotAction(BotActionType.RAISE, raiseAmount);
                }
                // 河牌无人下注且持有中等牌力时，有一定概率选择 block bet（小额价值下注）
                if ("river".equals(stage)
                        && callAmount == 0
                        && st == SimpleStrength.MEDIUM
                        && chips > 0
                        && random.nextDouble() < SharkConfig.RIVER_BLOCK_PROB) {
                    int sb = DpRoom.getSBChips();
                    int pot = room.getPot();
                    double factor = 0.33;
                    int target = (int) Math.round(pot * factor);
                    int bb = DpRoom.getBBChips();
                    int minBet = bb * 2;
                    if (target < minBet) {
                        target = minBet;
                    }
                    int raiseAmount = Math.min(chips, target);
                    if (sb > 0 && raiseAmount > 0) {
                        int units = Math.max(1, Math.round(raiseAmount * 1.0f / sb));
                        raiseAmount = units * sb;
                        if (raiseAmount > chips) {
                            units = Math.max(1, chips / sb);
                            raiseAmount = units * sb;
                        }
                    }
                    System.out.println("[BOT_Shark] 决策=RAISE(BLOCK)"
                            + " room=" + room.getRoomId()
                            + " hero=" + bot.getNickname()
                            + " stage=" + stage
                            + " pos=" + getTablePosition(room, bot)
                            + " strength=" + st
                            + " board=" + bd
                            + " chips=" + chips
                            + " call=" + callAmount
                            + " mood=" + mood
                            + " raiseAmount=" + raiseAmount);
                    System.out.println("[BOT_Shark-Explain] "
                            + "阶段=" + stage
                            + " 牌力=" + st
                            + " 牌面=" + bd
                            + " 主要对手=" + villainName
                            + " 对手风格=" + villainTier
                            + " 动作可信度=" + credibility
                            + " showdownBluffiness=" + String.format(java.util.Locale.ENGLISH, "%.2f", showdownBluffiness)
                            + " equityEst=" + String.format(java.util.Locale.ENGLISH, "%.2f", equityEst)
                            + " 底池赔率=" + String.format(java.util.Locale.ENGLISH, "%.2f", potOdds)
                            + " 跟注成本占筹码=" + String.format(java.util.Locale.ENGLISH, "%.2f", callRatio)
                            + " 情绪=" + String.format(java.util.Locale.ENGLISH, "%.2f", mood)
                            + " minStackBB=" + String.format(java.util.Locale.ENGLISH, "%.2f", stackCtx.minStackBB)
                            + " avgStackBB=" + String.format(java.util.Locale.ENGLISH, "%.2f", stackCtx.avgStackBB)
                            + " activeVillains=" + activeVillains
                            + " → 选择=RAISE(BLOCK)"
                            + " 金额=" + raiseAmount);
                    return new BotAction(BotActionType.RAISE, raiseAmount);
                }
                System.out.println("[BOT_Shark] 决策=CALL_OR_CHECK(默认)"
                        + " room=" + room.getRoomId()
                        + " hero=" + bot.getNickname()
                        + " stage=" + room.getCurrentStage()
                        + " pos=" + getTablePosition(room, bot)
                        + " strength=" + st
                        + " board=" + bd
                        + " chips=" + chips
                        + " call=" + callAmount
                        + " mood=" + mood);
                System.out.println("[BOT_Shark-Explain] "
                        + "阶段=" + stage
                        + " 牌力=" + st
                        + " 牌面=" + bd
                        + " 主要对手=" + villainName
                        + " 对手风格=" + villainTier
                        + " 动作可信度=" + credibility
                        + " 底池赔率=" + String.format(java.util.Locale.ENGLISH, "%.2f", potOdds)
                        + " 跟注成本占筹码=" + String.format(java.util.Locale.ENGLISH, "%.2f", callRatio)
                        + " 情绪=" + String.format(java.util.Locale.ENGLISH, "%.2f", mood)
                        + " minStackBB=" + String.format(java.util.Locale.ENGLISH, "%.2f", stackCtx.minStackBB)
                        + " avgStackBB=" + String.format(java.util.Locale.ENGLISH, "%.2f", stackCtx.avgStackBB)
                        + " activeVillains=" + activeVillains
                        + " → 选择=CALL_OR_CHECK(默认)");
                return new BotAction(BotActionType.CALL_OR_CHECK, 0);
            }
            case TAG: {
                // 紧凶型 TAG（重写逻辑）：
                // - 翻前：明显更紧，对 WEAK 起手在大部分情况下直接弃牌；
                // - 翻后：中等牌有一定概率 thin value，强牌更积极 value bet / raise；
                // - 不做复杂读牌，只基于牌力+赔率+简单压力做决策。
                BoardDanger bd = boardDanger;
                SimpleStrength st = strength;
                String stage = room.getCurrentStage();
                callAmount = Math.max(0, room.getCurrentBetToCall() - bot.getBet());
                callRatio = chips == 0 ? 1.0 : (callAmount * 1.0 / chips);
                SmartContext ctx = buildSmartContext(
                        room,
                        bot,
                        st,
                        stage,
                        callAmount,
                        difficulty,
                        random
                );

                // ==== 1) preflop：明显更紧，弱牌弃牌概率由 preflopTightness 驱动 ====
                if ("preflop".equals(stage)) {
                    if (st == SimpleStrength.WEAK) {
                        double baseFoldWeakPreflop = 0.4 + 0.55 * preflopTight;
                        if (callRatio < 0.1) {
                            baseFoldWeakPreflop -= 0.15;
                        } else if (callRatio > 0.4) {
                            baseFoldWeakPreflop += 0.1;
                        }
                        double foldProbPre = Math.min(1.0, Math.max(0.0, baseFoldWeakPreflop + (-mood) * 0.1));
                        foldProbPre = applySoftNoise(foldProbPre, SharkConfig.PROB_NOISE_DELTA, random);
                        if (random.nextDouble() < foldProbPre) {
                            return new BotAction(BotActionType.FOLD, 0);
                        }
                    }
                    // 强牌在有人 open 时更偏向 3bet，抢主动权
                    if (callAmount > 0 && (st == SimpleStrength.STRONG || st == SimpleStrength.MONSTER)) {
                        int bbPre = DpRoom.getBBChips();
                        int extraBB = (st == SimpleStrength.MONSTER) ? 4 : 3;
                        int extra = extraBB * bbPre;
                        int target = callAmount + extra;
                        int raiseAmount = Math.min(chips, target);
                        int sbPre = DpRoom.getSBChips();
                        if (sbPre > 0 && raiseAmount > 0) {
                            int units = Math.max(1, Math.round(raiseAmount * 1.0f / sbPre));
                            raiseAmount = units * sbPre;
                            if (raiseAmount > chips) {
                                units = Math.max(1, chips / sbPre);
                                raiseAmount = units * sbPre;
                            }
                        }
                        // 至少保证是真 3bet：在跟注基础上再多加若干个大盲
                        if (bbPre > 0 && callAmount > 0) {
                            int minRaise = callAmount + 3 * bbPre;
                            if (raiseAmount < minRaise) {
                                raiseAmount = Math.min(chips, minRaise);
                            }
                        }
                        if (raiseAmount > callAmount) {
                            return new BotAction(BotActionType.RAISE, raiseAmount);
                        }
                    }
                    // 其他 preflop 情况：直接跟注/过牌
                    return new BotAction(BotActionType.CALL_OR_CHECK, 0);
                }

                // ==== 2) flop/turn/river：先决定要不要弃牌 ====
                double baseFold = 0.0;
                if (st == SimpleStrength.WEAK) {
                    if (callRatio > 0.5) {
                        baseFold = (bd == BoardDanger.WET) ? 0.8 : 0.6;
                    } else if (bd == BoardDanger.WET) {
                        baseFold = 0.45;
                    }
                } else if (st == SimpleStrength.MEDIUM && callRatio > 0.6 && bd == BoardDanger.WET) {
                    baseFold = 0.35;
                }

                double potOdds = ctx.potOdds;
                if (st == SimpleStrength.WEAK && potOdds > 0.6) {
                    baseFold = Math.min(1.0, baseFold + 0.2);
                } else if (potOdds < 0.25 && (st == SimpleStrength.MEDIUM || st == SimpleStrength.STRONG)) {
                    baseFold = Math.max(0.0, baseFold * 0.75);
                }

                // 让 TAG 也具备一定“读人”能力：结合对手风格 + 摊牌 bluff 倾向微调弃牌基准
                ActionCredibility tagCred = ctx.credibility;
                double tagShowdownBluff = ctx.showdownBluffiness;
                if (tagCred == ActionCredibility.HIGH || tagShowdownBluff < 0.1) {
                    // 认定对手更偏“诚实价值下注”的类型：弱牌在压力下更倾向早弃
                    baseFold = Math.min(1.0, baseFold + 0.1);
                } else if (tagCred == ActionCredibility.LOW || tagShowdownBluff > 0.4) {
                    // 认定对手常 bluff：TAG 会多给一点跟注/继续抗压的空间
                    baseFold = Math.max(0.0, baseFold * 0.7);
                }

                double foldProbTag = Math.min(1.0, Math.max(0.0, baseFold + (-mood) * 0.05));
                // callStation 越高，越偏向继续跟注，整体降低 fold 概率
                foldProbTag *= (1.0 - 0.5 * callStation);
                foldProbTag = applySoftNoise(foldProbTag, SharkConfig.PROB_NOISE_DELTA, random);
                if (callAmount > 0 && foldProbTag > 0 && random.nextDouble() < foldProbTag) {
                    return new BotAction(BotActionType.FOLD, 0);
                }

                // ==== 3) 免费看牌：强牌高频 value，MEDIUM 少量 thin value ====
                if (callAmount == 0) {
                    int pot = room.getPot();
                    int bbPost = DpRoom.getBBChips();
                    int sbPost = DpRoom.getSBChips();

                    double valueBetProb;
                    double factor;
                    if (st == SimpleStrength.MONSTER) {
                        valueBetProb = 0.85 + mood * 0.05;
                        factor = "river".equals(stage) ? 0.9 : 0.7;
                    } else if (st == SimpleStrength.STRONG) {
                        valueBetProb = 0.7 + mood * 0.05;
                        factor = "river".equals(stage) ? 0.7 : 0.5;
                    } else if (st == SimpleStrength.MEDIUM) {
                        valueBetProb = ("river".equals(stage) ? 0.2 : 0.35) + mood * 0.03;
                        factor = 0.4;
                    } else {
                        return new BotAction(BotActionType.CALL_OR_CHECK, 0);
                    }
                    // 翻后 value bet 概率由 aggression 调节：在当前基础上做 0.7x~1.3x 的缩放
                    double aggroFactor = 0.7 + 0.6 * aggression;
                    valueBetProb *= aggroFactor;
                    valueBetProb = applySoftNoise(
                            Math.min(0.95, Math.max(0.05, valueBetProb)),
                            SharkConfig.PROB_NOISE_DELTA,
                            random
                    );
                    if (random.nextDouble() < valueBetProb) {
                        int target = (int) Math.round(pot * factor);
                        int minBet = bbPost * 2;
                        if (target < minBet) {
                            target = minBet;
                        }
                        int raiseAmount = Math.min(chips, target);
                        if (sbPost > 0 && raiseAmount > 0) {
                            int units = Math.max(1, Math.round(raiseAmount * 1.0f / sbPost));
                            raiseAmount = units * sbPost;
                            if (raiseAmount > chips) {
                                units = Math.max(1, chips / sbPost);
                                raiseAmount = units * sbPost;
                            }
                        }
                        return new BotAction(BotActionType.RAISE, raiseAmount);
                    }
                    return new BotAction(BotActionType.CALL_OR_CHECK, 0);
                }

                // ==== 4) 有跟注成本：根据牌力决定加注还是跟注 ====
                double raiseProb;
                if (st == SimpleStrength.MONSTER) {
                    raiseProb = 0.8 + mood * 0.05;
                } else if (st == SimpleStrength.STRONG) {
                    raiseProb = 0.65 + mood * 0.05;
                } else if (st == SimpleStrength.MEDIUM) {
                    raiseProb = 0.4 + mood * 0.03;
                } else {
                    raiseProb = 0.1;
                }
                // 翻后加注倾向同样受 aggression 控制
                double aggroFactorRaise = 0.7 + 0.6 * aggression;
                raiseProb *= aggroFactorRaise;
                raiseProb = applySoftNoise(
                        Math.min(0.9, Math.max(0.05, raiseProb)),
                        SharkConfig.PROB_NOISE_DELTA,
                        random
                );

                double r = random.nextDouble();
                if (r > raiseProb || chips <= callAmount) {
                    return new BotAction(BotActionType.CALL_OR_CHECK, 0);
                }

                int bb = DpRoom.getBBChips();
                int extraMinBB;
                int extraMaxBB;
                if (st == SimpleStrength.MONSTER) {
                    extraMinBB = 4;
                    extraMaxBB = "river".equals(stage) ? 8 : 6;
                } else if (st == SimpleStrength.STRONG) {
                    extraMinBB = 3;
                    extraMaxBB = "river".equals(stage) ? 6 : 5;
                } else { // MEDIUM
                    extraMinBB = 2;
                    extraMaxBB = 4;
                }
                int range = Math.max(1, extraMaxBB - extraMinBB + 1);
                int extraBB = extraMinBB + random.nextInt(range);
                int extra = extraBB * bb;

                int target = callAmount + extra;
                if (!"river".equals(stage) && st == SimpleStrength.MEDIUM) {
                    target = (int) Math.round(target * 0.8);
                }

                int raiseAmount = Math.min(chips, target);
                // 至少保证反加不是“只多 1~2 个大盲”：在已有跟注额基础上再多加 3~4 个 BB
                if (callAmount > 0 && bb > 0) {
                    int minExtraBB = (st == SimpleStrength.MONSTER || st == SimpleStrength.STRONG) ? 4 : 3;
                    if (room.getPot() > bb * 20) {
                        minExtraBB += 1;
                    }
                    int minRaise = callAmount + minExtraBB * bb;
                    if (raiseAmount < minRaise) {
                        raiseAmount = Math.min(chips, minRaise);
                    }
                }

                int sb = DpRoom.getSBChips();
                if (sb > 0 && raiseAmount > 0) {
                    int units = Math.max(1, Math.round(raiseAmount * 1.0f / sb));
                    raiseAmount = units * sb;
                    if (raiseAmount > chips) {
                        units = Math.max(1, chips / sb);
                        raiseAmount = units * sb;
                    }
                }
                if (raiseAmount <= callAmount) {
                    return new BotAction(BotActionType.CALL_OR_CHECK, 0);
                }
                return new BotAction(BotActionType.RAISE, raiseAmount);
            }
            default:
                return new BotAction(BotActionType.CALL_OR_CHECK, 0);
        }
    }
}

