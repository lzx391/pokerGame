package com.example.mgdemoplus.npc.strategypro.l4;

import com.example.mgdemoplus.npc.engine.DpNpcEngine.ActionCredibility;
import com.example.mgdemoplus.npc.engine.DpNpcEngine.BotType;
import com.example.mgdemoplus.npc.eval.DpNpcDrawCategory;
import com.example.mgdemoplus.npc.eval.DpNpcMadeHandCategory;
import com.example.mgdemoplus.npc.eval.DpNpcPostflopFormula;
import com.example.mgdemoplus.npc.strategypro.DpNpcRuleDecisionParams;
import com.example.mgdemoplus.utils.DpUtilSmartContext;

/**
 * L4 剥削层：概率弱牌抓诈（hero call）。在 tryFoldFacingBet 掷 fold 骰之前调用。
 */
public final class DpNpcHeroCall {

    private static final double MAX_BET_TO_POT = 0.55;
    private static final double BIG_BET_TO_POT = 0.65;

    private DpNpcHeroCall() {
    }

    /**
     * @return true 表示命中 hero call，调用方应返回 null（继续 call）而非 fold
     */
    public static boolean shouldHeroCall(
            DpNpcRuleDecisionParams p,
            String stage,
            int callAmount,
            DpNpcMadeHandCategory made,
            DpNpcDrawCategory draw,
            DpUtilSmartContext ctx) {
        double prob = heroCallProbability(p, stage, callAmount, made, draw, ctx);
        return prob > 0 && p.random.nextDouble() < prob;
    }

    /** 包内测试用：返回 hero call 概率，不满足前提时返回 0。 */
    static double heroCallProbability(
            DpNpcRuleDecisionParams p,
            String stage,
            int callAmount,
            DpNpcMadeHandCategory made,
            DpNpcDrawCategory draw,
            DpUtilSmartContext ctx) {
        if (p == null || p.type == BotType.CALL || ctx == null) {
            return 0.0;
        }
        if (callAmount <= 0 || callAmount >= p.chips) {
            return 0.0;
        }
        if (ctx.activeVillains >= 3) {
            return 0.0;
        }

        int pot = Math.max(1, p.room.getPot());
        double betToPot = callAmount * 1.0 / pot;
        if (betToPot > BIG_BET_TO_POT || betToPot > MAX_BET_TO_POT) {
            return 0.0;
        }

        boolean weakHand = (made == DpNpcMadeHandCategory.HIGH_CARD
                && !DpNpcPostflopFormula.hasSemiBluffDraw(draw))
                || ctx.equityEst < 0.25;
        if (!weakHand) {
            return 0.0;
        }

        double stageMul = stageMultiplier(stage);
        if (stageMul <= 0) {
            return 0.0;
        }

        double prob = baseProb(p.type, p.bluffFrequency) * stageMul;
        prob = applyProfileBonus(prob, ctx);
        return Math.min(0.35, Math.max(0.0, prob));
    }

    private static double stageMultiplier(String stage) {
        if ("river".equals(stage)) {
            return 1.0;
        }
        if ("turn".equals(stage)) {
            return 0.35;
        }
        if ("flop".equals(stage)) {
            return 0.15;
        }
        return 0.0;
    }

    private static double baseProb(BotType type, double bluffFrequency) {
        if (type == null) {
            return 0.02 + Math.min(1.0, Math.max(0.0, bluffFrequency)) * 0.12;
        }
        return switch (type) {
            case TAG -> 0.04;
            case LAG -> 0.10;
            case NIT -> 0.02;
            case FISH -> 0.06;
            case MANIAC -> 0.12;
            case CALL -> 0.0;
        };
    }

    private static double applyProfileBonus(double prob, DpUtilSmartContext ctx) {
        if (ctx.credibility == ActionCredibility.LOW || ctx.showdownBluffiness > 0.4) {
            prob *= 1.5;
        }
        if (ctx.counterStrategy != null && ctx.counterStrategy.bluffCatchMore) {
            prob *= 1.8;
        }
        return prob;
    }
}
