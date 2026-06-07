package com.example.mgdemoplus.npc.strategy;

import com.example.mgdemoplus.npc.engine.DpNpcEngine;
import com.example.mgdemoplus.npc.engine.DpNpcEngine.ActionCredibility;
import com.example.mgdemoplus.npc.engine.DpNpcEngine.BoardDanger;
import com.example.mgdemoplus.npc.engine.DpNpcEngine.BotAction;
import com.example.mgdemoplus.npc.engine.DpNpcEngine.BotActionType;
import com.example.mgdemoplus.npc.engine.DpNpcEngine.HandPlanType;
import com.example.mgdemoplus.npc.eval.DpNpcDrawCategory;
import com.example.mgdemoplus.npc.eval.DpNpcMadeHandCategory;
import com.example.mgdemoplus.npc.eval.DpNpcPostflopFormula;
import com.example.mgdemoplus.utils.DpUtilSmartContext;

/**
 * 自定义 NPC 翻后：六维参数 × 12 档公式化分支。
 */
public final class DpNpcCustomStrategy {
    private DpNpcCustomStrategy() {
    }

    public static BotAction decide(DpNpcRuleDecisionParams p) {
        BoardDanger bd = p.boardDanger;
        String stage = p.room.getCurrentStage();
        int callAmount = Math.max(0, p.room.getCurrentBetToCall() - p.bot.getBet());
        DpUtilSmartContext ctx = DpNpcEngine.buildSmartContext(
                p.room, p.bot, p.handSnapshot, stage, callAmount, p.random);

        double cbetFreq = p.bot.getNpcStyleCbetFreq();
        double bluffFreq = p.bot.getNpcStyleBluffFreq();
        double vpip = p.bot.getNpcStyleVpip();
        double aggression = com.example.mgdemoplus.npc.CustomNpcStyleSnapshot.clamp01(
                0.5 * (p.bot.getNpcStylePfr() + cbetFreq));
        double raiseScale = 0.6 + 0.9 * aggression;

        DpNpcMadeHandCategory made = DpNpcPostflopFormula.madeOrHigh(p.handSnapshot);
        DpNpcDrawCategory draw = DpNpcPostflopFormula.drawOrNone(p.handSnapshot);

        double baseFold = DpNpcPostflopFormula.baseFoldProb(
                made, draw, DpNpcPostflopFormula.textureOrDry(p.handSnapshot),
                p.chips == 0 ? 1.0 : (callAmount * 1.0 / p.chips), bd, 1.0);
        baseFold = Math.min(1.0, baseFold + 0.25 * p.checkRaiseFear);
        baseFold *= (1.0 - 0.55 * p.callStation);
        baseFold = Math.min(1.0, baseFold + DpNpcEngine.multiwayFoldProbBoost(ctx.activeVillains, p.checkRaiseFear));
        baseFold = DpNpcEngine.adjustFoldProbForEquityVsPotOdds(
                baseFold, callAmount, ctx.potOdds, ctx.equityEst, ctx.activeVillains, 1.0);
        baseFold = Math.max(0.0, baseFold - DpNpcPostflopFormula.drawCallFoldReduction(draw, ctx.potOdds) * vpip);

        if (callAmount <= 0) {
            double leadProb = 0.12 + 0.40 * cbetFreq;
            if (made.isAtLeast(DpNpcMadeHandCategory.TRIPS)) {
                leadProb += 0.28;
            } else if (made == DpNpcMadeHandCategory.HIGH_CARD) {
                leadProb += Math.min(0.85, 0.35 * bluffFreq + DpNpcPostflopFormula.semiBluffProb(made, draw, 1.0));
            } else if (made.isAtLeast(DpNpcMadeHandCategory.TOP_PAIR_WEAK_KICKER)) {
                leadProb += 0.08 * vpip;
            }
            leadProb = clamp01(leadProb);
            if (p.random.nextDouble() < leadProb) {
                double sizeFactor = DpNpcPostflopFormula.cbetPotFactor(
                        made, DpNpcPostflopFormula.textureOrDry(p.handSnapshot), stage);
                sizeFactor *= (0.75 + 0.5 * cbetFreq) * (0.85 + 0.25 * aggression);
                int pot = Math.max(p.room.getPot(), p.room.getBigBlindChips());
                int bet = (int) Math.round(pot * sizeFactor);
                bet = Math.max(p.room.getBigBlindChips(), bet);
                if (bet >= p.chips) {
                    return new BotAction(BotActionType.ALL_IN, p.chips);
                }
                return new BotAction(BotActionType.RAISE, bet);
            }
            return new BotAction(BotActionType.CALL_OR_CHECK, 0);
        }

        if (p.random.nextDouble() < clamp01(baseFold)) {
            return new BotAction(BotActionType.FOLD, 0);
        }

        double raiseProb = DpNpcPostflopFormula.raiseProb(
                made, draw, DpNpcPostflopFormula.textureOrDry(p.handSnapshot),
                DpNpcEngine.getHandPlanType(p.bot));
        if (made == DpNpcMadeHandCategory.HIGH_CARD) {
            raiseProb = Math.max(raiseProb, Math.min(0.85, 0.35 * bluffFreq));
        }
        raiseProb *= raiseScale;
        HandPlanType plan = DpNpcEngine.getHandPlanType(p.bot);
        if (plan == HandPlanType.GIVE_UP) {
            raiseProb *= 0.35;
        }
        ActionCredibility cred = ctx.credibility;
        if (cred == ActionCredibility.HIGH && made == DpNpcMadeHandCategory.HIGH_CARD) {
            raiseProb *= 0.5;
        } else if (cred == ActionCredibility.LOW && !DpNpcPostflopFormula.isMonster(made)) {
            raiseProb = Math.min(0.9, raiseProb * 1.15);
        }
        if (p.random.nextDouble() < clamp01(raiseProb) && callAmount > 0) {
            int pot = Math.max(p.room.getPot(), p.room.getBigBlindChips());
            double mult = (0.75 + 0.5 * cbetFreq) * (1.0 + 0.35 * aggression);
            int raise = (int) Math.round((callAmount + pot * 0.4) * mult);
            raise = Math.max(callAmount + p.room.getBigBlindChips(), raise);
            if (raise >= p.chips) {
                return new BotAction(BotActionType.ALL_IN, p.chips);
            }
            return new BotAction(BotActionType.RAISE, raise);
        }
        return new BotAction(BotActionType.CALL_OR_CHECK, 0);
    }

    private static double clamp01(double v) {
        return Math.max(0.0, Math.min(1.0, v));
    }
}
