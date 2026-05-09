package com.example.mgdemoplus.service.serviceImpl.dp;

import com.example.mgdemoplus.bo.DpRoomBO;
import com.example.mgdemoplus.entity.dp.DpPlayer;
import com.example.mgdemoplus.entity.dp.DpPlayerStats;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Shark 专用“学习实验模块”（不改 SmartContext，不影响其他 NPC）。
 *
 * <p>
 * 核心思想：不保存每一手的 SmartContext（太重），只保存“压缩后的长期记忆参数”。
 * </p>
 *
 * <p>
 * 当前实验只做一件事：对不同对手，累积一个“更愿意抓诈唬 / 更不尊重大注”的倾向，
 * 最终以一个 foldAdjustment（[-0.25, 0.25]）输出给 Shark 使用。
 * </p>
 *
 * <p>
 * 旋钮按对手 {@link DpPlayer#getNickname()} 全局存储（与房间 ID 无关），便于跨房间认出同一昵称；
 * 持久化由 {@link DpNpcSharkOpponentMemoryService} 在结算后写入 DB、上桌时加载。
 * </p>
 */
final class DpNpcSharkLearningLab {
    private DpNpcSharkLearningLab() {
    }

    private static String nk(String v) {
        return v == null ? "" : v;
    }

    private static void dbg(DpRoomBO room, String msg) {
        String rid = room != null ? room.getRoomId() : "-";
        System.out.println("[SHARK-LEARN][room=" + rid + "] " + msg);
    }

    static final class LearnedAdjust {
        /**
         * 对该对手的“整体弃牌倾向微调”：
         * - < 0：更不容易弃牌（更敢 bluff-catch / 更不尊重大注）
         * - > 0：更容易弃牌（更尊重其价值线）
         */
        final double foldAdjustment;

        /**
         * “更敢跟/更敢抓诈唬”的额外系数（0~0.25）：
         * - 越大：面对该玩家的大额压力时，整体更少弃牌（更愿意 call down / 抓 all-in 线）
         */
        final double bluffCatchBoost;

        /**
         * “下注尺度微调”乘子（约 0.9~1.15）：
         * - >1：对该玩家更愿意用更大尺度施压/榨取价值
         * - <1：对该玩家更偏向小尺度控池/试探
         */
        final double sizingFactor;

        /**
         * 对该对手整体「诈唬/空气施压」倾向乘子（约
         * {@link DpNpcEngine.SharkConfig#LEARN_BLUFF_FREQ_SCALE_MIN}~1.0）：
         * 由面对下注时的弃牌率、跟注站画像、负 reward 等数据学习，可真正把默认策略里的 bluff 压到低于基线。
         */
        final double bluffFrequencyScale;

        /**
         * 针对该对手的“更敢主动开枪/持续施压”的强度（0~0.25），按街拆分：
         * - flop：一枪（c-bet / stab）更敢开火
         * - turn：二枪（double barrel）更敢继续施压
         * - river：河牌收割/诈唬收尾（更谨慎，通常幅度较小）
         */
        final double bluffFireBoostFlop;
        final double bluffFireBoostTurn;
        final double bluffFireBoostRiver;

        /**
         * 针对该对手的“更敢 value 加注/加大价值压力”的强度（0~0.25），按街拆分：
         * - flop/turn：偏向持续构建底池
         * - river：偏向更敢做价值加注（对爱跟的人更明显）
         */
        final double valueRaiseBoostFlop;
        final double valueRaiseBoostTurn;
        final double valueRaiseBoostRiver;

        /**
         * “对该玩家施压时，更容易让他弃牌”的下注尺度偏好（按街），以 bet/pot 近似表示。
         * - 仅用于 bluff / steal 的下注尺度选择（不是 value）。
         * - 典型取值：0.33（小注）、0.55（中注）、0.80（大注）
         */
        final double bluffBetPotFactorFlop;
        final double bluffBetPotFactorTurn;
        final double bluffBetPotFactorRiver;

        LearnedAdjust(double foldAdjustment,
                double bluffCatchBoost,
                double sizingFactor,
                double bluffFrequencyScale,
                double bluffFireBoostFlop,
                double bluffFireBoostTurn,
                double bluffFireBoostRiver,
                double valueRaiseBoostFlop,
                double valueRaiseBoostTurn,
                double valueRaiseBoostRiver,
                double bluffBetPotFactorFlop,
                double bluffBetPotFactorTurn,
                double bluffBetPotFactorRiver) {
            this.foldAdjustment = foldAdjustment;
            this.bluffCatchBoost = bluffCatchBoost;
            this.sizingFactor = sizingFactor;
            this.bluffFrequencyScale = bluffFrequencyScale;
            this.bluffFireBoostFlop = bluffFireBoostFlop;
            this.bluffFireBoostTurn = bluffFireBoostTurn;
            this.bluffFireBoostRiver = bluffFireBoostRiver;
            this.valueRaiseBoostFlop = valueRaiseBoostFlop;
            this.valueRaiseBoostTurn = valueRaiseBoostTurn;
            this.valueRaiseBoostRiver = valueRaiseBoostRiver;
            this.bluffBetPotFactorFlop = bluffBetPotFactorFlop;
            this.bluffBetPotFactorTurn = bluffBetPotFactorTurn;
            this.bluffBetPotFactorRiver = bluffBetPotFactorRiver;
        }
    }

    /**
     * 可序列化快照（供 {@link DpNpcSharkOpponentMemoryService} 写入 JSON）。
     */
    public static final class LearnedPersistSnapshot {
        public double foldAdj;
        public double bluffCatch;
        public double sizing;
        public double shoveFreq;
        public double bluffFireFlop;
        public double bluffFireTurn;
        public double bluffFireRiver;
        public double valueRaiseFlop;
        public double valueRaiseTurn;
        public double valueRaiseRiver;
        /** 对某对手「诈唬/空气施压」总乘子，缺省 1.0；旧存档无此字段时 Jackson 为 0，导入时按 1.0 处理 */
        public double bluffFreqScale = 1.0;
        public java.util.List<BucketPersist> buckets = new java.util.ArrayList<>();

        public static final class BucketPersist {
            public String street;
            public String bucket;
            public int faced;
            public int folded;
            public double rewardSum;
            public int rewardCnt;
        }
    }

    /** 旋钮：按对手昵称（全局，不按房间）。 */
    private static final ConcurrentHashMap<String, Double> FOLD_ADJ = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, Double> BLUFF_CATCH_BOOST = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, Double> SIZING_FACTOR = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, Double> SHOVE_FREQ = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, Double> BLUFF_FIRE_BOOST_FLOP = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, Double> BLUFF_FIRE_BOOST_TURN = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, Double> BLUFF_FIRE_BOOST_RIVER = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, Double> VALUE_RAISE_BOOST_FLOP = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, Double> VALUE_RAISE_BOOST_TURN = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, Double> VALUE_RAISE_BOOST_RIVER = new ConcurrentHashMap<>();
    /** 对该对手整体诈唬频率乘子 [LEARN_BLUFF_FREQ_SCALE_MIN, 1.0]，默认 1 */
    private static final ConcurrentHashMap<String, Double> BLUFF_FREQ_SCALE = new ConcurrentHashMap<>();

    // ====== 按街 + 按尺度分桶：对手面对下注压力时的弃牌反应 ======
    private enum SizeBucket {
        SMALL, MEDIUM, BIG
    }

    private static final class BucketKey {
        final String villain;
        final String street; // flop/turn/river
        final SizeBucket bucket;

        BucketKey(String villain, String street, SizeBucket bucket) {
            this.villain = nk(villain);
            this.street = street == null ? "" : street;
            this.bucket = bucket == null ? SizeBucket.MEDIUM : bucket;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o)
                return true;
            if (o == null || getClass() != o.getClass())
                return false;
            BucketKey that = (BucketKey) o;
            return Objects.equals(villain, that.villain)
                    && Objects.equals(street, that.street)
                    && bucket == that.bucket;
        }

        @Override
        public int hashCode() {
            return Objects.hash(villain, street, bucket);
        }
    }

    private static final ConcurrentHashMap<BucketKey, Integer> FACED = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<BucketKey, Integer> FOLDED = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<BucketKey, Double> REWARD_SUM = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<BucketKey, Integer> REWARD_CNT = new ConcurrentHashMap<>();

    static LearnedAdjust getAdjust(DpRoomBO room, DpPlayer sharkBot, DpPlayer villain) {
        if (room == null || sharkBot == null || villain == null) {
            return new LearnedAdjust(0.0, 0.0, 1.0, 1.0,
                    0.0, 0.0, 0.0,
                    0.0, 0.0, 0.0,
                    0.45, 0.55, 0.55);
        }
        if (villain == sharkBot) {
            return new LearnedAdjust(0.0, 0.0, 1.0, 1.0,
                    0.0, 0.0, 0.0,
                    0.0, 0.0, 0.0,
                    0.45, 0.55, 0.55);
        }
        String vk = nk(villain.getNickname());
        double foldAdj = FOLD_ADJ.getOrDefault(vk, 0.0);
        double bluffCatch = BLUFF_CATCH_BOOST.getOrDefault(vk, 0.0);
        double sizing = SIZING_FACTOR.getOrDefault(vk, 1.0);
        double bluffFireFlop = BLUFF_FIRE_BOOST_FLOP.getOrDefault(vk, 0.0);
        double bluffFireTurn = BLUFF_FIRE_BOOST_TURN.getOrDefault(vk, 0.0);
        double bluffFireRiver = BLUFF_FIRE_BOOST_RIVER.getOrDefault(vk, 0.0);
        double valueRaiseFlop = VALUE_RAISE_BOOST_FLOP.getOrDefault(vk, 0.0);
        double valueRaiseTurn = VALUE_RAISE_BOOST_TURN.getOrDefault(vk, 0.0);
        double valueRaiseRiver = VALUE_RAISE_BOOST_RIVER.getOrDefault(vk, 0.0);
        if (sizing < DpNpcEngine.SharkConfig.LEARN_SIZING_MIN)
            sizing = DpNpcEngine.SharkConfig.LEARN_SIZING_MIN;
        if (sizing > DpNpcEngine.SharkConfig.LEARN_SIZING_MAX)
            sizing = DpNpcEngine.SharkConfig.LEARN_SIZING_MAX;
        if (bluffCatch < 0)
            bluffCatch = 0;
        if (bluffCatch > DpNpcEngine.SharkConfig.LEARN_BLUFF_CATCH_CAP)
            bluffCatch = DpNpcEngine.SharkConfig.LEARN_BLUFF_CATCH_CAP;
        bluffFireFlop = clampBluffFireBoost(bluffFireFlop);
        bluffFireTurn = clampBluffFireBoost(bluffFireTurn);
        bluffFireRiver = clampBluffFireBoost(bluffFireRiver);
        valueRaiseFlop = clampValueRaiseBoost(valueRaiseFlop);
        valueRaiseTurn = clampValueRaiseBoost(valueRaiseTurn);
        valueRaiseRiver = clampValueRaiseBoost(valueRaiseRiver);
        if (foldAdj < -DpNpcEngine.SharkConfig.LEARN_FOLD_ADJ_CAP)
            foldAdj = -DpNpcEngine.SharkConfig.LEARN_FOLD_ADJ_CAP;
        if (foldAdj > DpNpcEngine.SharkConfig.LEARN_FOLD_ADJ_CAP)
            foldAdj = DpNpcEngine.SharkConfig.LEARN_FOLD_ADJ_CAP;
        double bluffBetFlop = getPreferredBluffBetPot(vk, "flop", 0.45);
        double bluffBetTurn = getPreferredBluffBetPot(vk, "turn", 0.55);
        double bluffBetRiver = getPreferredBluffBetPot(vk, "river", 0.55);

        double bluffFreqScale = clampBluffFreqScale(BLUFF_FREQ_SCALE.getOrDefault(vk, 1.0));

        return new LearnedAdjust(foldAdj, bluffCatch, sizing, bluffFreqScale,
                bluffFireFlop, bluffFireTurn, bluffFireRiver,
                valueRaiseFlop, valueRaiseTurn, valueRaiseRiver,
                bluffBetFlop, bluffBetTurn, bluffBetRiver);
    }

    private static double clampBluffFreqScale(double v) {
        double min = DpNpcEngine.SharkConfig.LEARN_BLUFF_FREQ_SCALE_MIN;
        if (v < min) {
            return min;
        }
        if (v > 1.0) {
            return 1.0;
        }
        return v;
    }

    /**
     * 汇总该对手在所有街+桶里「面对下注」次数与弃牌次数（用于估计弃牌率）。
     */
    private static int aggregateFacedForVillain(String vk, int[] foldedOut) {
        int tf = 0;
        int td = 0;
        String[] streets = { "flop", "turn", "river" };
        for (String st : streets) {
            for (SizeBucket b : SizeBucket.values()) {
                BucketKey k = new BucketKey(vk, st, b);
                tf += FACED.getOrDefault(k, 0);
                td += FOLDED.getOrDefault(k, 0);
            }
        }
        foldedOut[0] = td;
        return tf;
    }

    /** SMALL/MEDIUM 桶的平均即时 reward（对大注桶略降权噪声），样本不足返回 0 */
    private static double weightedAvgRewardNonBigBuckets(String vk) {
        double sumW = 0.0;
        double sum = 0.0;
        for (Map.Entry<BucketKey, Integer> e : REWARD_CNT.entrySet()) {
            BucketKey key = e.getKey();
            if (!vk.equals(key.villain)) {
                continue;
            }
            if (key.bucket == SizeBucket.BIG) {
                continue;
            }
            int cnt = e.getValue() != null ? e.getValue() : 0;
            if (cnt <= 0) {
                continue;
            }
            double mean = REWARD_SUM.getOrDefault(key, 0.0) / cnt;
            sumW += cnt;
            sum += mean * cnt;
        }
        return sumW > 0 ? sum / sumW : 0.0;
    }

    private static double clampBluffFireBoost(double v) {
        if (v < 0)
            return 0;
        if (v > DpNpcEngine.SharkConfig.LEARN_BLUFF_FIRE_CAP)
            return DpNpcEngine.SharkConfig.LEARN_BLUFF_FIRE_CAP;
        return v;
    }

    private static double clampValueRaiseBoost(double v) {
        if (v < 0)
            return 0;
        if (v > DpNpcEngine.SharkConfig.LEARN_VALUE_RAISE_CAP)
            return DpNpcEngine.SharkConfig.LEARN_VALUE_RAISE_CAP;
        return v;
    }

    /**
     * 在一手牌结算后调用：利用已经更新好的 PlayerStats（以及本手摊牌牌力）去更新记忆参数。
     *
     * <p>
     * 规则（很粗，但可验证/可控）：
     * - 若对手“经常带着弱牌摊牌”（weakShowdownRatio 高），说明其线里 bluff/薄价值多：
     * Shark 对其整体更少弃牌（foldAdjustment 向负方向）。
     * - 若对手摊牌几乎都是强牌（weakShowdownRatio 很低），说明其更诚实：
     * Shark 更尊重其下注（foldAdjustment 向正方向）。
     *
     * <p>
     * 注意：这里不需要保存 SmartContext 历史，只用统计结果驱动“旋钮”。
     * </p>
     */
    static void onHandSettled(DpRoomBO room) {
        if (room == null || room.getPlayerStatsMap() == null) {
            return;
        }
        Map<String, DpPlayerStats> statsMap = room.getPlayerStatsMap();
        if (statsMap.isEmpty()) {
            return;
        }

        // 先用本手逐街动作日志更新 “面对不同尺度下注的弃牌反应”（按街 + 分桶）
        // 这一步只依赖 ActionLog，不依赖 PlayerStats（人手足够时全员桌都会记 ActionLog）。
        updateFoldBucketsFromActionLog(room);

        // 注意：你当前的对局很多是 bot vs bot。
        // 如果只学习真人玩家，那么面对 BOT_Fish/BOT_Maniac 时 LearnedAdjust 永远是 0，
        // Shark 就会“看起来完全不会针对人”。这里改成：除 Shark 自己外，其它玩家（包含机器人）都参与学习。
        for (DpPlayer p : room.getPlayers()) {
            if (p == null)
                continue;
            String name = p.getNickname();
            if (name == null)
                continue;
            if (DpNpcEngine.SHARK_BOT_NICKNAME.equals(name))
                continue;

            DpPlayerStats s = statsMap.get(name);
            if (s == null)
                continue;

            // 用最近 10 手的“弱摊牌比例”作为稳定信号（并输出调试分解，方便确认过滤是否生效）
            DpPlayerStats.ShowdownWeakStats weakStats = s.getRecentShowdownWeakStats(10);
            double weakRatio = weakStats.ratio();
            // dbg(room, "onHandSettled villain=" + name
            // + " sample=" + s.getSampleCount()
            // + " weakRatio(10)=" + String.format("%.3f", weakRatio)
            // + " weakSample=" + weakStats.sample
            // + " weakCnt=" + weakStats.weakCount
            // + " skipBoard=" + weakStats.skippedBoardDominant
            // + " VPIP=" + String.format("%.3f", s.getOverallParticipationRate())
            // + " PFR=" + String.format("%.3f", s.getOverallRaiseRate()));

            // === 基于 ActionLog 的“在压力下的弃牌曲线”作为主信号 ===
            String vk = nk(name);
            double turnBigFold = computeFoldRate(name, "turn", SizeBucket.BIG);
            double riverBigFold = computeFoldRate(name, "river", SizeBucket.BIG);
            int bigCnt = 0;
            double bigSum = 0.0;
            if (turnBigFold >= 0.0) {
                bigSum += turnBigFold;
                bigCnt++;
            }
            if (riverBigFold >= 0.0) {
                bigSum += riverBigFold;
                bigCnt++;
            }
            double bigFold = bigCnt > 0 ? (bigSum / bigCnt) : -1.0;

            double targetAdj = 0.0;
            if (bigFold >= 0.0) {
                // 1) 大注下弃牌很多 + 弱摊牌比例低 → 更偏向诚实强牌线：整体更尊重大注
                if (bigFold >= 0.60 && weakRatio <= 0.25) {
                    targetAdj = 0.18;
                } else if (bigFold >= 0.50 && weakRatio <= 0.35) {
                    targetAdj = 0.10;
                }
                // 2) 大注下很少弃牌 + 弱摊牌比例高 → 更偏向 bluff / 松薄价值：整体更少弃牌
                else if (bigFold <= 0.25 && weakRatio >= 0.35) {
                    targetAdj = -0.18;
                } else if (bigFold <= 0.35 && weakRatio >= 0.30) {
                    targetAdj = -0.10;
                } else {
                    targetAdj = 0.0;
                }
            } else {
                // 样本太少：退回到旧的“仅看 weakRatio”逻辑（权重略小）
                if (weakRatio >= 0.45) {
                    targetAdj = -0.14;
                } else if (weakRatio >= 0.30) {
                    targetAdj = -0.08;
                } else if (weakRatio <= 0.08) {
                    targetAdj = 0.12;
                } else if (weakRatio <= 0.15) {
                    targetAdj = 0.06;
                } else {
                    targetAdj = 0.0;
                }
            }

            double old = FOLD_ADJ.getOrDefault(vk, 0.0);

            // 缓慢学习：每手只向目标靠近一点点（避免两三手就“人格突变”）
            double lr = DpNpcEngine.SharkConfig.LEARN_FOLD_LR; // learning rate
            double next = old + (targetAdj - old) * lr;

            // 安全裁剪
            if (next > DpNpcEngine.SharkConfig.LEARN_FOLD_ADJ_CAP)
                next = DpNpcEngine.SharkConfig.LEARN_FOLD_ADJ_CAP;
            if (next < -DpNpcEngine.SharkConfig.LEARN_FOLD_ADJ_CAP)
                next = -DpNpcEngine.SharkConfig.LEARN_FOLD_ADJ_CAP;

            FOLD_ADJ.put(vk, next);

            // ===== 旋钮 2：对“高频 all-in / 高压玩家”增加 bluff-catch（更敢跟）=====
            // 信号来源：Shark 逐街动作日志（本手快照已在结算阶段生成 PlayerStats 前后可用）
            // 我们这里只做一个非常简单的 EMA：本手该玩家是否出现过 ALL_IN（任何街都算）
            boolean shovedThisHand = false;
            var events = DpNpcSharkHandActionLog.snapshot(room);
            if (events != null && !events.isEmpty()) {
                for (var e : events) {
                    if (e == null)
                        continue;
                    if (!name.equals(e.actor))
                        continue;
                    if (e.type == DpNpcSharkHandActionLog.ActionType.ALL_IN) {
                        shovedThisHand = true;
                        break;
                    }
                }
            }
            double shoveOld = SHOVE_FREQ.getOrDefault(vk, 0.0);
            double shoveObs = shovedThisHand ? 1.0 : 0.0;
            double a = DpNpcEngine.SharkConfig.LEARN_SHOVE_EMA_ALPHA;
            if (a < 0.01)
                a = 0.01;
            if (a > 0.50)
                a = 0.50;
            double shoveNext = shoveOld * (1.0 - a) + shoveObs * a;
            if (shoveNext < 0)
                shoveNext = 0;
            if (shoveNext > 1)
                shoveNext = 1;
            SHOVE_FREQ.put(vk, shoveNext);

            // shove 越高，bluffCatchBoost 越高（0~cap）
            double bluffCatchTarget = DpNpcEngine.SharkConfig.LEARN_BLUFF_CATCH_CAP * shoveNext;
            double bcOld = BLUFF_CATCH_BOOST.getOrDefault(vk, 0.0);
            double bcNext = bcOld + (bluffCatchTarget - bcOld) * DpNpcEngine.SharkConfig.LEARN_BLUFF_CATCH_LR;
            if (bcNext < 0)
                bcNext = 0;
            if (bcNext > DpNpcEngine.SharkConfig.LEARN_BLUFF_CATCH_CAP)
                bcNext = DpNpcEngine.SharkConfig.LEARN_BLUFF_CATCH_CAP;
            BLUFF_CATCH_BOOST.put(vk, bcNext);

            // ===== 旋钮 4：针对该玩家的“主动开枪/持续施压”倾向（按街拆分）=====
            // 信号：1）该玩家是否偏紧偏怂（tight passive）→ 更适合偷；2）该玩家是否常在 turn/river 放弃 → 更适合二枪/三枪；
            // 3）该玩家摊牌偏弱（weakRatio 高）→ 说明其线更松/更薄 → 适合持续施压与 thin value。
            double overallPart = s.getOverallParticipationRate();
            double overallRaise = s.getOverallRaiseRate();
            boolean tightPassive = overallPart > 0 && overallPart < 0.28 && overallRaise < 0.16;
            boolean callingStationLike = overallPart >= 0.48 && overallRaise < 0.18;

            int windowN = 10;
            int giveUpTurn = 0;
            int giveUpRiver = 0;
            int barrelOpp = 0;
            int processed = 0;
            for (DpPlayerStats.SingleHandStats h : s.getRecentHands()) {
                if (processed >= windowN)
                    break;
                processed++;
                // 只统计该玩家确实参与过的手，避免被弃牌/离线污染
                if (!h.isParticipated())
                    continue;
                barrelOpp++;
                if (h.isGaveUpTurnAfterRaise())
                    giveUpTurn++;
                if (h.isGaveUpRiverAfterRaise())
                    giveUpRiver++;
            }
            double giveUpTurnRate = barrelOpp > 0 ? (giveUpTurn * 1.0 / barrelOpp) : 0.0;
            double giveUpRiverRate = barrelOpp > 0 ? (giveUpRiver * 1.0 / barrelOpp) : 0.0;

            // flop：主要看 tightPassive 与 weakRatio（偷盲/薄价值），不直接吃 giveUpTurn/river
            double bluffFireFlopTarget = 0.0;
            if (tightPassive)
                bluffFireFlopTarget += 0.12;
            if (weakRatio >= 0.35)
                bluffFireFlopTarget += 0.05;
            // 跟注站：诈唬加成目标直接为 0（真正压诈唬靠 bluffFrequencyScale 乘子）
            if (callingStationLike) {
                bluffFireFlopTarget = 0.0;
            }
            bluffFireFlopTarget = clampBluffFireBoost(bluffFireFlopTarget);

            // turn：二枪最依赖“一枪流”（turn give-up）
            double bluffFireTurnTarget = 0.0;
            if (tightPassive)
                bluffFireTurnTarget += 0.08;
            if (giveUpTurnRate >= 0.30)
                bluffFireTurnTarget += 0.11;
            if (weakRatio >= 0.35)
                bluffFireTurnTarget += 0.04;
            if (callingStationLike) {
                bluffFireTurnTarget = 0.0;
            }
            bluffFireTurnTarget = clampBluffFireBoost(bluffFireTurnTarget);

            // river：更谨慎，只给小幅提升；主要看 river give-up 与 weakRatio
            double bluffFireRiverTarget = 0.0;
            if (giveUpRiverRate >= 0.22)
                bluffFireRiverTarget += 0.08;
            if (weakRatio >= 0.35)
                bluffFireRiverTarget += 0.03;
            if (callingStationLike) {
                bluffFireRiverTarget = 0.0;
            }
            bluffFireRiverTarget = clampBluffFireBoost(bluffFireRiverTarget);

            double bfLr = DpNpcEngine.SharkConfig.LEARN_BLUFF_FIRE_LR;
            BLUFF_FIRE_BOOST_FLOP.put(vk,
                    lerpBoost(BLUFF_FIRE_BOOST_FLOP.getOrDefault(vk, 0.0), bluffFireFlopTarget, bfLr));
            BLUFF_FIRE_BOOST_TURN.put(vk,
                    lerpBoost(BLUFF_FIRE_BOOST_TURN.getOrDefault(vk, 0.0), bluffFireTurnTarget, bfLr));
            BLUFF_FIRE_BOOST_RIVER.put(vk,
                    lerpBoost(BLUFF_FIRE_BOOST_RIVER.getOrDefault(vk, 0.0), bluffFireRiverTarget, bfLr));

            // ===== 旋钮 5：针对该玩家的 value 加注倾向（按街拆分）=====
            // 直觉：面对“爱跟不爱加”的人（站桩/松被动），强牌要更敢加注、加大榨取。
            double baseValue = 0.0;
            if (callingStationLike)
                baseValue += 0.16;
            if (weakRatio >= 0.30)
                baseValue += 0.06;
            baseValue -= 0.06 * shoveNext;
            baseValue = clampValueRaiseBoost(baseValue);

            // river 的 value raise 更重要一点（很多对手会在河牌“跟到底”）
            double valueFlopTarget = clampValueRaiseBoost(baseValue * 0.85);
            double valueTurnTarget = clampValueRaiseBoost(baseValue * 0.95);
            double valueRiverTarget = clampValueRaiseBoost(baseValue * 1.05);

            double vrLr = DpNpcEngine.SharkConfig.LEARN_VALUE_RAISE_LR;
            VALUE_RAISE_BOOST_FLOP.put(vk,
                    lerpValueBoost(VALUE_RAISE_BOOST_FLOP.getOrDefault(vk, 0.0), valueFlopTarget, vrLr));
            VALUE_RAISE_BOOST_TURN.put(vk,
                    lerpValueBoost(VALUE_RAISE_BOOST_TURN.getOrDefault(vk, 0.0), valueTurnTarget, vrLr));
            VALUE_RAISE_BOOST_RIVER.put(vk,
                    lerpValueBoost(VALUE_RAISE_BOOST_RIVER.getOrDefault(vk, 0.0), valueRiverTarget, vrLr));

            // ===== 旋钮 3：下注尺度微调（更像真人“对不同人下不同尺寸”）=====
            // 对于经常 shove 的玩家：我们更偏向用稍小的试探/控池（避免把自己逼进高波动）
            // 对于“弱摊牌比例高”的玩家：更偏向用稍大 value 尺度榨取
            double sizingTarget = 1.0;
            if (weakRatio >= 0.35) {
                sizingTarget += 0.06;
            } else if (weakRatio <= 0.12) {
                sizingTarget -= 0.03;
            }
            sizingTarget -= 0.08 * shoveNext;
            if (sizingTarget < 0.9)
                sizingTarget = 0.9;
            if (sizingTarget > 1.15)
                sizingTarget = 1.15;

            double szOld = SIZING_FACTOR.getOrDefault(vk, 1.0);
            double szNext = szOld + (sizingTarget - szOld) * DpNpcEngine.SharkConfig.LEARN_SIZING_LR;
            if (szNext < DpNpcEngine.SharkConfig.LEARN_SIZING_MIN)
                szNext = DpNpcEngine.SharkConfig.LEARN_SIZING_MIN;
            if (szNext > DpNpcEngine.SharkConfig.LEARN_SIZING_MAX)
                szNext = DpNpcEngine.SharkConfig.LEARN_SIZING_MAX;
            SIZING_FACTOR.put(vk, szNext);

            // ===== 旋钮 6：整体「诈唬/空气施压」乘子（可低于 1，专治跟注站）=====
            int[] foldedTot = new int[1];
            int facedTot = aggregateFacedForVillain(vk, foldedTot);
            double foldRateAll = facedTot >= 10 ? (foldedTot[0] * 1.0 / facedTot) : -1.0;
            double targetBfs = 1.0;
            if (foldRateAll >= 0) {
                if (foldRateAll < 0.06) {
                    targetBfs = 0.26;
                } else if (foldRateAll < 0.11) {
                    targetBfs = 0.34;
                } else if (foldRateAll < 0.17) {
                    targetBfs = 0.44;
                } else if (foldRateAll < 0.26) {
                    targetBfs = 0.58;
                } else if (foldRateAll < 0.36) {
                    targetBfs = 0.74;
                }
            }
            if (callingStationLike) {
                targetBfs = Math.min(targetBfs, 0.40);
            }
            double avgRew = weightedAvgRewardNonBigBuckets(vk);
            if (facedTot >= 24 && avgRew < -0.20) {
                targetBfs = Math.min(targetBfs, 0.88 * targetBfs);
            }
            double bfsOld = BLUFF_FREQ_SCALE.getOrDefault(vk, 1.0);
            double bfsLr = DpNpcEngine.SharkConfig.LEARN_BLUFF_FREQ_SCALE_LR;
            double bfsNext = bfsOld + (targetBfs - bfsOld) * bfsLr;
            BLUFF_FREQ_SCALE.put(vk, clampBluffFreqScale(bfsNext));

            double prefF = getPreferredBluffBetPot(name, "flop", 0.45);
            double prefT = getPreferredBluffBetPot(name, "turn", 0.55);
            double prefR = getPreferredBluffBetPot(name, "river", 0.55);
            turnBigFold = computeFoldRate(name, "turn", SizeBucket.BIG);
            riverBigFold = computeFoldRate(name, "river", SizeBucket.BIG);
            // dbg(room, "updated villain=" + name
            // + " foldAdj=" + String.format("%.3f", next)
            // + " bc=" + String.format("%.3f", bcNext)
            // + " sz=" + String.format("%.3f", szNext)
            // + " bf(f/t/r)=" + String.format("%.3f",
            // BLUFF_FIRE_BOOST_FLOP.getOrDefault(vk, 0.0)) + "/"
            // + String.format("%.3f", BLUFF_FIRE_BOOST_TURN.getOrDefault(vk, 0.0)) + "/"
            // + String.format("%.3f", BLUFF_FIRE_BOOST_RIVER.getOrDefault(vk, 0.0))
            // + " vr(f/t/r)=" + String.format("%.3f",
            // VALUE_RAISE_BOOST_FLOP.getOrDefault(vk, 0.0)) + "/"
            // + String.format("%.3f", VALUE_RAISE_BOOST_TURN.getOrDefault(vk, 0.0)) + "/"
            // + String.format("%.3f", VALUE_RAISE_BOOST_RIVER.getOrDefault(vk, 0.0))
            // + " turnBigFold=" + String.format("%.3f", turnBigFold < 0 ? -1.0 :
            // turnBigFold)
            // + " riverBigFold=" + String.format("%.3f", riverBigFold < 0 ? -1.0 :
            // riverBigFold)
            // + " prefSize(f/t/r)=" + String.format("%.2f", prefF) + "/" +
            // String.format("%.2f", prefT) + "/" + String.format("%.2f", prefR)
            // + " bfs=" + String.format("%.3f", BLUFF_FREQ_SCALE.getOrDefault(vk, 1.0))
            // + " foldRateAll=" + (foldRateAll < 0 ? "-1" : String.format("%.3f",
            // foldRateAll)));
        }
    }

    private static void updateFoldBucketsFromActionLog(DpRoomBO room) {
        List<DpNpcSharkHandActionLog.ActionEvent> events = DpNpcSharkHandActionLog.snapshot(room);
        if (events == null || events.isEmpty())
            return;
        for (DpNpcSharkHandActionLog.ActionEvent e : events) {
            if (e == null)
                continue;
            String street = e.stage;
            if (!"flop".equals(street) && !"turn".equals(street) && !"river".equals(street))
                continue;

            // 面对的跟注成本：betToCallBefore - actorBetBefore
            int callNeed = e.betToCallBefore - e.actorBetBefore;
            if (callNeed <= 0)
                continue; // 免费弃牌不算“面对下注压力”

            int potBefore = e.potBefore;
            if (potBefore <= 0)
                potBefore = 1;
            double betPot = callNeed * 1.0 / potBefore;
            SizeBucket b = bucketByBetPot(betPot);

            BucketKey k = new BucketKey(e.actor, street, b);
            // faced：只要该玩家在该街“需要付费才能继续”且做出了动作，就算一次面对压力的样本
            // 记录的动作包括：fold / call / raise / all-in
            boolean isResponse = e.type == DpNpcSharkHandActionLog.ActionType.FOLD
                    || e.type == DpNpcSharkHandActionLog.ActionType.CALL
                    || e.type == DpNpcSharkHandActionLog.ActionType.RAISE
                    || e.type == DpNpcSharkHandActionLog.ActionType.ALL_IN;
            if (!isResponse) {
                continue;
            }
            FACED.merge(k, 1, Integer::sum);
            if (e.type == DpNpcSharkHandActionLog.ActionType.FOLD) {
                FOLDED.merge(k, 1, Integer::sum);
            }

            // reward：用“即时收益近似”来衡量这个尺度是否有效（更像真人在试探边界）
            // - 若对手弃牌：奖励与底池规模正相关、与本次施压成本（callNeed）负相关
            // - 若对手不弃牌：小惩罚（说明该尺度未能拿下底池）
            double reward = computeImmediateReward(e.type, potBefore, callNeed);
            REWARD_SUM.merge(k, reward, Double::sum);
            REWARD_CNT.merge(k, 1, Integer::sum);
        }
    }

    private static double computeImmediateReward(DpNpcSharkHandActionLog.ActionType type, int potBefore, int callNeed) {
        if (potBefore <= 0)
            potBefore = 1;
        if (callNeed <= 0)
            callNeed = 1;
        double betPot = callNeed * 1.0 / potBefore;
        // fold：越便宜拿下越好，但也避免对极小成本出现过大数值
        if (type == DpNpcSharkHandActionLog.ActionType.FOLD) {
            double raw = 1.0 / Math.max(0.10, betPot); // betPot=0.33 -> ~3.0
            if (raw > 3.0)
                raw = 3.0;
            if (raw < 0.0)
                raw = 0.0;
            return raw;
        }
        // 不弃牌：轻微惩罚（避免学成“只下极小注永远试探”，也避免过度负反馈）
        // betPot 越大惩罚略大：说明这个尺度没把人打跑，风险更高
        double pen = 0.15 + 0.35 * Math.min(1.0, betPot); // 约 -0.15 ~ -0.50
        return -pen;
    }

    private static SizeBucket bucketByBetPot(double betPot) {
        if (betPot < 0.28)
            return SizeBucket.SMALL;
        if (betPot < 0.65)
            return SizeBucket.MEDIUM;
        return SizeBucket.BIG;
    }

    /**
     * 计算某玩家在指定街道 + 尺度桶下“在压力下的弃牌率”（fold / faced）。
     * 样本不足时返回 -1，调用方负责回退。
     */
    private static double computeFoldRate(String villain, String street, SizeBucket bucket) {
        if (villain == null || street == null || bucket == null) {
            return -1.0;
        }
        final int minSamples = 4;
        BucketKey k = new BucketKey(villain, street, bucket);
        int faced = FACED.getOrDefault(k, 0);
        int folded = FOLDED.getOrDefault(k, 0);
        if (faced < minSamples) {
            return -1.0;
        }
        if (faced <= 0) {
            return -1.0;
        }
        return folded * 1.0 / faced;
    }

    private static double getPreferredBluffBetPot(String villain, String street, double fallback) {
        // 我们在 LearningLab 里希望得到“哪种尺度更容易让他弃牌”
        // 规则：优先选择 foldRate 更高的桶；样本太少时回退默认
        final int minSamples = 3;
        double bestRate = -1.0;
        double bestFactor = fallback;

        for (SizeBucket b : SizeBucket.values()) {
            BucketKey k = new BucketKey(villain, street, b);
            int faced = FACED.getOrDefault(k, 0);
            int folded = FOLDED.getOrDefault(k, 0);
            if (faced < minSamples)
                continue;
            double rate = faced > 0 ? (folded * 1.0 / faced) : 0.0;
            if (rate > bestRate) {
                bestRate = rate;
                bestFactor = factorForBucket(b);
            }
        }

        return bestFactor;
    }

    /**
     * epsilon-greedy 选尺度：大多数时候用当前“最容易让他弃牌”的桶，小概率随机试探别的桶。
     * 这能模拟真人的“有意识试探边界”，并且随着样本增长逐步收敛到更有效的尺度。
     */
    static double pickBluffBetPotFactorWithExplore(DpRoomBO room,
            DpPlayer villain,
            String street,
            java.util.Random random,
            double fallback,
            double epsilon) {
        if (room == null || villain == null || random == null)
            return fallback;
        String name = villain.getNickname();
        if (name == null)
            return fallback;
        if (!"flop".equals(street) && !"turn".equals(street) && !"river".equals(street))
            return fallback;

        double bfs = clampBluffFreqScale(BLUFF_FREQ_SCALE.getOrDefault(nk(name), 1.0));

        // 样本少时，提高探索概率（避免早期被噪声锁死）
        int totalFaced = 0;
        for (SizeBucket b : SizeBucket.values()) {
            BucketKey k = new BucketKey(name, street, b);
            totalFaced += FACED.getOrDefault(k, 0);
        }
        double eps = epsilon;
        if (totalFaced < 4)
            eps = Math.max(eps, 0.30);
        else if (totalFaced < 8)
            eps = Math.max(eps, 0.20);

        if (eps > 0 && random.nextDouble() < eps) {
            // explore：随机挑一个桶（均匀）
            int idx = random.nextInt(SizeBucket.values().length);
            return factorForBucket(SizeBucket.values()[idx]);
        }

        // exploit：挑“平均 reward”最高的桶（更像在试探边界并追求收益）
        SizeBucket best = null;
        double bestScore = -1;
        for (SizeBucket b : SizeBucket.values()) {
            BucketKey k = new BucketKey(name, street, b);
            int cnt = REWARD_CNT.getOrDefault(k, 0);
            double sum = REWARD_SUM.getOrDefault(k, 0.0);
            if (cnt <= 0)
                continue;
            // 平滑：给一个轻微的先验（偏向中注），避免极少样本锁死
            double priorMean = (b == SizeBucket.MEDIUM) ? 0.10 : 0.0;
            int priorN = 2;
            double score = (sum + priorMean * priorN) / (cnt + priorN);
            if (score > bestScore) {
                bestScore = score;
                best = b;
            }
        }
        if (best == null)
            return fallback;
        return factorForBucket(best);
    }

    private static double factorForBucket(SizeBucket b) {
        return switch (b) {
            case SMALL -> 0.33;
            case MEDIUM -> 0.55;
            case BIG -> 0.80;
        };
    }

    private static double lerpBoost(double old, double target, double lr) {
        double next = old + (target - old) * lr;
        return clampBluffFireBoost(next);
    }

    private static double lerpValueBoost(double old, double target, double lr) {
        double next = old + (target - old) * lr;
        return clampValueRaiseBoost(next);
    }

    private static SizeBucket parseBucketName(String raw) {
        if (raw == null) {
            return SizeBucket.MEDIUM;
        }
        try {
            return SizeBucket.valueOf(raw.trim().toUpperCase());
        } catch (Exception ignored) {
            return SizeBucket.MEDIUM;
        }
    }

    /** 清除某对手在内存中的学习旋钮（用于导入前覆盖）。 */
    static void wipeLearnedForVillain(String villain) {
        String v = nk(villain);
        FOLD_ADJ.remove(v);
        BLUFF_CATCH_BOOST.remove(v);
        SIZING_FACTOR.remove(v);
        SHOVE_FREQ.remove(v);
        BLUFF_FIRE_BOOST_FLOP.remove(v);
        BLUFF_FIRE_BOOST_TURN.remove(v);
        BLUFF_FIRE_BOOST_RIVER.remove(v);
        VALUE_RAISE_BOOST_FLOP.remove(v);
        VALUE_RAISE_BOOST_TURN.remove(v);
        VALUE_RAISE_BOOST_RIVER.remove(v);
        BLUFF_FREQ_SCALE.remove(v);
        FACED.keySet().removeIf(k -> v.equals(k.villain));
        FOLDED.keySet().removeIf(k -> v.equals(k.villain));
        REWARD_SUM.keySet().removeIf(k -> v.equals(k.villain));
        REWARD_CNT.keySet().removeIf(k -> v.equals(k.villain));
    }

    static LearnedPersistSnapshot exportLearnedSnapshot(String villain) {
        String v = nk(villain);
        LearnedPersistSnapshot s = new LearnedPersistSnapshot();
        s.foldAdj = FOLD_ADJ.getOrDefault(v, 0.0);
        s.bluffCatch = BLUFF_CATCH_BOOST.getOrDefault(v, 0.0);
        s.sizing = SIZING_FACTOR.getOrDefault(v, 1.0);
        s.shoveFreq = SHOVE_FREQ.getOrDefault(v, 0.0);
        s.bluffFireFlop = BLUFF_FIRE_BOOST_FLOP.getOrDefault(v, 0.0);
        s.bluffFireTurn = BLUFF_FIRE_BOOST_TURN.getOrDefault(v, 0.0);
        s.bluffFireRiver = BLUFF_FIRE_BOOST_RIVER.getOrDefault(v, 0.0);
        s.valueRaiseFlop = VALUE_RAISE_BOOST_FLOP.getOrDefault(v, 0.0);
        s.valueRaiseTurn = VALUE_RAISE_BOOST_TURN.getOrDefault(v, 0.0);
        s.valueRaiseRiver = VALUE_RAISE_BOOST_RIVER.getOrDefault(v, 0.0);
        s.bluffFreqScale = BLUFF_FREQ_SCALE.getOrDefault(v, 1.0);
        for (java.util.Map.Entry<BucketKey, Integer> e : FACED.entrySet()) {
            BucketKey bk = e.getKey();
            if (!v.equals(bk.villain)) {
                continue;
            }
            LearnedPersistSnapshot.BucketPersist row = new LearnedPersistSnapshot.BucketPersist();
            row.street = bk.street;
            row.bucket = bk.bucket.name();
            row.faced = e.getValue() != null ? e.getValue() : 0;
            row.folded = FOLDED.getOrDefault(bk, 0);
            row.rewardSum = REWARD_SUM.getOrDefault(bk, 0.0);
            row.rewardCnt = REWARD_CNT.getOrDefault(bk, 0);
            s.buckets.add(row);
        }
        return s;
    }

    static void importLearnedSnapshot(String villain, LearnedPersistSnapshot snap) {
        String v = nk(villain);
        wipeLearnedForVillain(v);
        if (snap == null) {
            return;
        }
        FOLD_ADJ.put(v, snap.foldAdj);
        BLUFF_CATCH_BOOST.put(v, snap.bluffCatch);
        SIZING_FACTOR.put(v, snap.sizing);
        SHOVE_FREQ.put(v, snap.shoveFreq);
        BLUFF_FIRE_BOOST_FLOP.put(v, snap.bluffFireFlop);
        BLUFF_FIRE_BOOST_TURN.put(v, snap.bluffFireTurn);
        BLUFF_FIRE_BOOST_RIVER.put(v, snap.bluffFireRiver);
        VALUE_RAISE_BOOST_FLOP.put(v, snap.valueRaiseFlop);
        VALUE_RAISE_BOOST_TURN.put(v, snap.valueRaiseTurn);
        VALUE_RAISE_BOOST_RIVER.put(v, snap.valueRaiseRiver);
        double impBfs = snap.bluffFreqScale;
        if (impBfs <= 0 || impBfs > 1.0 || Double.isNaN(impBfs)) {
            impBfs = 1.0;
        }
        BLUFF_FREQ_SCALE.put(v, clampBluffFreqScale(impBfs));
        if (snap.buckets == null) {
            return;
        }
        for (LearnedPersistSnapshot.BucketPersist b : snap.buckets) {
            if (b == null) {
                continue;
            }
            SizeBucket sb = parseBucketName(b.bucket);
            BucketKey k = new BucketKey(v, b.street, sb);
            if (b.faced > 0) {
                FACED.put(k, b.faced);
            }
            if (b.folded > 0) {
                FOLDED.put(k, b.folded);
            }
            if (b.rewardCnt > 0) {
                REWARD_CNT.put(k, b.rewardCnt);
                REWARD_SUM.put(k, b.rewardSum);
            }
        }
    }
}
