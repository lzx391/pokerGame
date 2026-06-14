package com.example.mgdemoplus.npc.strategypro.postflop.call;

import com.example.mgdemoplus.npc.engine.DpNpcEngine;
import com.example.mgdemoplus.npc.engine.DpNpcEngine.BoardDanger;
import com.example.mgdemoplus.npc.engine.DpNpcEngine.BotAction;
import com.example.mgdemoplus.npc.engine.DpNpcEngine.BotActionType;
import com.example.mgdemoplus.npc.engine.DpNpcEngine.BotType;
import com.example.mgdemoplus.npc.engine.DpNpcEngine.HandPlanType;
import com.example.mgdemoplus.npc.eval.DpBoardTexture;
import com.example.mgdemoplus.npc.eval.DpNpcDrawCategory;
import com.example.mgdemoplus.npc.eval.DpNpcMadeHandCategory;
import com.example.mgdemoplus.npc.eval.DpNpcPostflopFormula;
import com.example.mgdemoplus.npc.strategypro.DpNpcRuleDecisionParams;
import com.example.mgdemoplus.npc.strategypro.l1.DpNpcHardConstraints;
import com.example.mgdemoplus.npc.trace.DpNpcPostflopTraceHooks;
import com.example.mgdemoplus.utils.DpUtilSmartContext;

/**
 * CALL 独立翻后策略：纯跟注站，宽 call、听牌不弃、从不加注（§4.2）。
 * <p>禁止套壳旧 PassiveStation 聚合决策类。</p>
 */
public final class DpNpcCallPostflopStrategy {

    /** 比 FISH（0.82）更松 — 越小越不弃 */
    private static final double CALL_FOLD_TIGHTNESS = 0.55;

    private DpNpcCallPostflopStrategy() {
    }

    public static BotAction decide(DpNpcRuleDecisionParams p) {
        BoardDanger bd = p.boardDanger;
        String stage = p.room.getCurrentStage();
        int callAmount = Math.max(0, p.room.getCurrentBetToCall() - p.bot.getBet());
        DpUtilSmartContext ctx = DpNpcEngine.buildSmartContext(
                p.room, p.bot, p.handSnapshot, stage, callAmount, p.random);

        DpNpcEngine.initHandPlanIfNeededForPostflop(
                p.room, p.bot, BotType.CALL, p.handSnapshot, bd, p.position, ctx, p.random);
        DpNpcEngine.updateHandPlanForLaterStreetIfNeeded(
                p.room, p.bot, BotType.CALL, p.handSnapshot, bd, ctx);

        DpNpcMadeHandCategory made = DpNpcPostflopFormula.madeOrHigh(p.handSnapshot);
        DpNpcDrawCategory draw = DpNpcPostflopFormula.drawOrNone(p.handSnapshot);
        DpBoardTexture tex = DpNpcPostflopFormula.textureOrDry(p.handSnapshot);
        HandPlanType plan = DpNpcEngine.getHandPlanType(p.bot);

        DpNpcPostflopTraceHooks.postflopContext(
                stage, callAmount, plan, made, draw, tex, bd, p, ctx, 0.0, 0.0);

        if (callAmount > 0) {
            BotAction foldAction = tryFoldFacingBet(p, stage, callAmount, made, draw, tex, plan, ctx);
            if (foldAction != null) {
                return foldAction;
            }
            DpNpcPostflopTraceHooks.postflopAction("CALL_OR_CHECK", "跟注站默认跟注");
            return new BotAction(BotActionType.CALL_OR_CHECK, 0);
        }
        DpNpcPostflopTraceHooks.postflopAction("CALL_OR_CHECK", "无下注线 check");
        // §4.2：0% cbet，永远 check，无诈唬
        return new BotAction(BotActionType.CALL_OR_CHECK, 0);
    }

