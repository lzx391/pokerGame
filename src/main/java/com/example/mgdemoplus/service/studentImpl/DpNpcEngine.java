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

    public static final String DEMO_BOT_NICKNAME = "BOT_Demo";
    public static final String MANIAC_BOT_NICKNAME = "BOT_Maniac";
    public static final String SHARK_BOT_NICKNAME = "BOT_Shark";

    /**
     * 机器人类型（现有昵称入口），后续只用来映射「风格 + 难度」。
     * 真正的决策逻辑不按类型分叉，只按参数表工作。
     */
    private enum BotType {
        DEMO,
        MANIAC,
        SHARK
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
        HARD
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
    private enum VillainRangeTier {
        NIT,        // 极紧
        TIGHT,      // 稍紧
        BALANCED,   // 正常
        LOOSE,      // 偏松
        MANIAC      // 疯狗
    }

    /**
     * 当前一轮动作的可信度：是价值下注还是诈唬的概率感知。
     */
    private enum ActionCredibility {
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
    }

    public static boolean isBotPlayer(DpPlayer p) {
        if (p == null) return false;
        String name = p.getNickname();
        return DEMO_BOT_NICKNAME.equals(name)
                || MANIAC_BOT_NICKNAME.equals(name)
                || SHARK_BOT_NICKNAME.equals(name);
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
        return null;
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
     */
    @SuppressWarnings("unused")
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
        Random random = new Random();

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
    private static BotAction decideBotAction(DpRoom room, DpPlayer bot, BotType type) {
        int chips = bot.getChips();
        if (chips <= 0) {
            return new BotAction(BotActionType.FOLD, 0);
        }

        int callAmount = Math.max(0, room.getCurrentBetToCall() - bot.getBet());
        double callRatio = chips == 0 ? 1.0 : (callAmount * 1.0 / chips);
        TablePosition position = getTablePosition(room, bot);
        SimpleStrength rawStrength = estimateCurrentStrength(room, bot);
        NpcDifficulty difficulty = (type == BotType.SHARK) ? NpcDifficulty.HARD
                : (type == BotType.MANIAC ? NpcDifficulty.MEDIUM : NpcDifficulty.MEDIUM);
        Random random = new Random();
        SimpleStrength strength = applyStrengthNoise(rawStrength, difficulty, random);
        BoardDanger boardDanger = evaluateBoardDanger(room.getCommunityCards());
        double mood = bot.getMood();

        switch (type) {
            case DEMO: {
                double foldBase = 0.0;
                if (strength == SimpleStrength.WEAK && callAmount > 0 && callRatio > 0.5) {
                    foldBase = 0.6;
                    if (boardDanger == BoardDanger.WET) {
                        foldBase += 0.2;
                    }
                }
                double foldProb = Math.min(1.0, Math.max(0.0, foldBase + (-mood) * 0.2));
                if (foldProb > 0 && random.nextDouble() < foldProb) {
                    return new BotAction(BotActionType.FOLD, 0);
                }

                double r = random.nextDouble();

                double callOrCheckProb = 0.75 - mood * 0.1;
                if (r < callOrCheckProb) {
                    return new BotAction(BotActionType.CALL_OR_CHECK, 0);
                }

                String stage = room.getCurrentStage();
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

                if (callAmount >= chips) {
                    double rAllIn = random.nextDouble();
                    if (rAllIn < 0.08) {
                        return new BotAction(BotActionType.FOLD, 0);
                    } else {
                        return new BotAction(BotActionType.ALL_IN, chips);
                    }
                }

                double r = random.nextDouble();

                if (callAmount == 0) {
                    double raiseProb = 0.75 + mood * 0.15;
                    raiseProb = Math.max(0.6, Math.min(0.95, raiseProb));

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
                double maniFoldProb = maniFoldBase - mood * 0.2;
                if (maniFoldProb < 0.0) maniFoldProb = 0.0;
                if (maniFoldProb > 0.5) maniFoldProb = 0.5;
                if (random.nextDouble() < maniFoldProb) {
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
                        minMulti = 2;
                        maxMulti = 5;
                    } else {
                        minMulti = 1;
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
                    return new BotAction(BotActionType.RAISE, raiseAmount);
                }
                return new BotAction(BotActionType.ALL_IN, chips);
            }
            case SHARK: {
                BoardDanger bd = boardDanger;
                SimpleStrength st = strength;

                double baseFold = 0.0;
                if (st == SimpleStrength.WEAK && callAmount > 0 && callRatio > 0.4) {
                    baseFold = (bd == BoardDanger.WET) ? 0.7 : 0.5;
                }

                PlayerStats.SingleHandStats lastAggressorStats = null;
                PlayerStats aggressorStats = null;
                Map<String, PlayerStats> statsMap = room.getPlayerStatsMap();
                if (statsMap != null) {
                    DpPlayer aggressor = null;
                    int maxBet = 0;
                    for (DpPlayer p : room.getPlayers()) {
                        if (p.isFold() || p.isLeftThisHand()) continue;
                        if (p.getBet() > maxBet) {
                            maxBet = p.getBet();
                            aggressor = p;
                        }
                    }
                    if (aggressor != null && !aggressor.getNickname().equals(bot.getNickname())) {
                        aggressorStats = statsMap.get(aggressor.getNickname());
                        if (aggressorStats != null && !aggressorStats.getRecentHands().isEmpty()) {
                            lastAggressorStats = aggressorStats.getRecentHands().peekLast();
                        }
                    }
                }

                if (aggressorStats != null) {
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

                double foldProbShark = Math.min(1.0, Math.max(0.0, baseFold + (-mood) * 0.1));
                if (foldProbShark > 0 && random.nextDouble() < foldProbShark) {
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
                    return new BotAction(BotActionType.FOLD, 0);
                }

                double r = random.nextDouble();

                String stage = room.getCurrentStage();

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
                    return new BotAction(BotActionType.CALL_OR_CHECK, 0);
                }

                if (!"preflop".equals(stage) && chips > callAmount) {
                    int raiseBase = callAmount;
                    int extraBB;
                    if (st == SimpleStrength.STRONG) {
                        extraBB = isLateStreet ? 4 : 2;
                    } else if (st == SimpleStrength.MEDIUM) {
                        extraBB = 2;
                    } else {
                        extraBB = 1;
                    }
                    int minExtra = DpRoom.getBBChips() * extraBB;
                    int raiseAmount = Math.min(chips, raiseBase + minExtra);
                    System.out.println("[BOT_Shark] 决策=RAISE"
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
                return new BotAction(BotActionType.CALL_OR_CHECK, 0);
            }
            default:
                return new BotAction(BotActionType.CALL_OR_CHECK, 0);
        }
    }
}

