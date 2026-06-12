package com.example.mgdemoplus.npc.strategypro.l4;

import com.example.mgdemoplus.common.bo.DpRoomBO;
import com.example.mgdemoplus.npc.engine.DpNpcEngine.BotType;
import com.example.mgdemoplus.npc.eval.DpNpcMadeHandCategory;

import java.util.Random;

/**
 * L4 剥削层：翻后 facing bet 加注尺度统一升级，避免同街反复小加注循环。
 * 读取 {@link DpRoomBO#getRaiseLevel()} 与 {@link DpRoomBO#getLastRaiseIncrement()}。
 */
public final class DpNpcRaiseEscalation {

    private static final double POT_ALPHA = 0.45;
    private static final double LAST_RAISE_BETA = 1.15;
    private static final double LEVEL_ESCALATION_BASE = 1.35;
    private static final double ANTI_REPEAT_POT_FRAC = 0.55;
    private static final double DEEP_STACK_TRIPS_BOOST = 1.06;
    private static final double DEEP_STACK_MARGINAL_PENALTY = 0.85;
    private static final double DEEP_STACK_AVG_BB = 80.0;

    private DpNpcRaiseEscalation() {
    }

    public static final class EscalationResult {
        public final int raiseAmount;
        /** true 时调用方应优先 ALL_IN 而非小额 raise */
        public final boolean suggestJam;

        public EscalationResult(int raiseAmount, boolean suggestJam) {
            this.raiseAmount = raiseAmount;
            this.suggestJam = suggestJam;
        }
    }

    /**
     * 统一 facing bet raise 尺度：随 raiseLevel 递增，禁止连续同增量。
     */
    public static EscalationResult computeFacingBetRaise(
            DpRoomBO room,
            int callAmount,
            int chips,
            DpNpcMadeHandCategory made,
            String stage,
            BotType type,
            int extraMinBb,
            int extraMaxBb,
            Random random) {
        if (room == null || callAmount <= 0 || chips <= callAmount) {
            return new EscalationResult(0, false);
        }
        int bb = Math.max(1, room.getBigBlindChips());
        int pot = Math.max(bb, room.getPot());
        int raiseLevel = Math.max(0, room.getRaiseLevel());
        int lastInc = Math.max(0, room.getLastRaiseIncrement());

        int extraBB = extraMinBb + random.nextInt(Math.max(1, extraMaxBb - extraMinBb + 1));
        double levelMul = raiseLevel >= 2 ? Math.pow(LEVEL_ESCALATION_BASE, raiseLevel - 1) : 1.0;

        int fromExtra = extraBB * bb;
        int fromPot = (int) Math.round(pot * POT_ALPHA);
        int fromLast = lastInc > 0 ? (int) Math.round(lastInc * LAST_RAISE_BETA) : bb * 2;
        int baseIncrement = Math.max(fromExtra, Math.max(fromPot, fromLast));
        baseIncrement = (int) Math.round(baseIncrement * levelMul);

        boolean suggestJam = false;
        if (lastInc > 0 && Math.abs(baseIncrement - lastInc) < bb) {
            int potBump = (int) Math.round(pot * ANTI_REPEAT_POT_FRAC);
            baseIncrement = Math.max(baseIncrement, potBump);
            if (raiseLevel >= 3 && (made.isAtLeast(DpNpcMadeHandCategory.TRIPS) || type == BotType.MANIAC)) {
                suggestJam = true;
            }
        }
        if (raiseLevel >= 3 && (made.isAtLeast(DpNpcMadeHandCategory.TRIPS) || type == BotType.MANIAC)) {
            suggestJam = suggestJam || random.nextDouble() < jamBoostOnHighReRaise(raiseLevel, made, type);
        }

        int target = callAmount + baseIncrement;
        if (!"river".equals(stage)
                && made.ordinal() <= DpNpcMadeHandCategory.TOP_PAIR_WEAK_KICKER.ordinal()
                && made.ordinal() >= DpNpcMadeHandCategory.MIDDLE_PAIR.ordinal()) {
            target = (int) Math.round(target * 0.8);
        }
        int raiseAmount = Math.min(chips, target);
        raiseAmount = enforceMinRaise(callAmount, raiseAmount, made, pot, bb, chips);
        raiseAmount = snapToSmallBlind(room, raiseAmount, chips);

        if (raiseAmount <= callAmount) {
            return new EscalationResult(0, suggestJam);
        }
        return new EscalationResult(raiseAmount, suggestJam);
    }