    private static BotAction tryFoldFacingBet(
            DpNpcRuleDecisionParams p,
            String stage,
            int callAmount,
            DpNpcMadeHandCategory made,
            DpNpcDrawCategory draw,
            DpBoardTexture tex,
            HandPlanType plan,
            DpUtilSmartContext ctx) {
        if (DpNpcHardConstraints.mustNotFold(
                made,
                p.handSnapshot != null ? p.handSnapshot.handStrength : null,
                callAmount,
                ctx.equityEst,
                callAmount >= p.chips)) {
            return null;
        }

        // §4.2：任意听牌不弃
        if (draw != DpNpcDrawCategory.NONE) {
            return null;
        }

        double callRatio = p.chips == 0 ? 1.0 : (callAmount * 1.0 / p.chips);

        // §4.2：任意对子跟注；底对仅面对超大注有极低弃牌率
        if (made != DpNpcMadeHandCategory.HIGH_CARD) {
            if (made == DpNpcMadeHandCategory.BOTTOM_PAIR && callRatio >= 0.72) {
                double foldProb = 0.06 + 0.10 * Math.min(1.0, (callRatio - 0.72) / 0.28);
                foldProb = applyCallStationShrink(foldProb, p.callStation);
                foldProb = DpNpcHardConstraints.capFoldProb(
                        foldProb,
                        made,
                        p.handSnapshot != null ? p.handSnapshot.handStrength : null,
                        callAmount,
                        ctx.equityEst,
                        callAmount >= p.chips);
                if (foldProb > 0 && p.random.nextDouble() < foldProb) {
                    DpNpcPostflopTraceHooks.foldRollHit(foldProb, foldProb);
                    return new BotAction(BotActionType.FOLD, 0);
                }
                DpNpcPostflopTraceHooks.foldRollMiss(foldProb, foldProb, plan);
            }
            return null;
        }

        // 高牌 + 大注 + 无听牌：唯一常规弃牌场景
        double baseFold = DpNpcPostflopFormula.baseFoldProb(
                made, draw, tex, callRatio, p.boardDanger, CALL_FOLD_TIGHTNESS);
        baseFold = Math.min(1.0, baseFold + DpNpcHardConstraints.multiwayFoldBoost(
                made, ctx.activeVillains, p.checkRaiseFear, callRatio));
        baseFold = DpNpcEngine.adjustFoldProbForEquityVsPotOdds(
                baseFold, callAmount, ctx.potOdds, ctx.equityEst, ctx.activeVillains, 0.72);

        if (callRatio > 0 && callRatio < 0.32) {
            baseFold *= 0.38;
        } else if (callRatio >= 0.50) {
            baseFold = Math.min(1.0, baseFold + 0.22);
        }
        if (ctx.potOdds > 0.55) {
            baseFold = Math.min(1.0, baseFold + 0.12);
        }
        if (plan == HandPlanType.GIVE_UP) {
            double beforeGiveUp = baseFold;
            baseFold = Math.min(1.0, baseFold + 0.08);
            DpNpcPostflopTraceHooks.giveUpFoldBoost(plan, made, beforeGiveUp, baseFold, 0.08);
        }

        double foldProb = applyCallStationShrink(baseFold, p.callStation);
        foldProb = DpNpcHardConstraints.capFoldProb(
                foldProb,
                made,
                p.handSnapshot != null ? p.handSnapshot.handStrength : null,
                callAmount,
                ctx.equityEst,
                callAmount >= p.chips);
        if (foldProb > 0 && p.random.nextDouble() < foldProb) {
            if (DpNpcHardConstraints.mustNotFold(
                    made,
                    p.handSnapshot != null ? p.handSnapshot.handStrength : null,
                    callAmount,
                    ctx.equityEst,
                    callAmount >= p.chips)) {
                return null;
            }
            DpNpcPostflopTraceHooks.foldRollHit(baseFold, foldProb);
            return new BotAction(BotActionType.FOLD, 0);
        }
        DpNpcPostflopTraceHooks.foldRollMiss(baseFold, foldProb, plan);
        return null;
    }

    private static double applyCallStationShrink(double foldProb, double callStation) {
        return Math.min(1.0, Math.max(0.0, foldProb * (1.0 - 0.72 * callStation)));
    }
}
