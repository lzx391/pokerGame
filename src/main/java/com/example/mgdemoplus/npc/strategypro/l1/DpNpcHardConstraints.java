package com.example.mgdemoplus.npc.strategypro.l1;

import com.example.mgdemoplus.npc.engine.DpNpcEngine;
import com.example.mgdemoplus.npc.engine.DpNpcEngine.BotAction;
import com.example.mgdemoplus.npc.engine.DpNpcEngine.BotActionType;
import com.example.mgdemoplus.npc.engine.DpNpcEngine.RuleNpcConfig;
import com.example.mgdemoplus.npc.eval.DpNpcEquityEstimator;
import com.example.mgdemoplus.npc.eval.DpNpcHandSnapshot;
import com.example.mgdemoplus.npc.eval.DpNpcMadeHandCategory;
import com.example.mgdemoplus.npc.eval.DpNpcPostflopFormula;
import com.example.mgdemoplus.npc.strategypro.facade.DpNpcDecisionContext;
import com.example.mgdemoplus.npc.trace.DpNpcTagDecisionTraceCollector;
import com.example.mgdemoplus.utils.DpUtilHandEvaluator.HandStrength;

/**
 * Wave 2b：L1 数学硬约束层。优先级高于一切 L2–L5 概率/性格偏移。
 */
public final class DpNpcHardConstraints {

    private DpNpcHardConstraints() {
    }

    /**
     * 全局开关；设为 {@code false} 可整体回滚 L1 层。
     */
    public static boolean HARD_CONSTRAINTS_ENABLED = RuleNpcConfig.HARD_CONSTRAINTS_ENABLED;

    /**
     * 是否绝对禁止弃牌（fold 概率必须为 0）。
     */
    public static boolean mustNotFold(
            DpNpcMadeHandCategory made,
            HandStrength hs,
            int callAmount,
            double equityEst,
            boolean facingAllIn) {
        if (!HARD_CONSTRAINTS_ENABLED) {
            return false;
        }
        if (callAmount <= 0) {
            return true;
        }
        if (isNutHand(made, hs)) {
            return true;
        }
        if (!facingAllIn
                && made != null
                && made.isAtLeast(DpNpcMadeHandCategory.TRIPS)
                && equityEst >= 0.55) {
            return true;
        }
        return false;
    }

    /**
     * 将弃牌概率压到 L1 允许的上限（坚果/免费看牌 → 0；TRIPS+ 高权益 → ≤5%）。
     */
    public static double capFoldProb(
            double foldProb,
            DpNpcMadeHandCategory made,
            HandStrength hs,
            int callAmount,
            double equityEst,
            boolean facingAllIn) {
        if (!HARD_CONSTRAINTS_ENABLED) {
            return foldProb;
        }
        if (mustNotFold(made, hs, callAmount, equityEst, facingAllIn)) {
            return 0.0;
        }
        if (!facingAllIn
                && made != null
                && made.isAtLeast(DpNpcMadeHandCategory.TRIPS)
                && equityEst >= 0.55) {
            return Math.min(foldProb, 0.05);
        }
        if (callAmount <= 0) {
            return 0.0;
        }
        return foldProb;
    }

    /**
     * 公牌平分坚果场景：共享皇家/同花顺等不应被 0.22 权益上限逼弃。
     */
    public static double adjustEquityForPlayingTheBoard(
            DpNpcHandSnapshot snap,
            double equityEst,
            int activeVillains) {
        if (!HARD_CONSTRAINTS_ENABLED || snap == null || !snap.playingTheBoard) {
            return equityEst;
        }
        HandStrength hs = snap.handStrength;
        if (hs != null && hs.rankCategory >= 9) {
            int activePlayers = Math.max(2, activeVillains + 1);
            return DpNpcEquityEstimator.clampEquityEstimate(1.0 / activePlayers);
        }
        if (hs != null && hs.rankCategory >= 7) {
            int activePlayers = Math.max(2, activeVillains + 1);
            return DpNpcEquityEstimator.clampEquityEstimate(0.85 / activePlayers);
        }
        return Math.min(equityEst, 0.22);
    }