    /**
     * 深码桌：TRIPS+ 不叠加边际牌惩罚；仅 TWO_PAIR 及以下保持 ×0.85。
     */
    public static double adjustDeepStackCommitFactor(
            double factor,
            DpNpcMadeHandCategory made,
            double avgStackBB) {
        if (avgStackBB < DEEP_STACK_AVG_BB) {
            return factor;
        }
        if (made.isAtLeast(DpNpcMadeHandCategory.TRIPS)) {
            return Math.min(0.95, factor * DEEP_STACK_TRIPS_BOOST);
        }
        if (!made.isAtLeast(DpNpcMadeHandCategory.TWO_PAIR)) {
            return factor * DEEP_STACK_MARGINAL_PENALTY;
        }
        return factor;
    }

    /**
     * MANIAC facing bet 且 raiseLevel≥2 时额外 jam 概率（由 decideFacingBet 调用）。
     */
    public static double maniacReRaiseJamProb(int raiseLevel, DpNpcMadeHandCategory made) {
        if (raiseLevel < 2) {
            return 0.0;
        }
        double prob = 0.18 + 0.08 * Math.min(3, raiseLevel - 1);
        if (made.isAtLeast(DpNpcMadeHandCategory.TRIPS)) {
            prob += 0.12;
        }
        return Math.min(0.55, prob);
    }

    /**
     * 用于测试/文档：给定 raiseLevel 序列，第 N 次增量应大于第 1 次（同参数除 level 外一致）。
     */
    public static int incrementForLevel(
            int raiseLevel,
            int pot,
            int bb,
            int lastInc,
            int extraBb) {
        double levelMul = raiseLevel >= 2 ? Math.pow(LEVEL_ESCALATION_BASE, raiseLevel - 1) : 1.0;
        int fromExtra = extraBb * bb;
        int fromPot = (int) Math.round(pot * POT_ALPHA);
        int fromLast = lastInc > 0 ? (int) Math.round(lastInc * LAST_RAISE_BETA) : bb * 2;
        return (int) Math.round(Math.max(fromExtra, Math.max(fromPot, fromLast)) * levelMul);
    }

    private static double jamBoostOnHighReRaise(
            int raiseLevel,
            DpNpcMadeHandCategory made,
            BotType type) {
        double prob = 0.22 + 0.06 * Math.min(4, raiseLevel - 2);
        if (type == BotType.MANIAC) {
            prob += 0.15;
        }
        if (made.isAtLeast(DpNpcMadeHandCategory.TRIPS)) {
            prob += 0.10;
        }
        return Math.min(0.65, prob);
    }

    private static int enforceMinRaise(
            int callAmount,
            int raiseAmount,
            DpNpcMadeHandCategory made,
            int pot,
            int bb,
            int chips) {
        int minExtraBB = made.isAtLeast(DpNpcMadeHandCategory.TRIPS) ? 4 : 3;
        if (pot > bb * 20) {
            minExtraBB += 1;
        }
        int minRaise = callAmount + minExtraBB * bb;
        if (raiseAmount < minRaise) {
            raiseAmount = Math.min(chips, minRaise);
        }
        return raiseAmount;
    }

    private static int snapToSmallBlind(DpRoomBO room, int raiseAmount, int chips) {
        int sb = room.getSmallBlindChips();
        if (sb <= 0 || raiseAmount <= 0) {
            return raiseAmount;
        }
        int units = Math.max(1, Math.round(raiseAmount * 1.0f / sb));
        raiseAmount = units * sb;
        if (raiseAmount > chips) {
            units = Math.max(1, chips / sb);
            raiseAmount = units * sb;
        }
        return raiseAmount;
    }
}
