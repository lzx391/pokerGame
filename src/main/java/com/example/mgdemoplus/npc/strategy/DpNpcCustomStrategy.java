package com.example.mgdemoplus.npc.strategy;

import static com.example.mgdemoplus.utils.DpUtilHandEvaluator.SimpleStrength;

import com.example.mgdemoplus.npc.CustomNpcStyleSnapshot;
import com.example.mgdemoplus.npc.engine.DpNpcEngine;
import com.example.mgdemoplus.npc.engine.DpNpcEngine.ActionCredibility;
import com.example.mgdemoplus.npc.engine.DpNpcEngine.BoardDanger;
import com.example.mgdemoplus.npc.engine.DpNpcEngine.BotAction;
import com.example.mgdemoplus.npc.engine.DpNpcEngine.BotActionType;
import com.example.mgdemoplus.npc.engine.DpNpcEngine.HandPlanType;
import com.example.mgdemoplus.utils.DpUtilSmartContext;

/**
 * 自定义 NPC 翻后策略：六维参数对 fold/call/raise 的影响强于六档预设分支。
 */
public final class DpNpcCustomStrategy {
    private DpNpcCustomStrategy() {
    }

    public static BotAction decide(DpNpcRuleDecisionParams p) {
        BoardDanger bd = p.boardDanger;
        SimpleStrength st = p.strength;
        String stage = p.room.getCurrentStage();
        int callAmount = Math.max(0, p.room.getCurrentBetToCall() - p.bot.getBet());
        double callRatio = p.chips == 0 ? 1.0 : (callAmount * 1.0 / p.chips);
        DpUtilSmartContext ctx = DpNpcEngine.buildSmartContext(p.room, p.bot, st, stage, callAmount, p.random);

        double vpip = p.bot.getNpcStyleVpip();
        double pfr = p.bot.getNpcStylePfr();
        double cbetFreq = p.bot.getNpcStyleCbetFreq();
        double bluffFreq = p.bot.getNpcStyleBluffFreq();
        double callStation = p.callStation;
        double foldToPressure = p.checkRaiseFear;
        double aggression = CustomNpcStyleSnapshot.clamp01(0.5 * (pfr + cbetFreq));
        double raiseScale = 0.6 + 0.9 * aggression;

        double baseFold = 0.0;
        if (st == SimpleStrength.WEAK) {
            if (callRatio > 0.5) {
                baseFold = (bd == BoardDanger.WET) ? 0.72 : 0.52;
            } else if (bd == BoardDanger.WET) {
                baseFold = 0.32;
            }
        } else if (st == SimpleStrength.MEDIUM && callRatio > 0.55 && bd == BoardDanger.WET) {
            baseFold = 0.28;
        }

        baseFold = Math.min(1.0, baseFold + 0.25 * foldToPressure);
        baseFold *= (1.0 - 0.55 * callStation);
        baseFold = Math.max(0.0, Math.min(1.0, baseFold));

        baseFold = Math.min(
                1.0,
                baseFold + DpNpcEngine.multiwayFoldProbBoost(ctx.activeVillains, foldToPressure));

        baseFold = DpNpcEngine.adjustFoldProbForEquityVsPotOdds(
                baseFold,
                callAmount,
                ctx.potOdds,
                ctx.equityEst,
                ctx.activeVillains,
                1.0);

        if (callAmount <= 0) {
            double leadProb = 0.12 + 0.40 * cbetFreq;
            if (st == SimpleStrength.STRONG || st == SimpleStrength.MONSTER) {
                leadProb += 0.25;
            } else if (st == SimpleStrength.WEAK) {
                leadProb += Math.min(0.85, 0.35 * bluffFreq);
            } else if (st == SimpleStrength.MEDIUM) {
                leadProb += 0.08 * vpip;
            }
            leadProb = clamp01(leadProb);
            if (p.random.nextDouble() < leadProb) {
                double sizeFactor = (0.75 + 0.5 * cbetFreq) * (0.85 + 0.25 * aggression);
                int pot = Math.max(p.room.getPot(), p.room.getBigBlindChips());
                int bet = (int) Math.round(pot * sizeFactor * 0.33);
                bet = Math.max(p.room.getBigBlindChips(), bet);
                if (bet >= p.chips) {
                    return new BotAction(BotActionType.ALL_IN, p.chips);
                }
                return new BotAction(BotActionType.RAISE, bet);
            }
            return new BotAction(BotActionType.CALL_OR_CHECK, 0);
        }

        double foldProb = baseFold;
        if (p.random.nextDouble() < foldProb) {
            return new BotAction(BotActionType.FOLD, 0);
        }

        double raiseProb = 0.0;
        if (st == SimpleStrength.MONSTER || st == SimpleStrength.STRONG) {
            raiseProb = 0.35 + 0.45 * aggression;
        } else if (st == SimpleStrength.MEDIUM) {
            raiseProb = 0.08 + 0.22 * aggression;
        } else {
            raiseProb = Math.min(0.85, 0.35 * bluffFreq);
        }
        raiseProb *= raiseScale;
        raiseProb = clamp01(raiseProb);

        HandPlanType plan = DpNpcEngine.getHandPlanType(p.bot);
        if (plan == HandPlanType.GIVE_UP) {
            raiseProb *= 0.35;
        }

        ActionCredibility cred = ctx.credibility;
        if (cred == ActionCredibility.HIGH && st == SimpleStrength.WEAK) {
            raiseProb *= 0.5;
        } else if (cred == ActionCredibility.LOW && st != SimpleStrength.MONSTER) {
            raiseProb = Math.min(0.9, raiseProb * 1.15);
        }

        if (p.random.nextDouble() < raiseProb && callAmount > 0) {
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
        if (v < 0.0) {
            return 0.0;
        }
        if (v > 1.0) {
            return 1.0;
        }
        return v;
    }
}