    /**
     * 多人池弃牌加成：弱牌 / 高牌 facing 大注时显著收紧跟注。
     */
    public static double multiwayFoldBoost(
            DpNpcMadeHandCategory made,
            int activeVillains,
            double foldToPressure,
            double callRatio) {
        double boost = DpNpcEngine.multiwayFoldProbBoost(activeVillains, foldToPressure);
        if (!HARD_CONSTRAINTS_ENABLED) {
            return boost;
        }
        DpNpcMadeHandCategory m = made != null ? made : DpNpcMadeHandCategory.HIGH_CARD;
        if (activeVillains >= 2) {
            if (m == DpNpcMadeHandCategory.HIGH_CARD) {
                if (callRatio >= 0.35) {
                    boost += 0.10 + 0.04 * Math.min(1.0, (activeVillains - 1) / 2.0);
                }
                if (callRatio >= 0.55) {
                    boost += 0.06;
                }
            } else if (m == DpNpcMadeHandCategory.BOTTOM_PAIR || m == DpNpcMadeHandCategory.MIDDLE_PAIR) {
                if (callRatio >= 0.40) {
                    boost += 0.08 + 0.03 * Math.min(1.0, (activeVillains - 1) / 2.0);
                }
            } else if (m == DpNpcMadeHandCategory.TOP_PAIR_WEAK_KICKER && callRatio >= 0.50) {
                boost += 0.07;
            }
        }
        if (m == DpNpcMadeHandCategory.TOP_PAIR_TOP_KICKER && callRatio >= 0.35) {
            boost += 0.06 + 0.11 * Math.min(2.0, activeVillains);
            if (activeVillains >= 2 && callRatio >= 0.55) {
                boost += 0.05;
            }
        }
        return Math.min(0.45, boost);
    }

    /**
     * Facade 统一拦截：任何 postflop 决策返回 FOLD 前过 L1 守卫。
     */
    public static BotAction applyOrOverride(DpNpcDecisionContext ctx, BotAction action) {
        if (!HARD_CONSTRAINTS_ENABLED || action == null || action.getType() != BotActionType.FOLD) {
            return action;
        }
        if (ctx == null || ctx.params == null) {
            return action;
        }
        DpNpcHandSnapshot snap = ctx.params.handSnapshot;
        DpNpcMadeHandCategory made = DpNpcPostflopFormula.madeOrHigh(snap);
        HandStrength hs = snap != null ? snap.handStrength : null;
        int callAmount = ctx.params.callAmount;
        boolean facingAllIn = callAmount >= ctx.params.chips && ctx.params.chips > 0;
        double equityEst = estimateEquityForGuard(ctx, snap, callAmount);
        if (mustNotFold(made, hs, callAmount, equityEst, facingAllIn)) {
            if (DpNpcTagDecisionTraceCollector.current() != null
                    && ctx.botType == DpNpcEngine.BotType.TAG) {
                DpNpcTagDecisionTraceCollector.step(
                        "L1",
                        "L1_BLOCK_FOLD",
                        "L1 blocked fold → call/check",
                        null);
            }
            return new BotAction(BotActionType.CALL_OR_CHECK, 0);
        }
        return action;
    }

    public static boolean isNutHand(DpNpcMadeHandCategory made, HandStrength hs) {
        if (made != null && made.isAtLeast(DpNpcMadeHandCategory.FULL_HOUSE)) {
            return true;
        }
        if (made == DpNpcMadeHandCategory.ROCKET) {
            return true;
        }
        return hs != null && hs.rankCategory >= 9;
    }

    private static double estimateEquityForGuard(
            DpNpcDecisionContext ctx,
            DpNpcHandSnapshot snap,
            int callAmount) {
        if (snap == null || ctx.params.bot == null) {
            return 0.0;
        }
        String stage = ctx.params.room != null ? ctx.params.room.getCurrentStage() : "";
        double raw = DpNpcEquityEstimator.estimate(snap, stage, ctx.params.bot.getHoleCards());
        int activeVillains = 0;
        if (ctx.params.room != null && ctx.params.bot != null) {
            for (var p : ctx.params.room.getPlayers()) {
                if (p == null || p == ctx.params.bot || p.isFold() || p.isLeftThisHand()) {
                    continue;
                }
                activeVillains++;
            }
        }
        return adjustEquityForPlayingTheBoard(snap, raw, activeVillains);
    }
}
