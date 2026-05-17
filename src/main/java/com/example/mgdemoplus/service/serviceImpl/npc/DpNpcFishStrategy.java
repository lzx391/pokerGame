package com.example.mgdemoplus.service.serviceImpl.npc;

import static com.example.mgdemoplus.utils.DpUtilHandEvaluator.SimpleStrength;

import com.example.mgdemoplus.service.serviceImpl.DpNpcEngine;
import com.example.mgdemoplus.service.serviceImpl.DpNpcEngine.BotAction;
import com.example.mgdemoplus.service.serviceImpl.DpNpcEngine.BotActionType;
import com.example.mgdemoplus.service.serviceImpl.DpNpcEngine.BotType;
import com.example.mgdemoplus.service.serviceImpl.DpNpcEngine.BoardDanger;
import com.example.mgdemoplus.service.serviceImpl.DpNpcEngine.HandPlanType;
import com.example.mgdemoplus.service.serviceImpl.DpNpcEngine.RuleNpcConfig;
import com.example.mgdemoplus.utils.DpUtilSmartContext;

/**
 * 娱乐鱼（FISH）：翻后独立规则，不与其他风格共用实现类。
 */
public final class DpNpcFishStrategy {
    private DpNpcFishStrategy() {
    }

    public static BotAction decide(DpNpcRuleDecisionParams p) {
        // System.out.println("DpNpcFishStrategy");
        String stage = p.room.getCurrentStage();

        DpUtilSmartContext fishCallCtx = null;
        HandPlanType fishCallPlan = null;
        if (!"preflop".equals(stage)) {
            fishCallCtx = DpNpcEngine.buildSmartContext(p.room, p.bot, p.strength, stage, p.callAmount, p.random);
            DpNpcEngine.initHandPlanIfNeededForPostflop(
                    p.room,
                    p.bot,
                    BotType.FISH,
                    p.strength,
                    p.boardDanger,
                    p.position,
                    fishCallCtx,
                    p.random);
            DpNpcEngine.updateHandPlanForLaterStreetIfNeeded(
                    p.room, p.bot, BotType.FISH, p.strength, p.boardDanger, fishCallCtx);
            fishCallPlan = DpNpcEngine.getHandPlanType(p.bot);
        }

        if (p.callAmount > 0 && !"preflop".equals(stage)) {
            double foldBase = 0.0;
            if (p.strength == SimpleStrength.WEAK && p.callRatio > 0.5) {
                foldBase = 0.6;
                if (p.boardDanger == BoardDanger.WET) {
                    foldBase += 0.2;
                }
            }
            if (fishCallPlan == HandPlanType.GIVE_UP) {
                foldBase = Math.min(1.0, foldBase + 0.11);
            }
            if (fishCallCtx != null) {
                foldBase = Math.min(
                        1.0,
                        foldBase + DpNpcEngine.multiwayFoldProbBoost(
                                fishCallCtx.activeVillains,
                                p.checkRaiseFear));
                foldBase = DpNpcEngine.adjustFoldProbForEquityVsPotOdds(
                        foldBase,
                        p.callAmount,
                        fishCallCtx.potOdds,
                        fishCallCtx.equityEst,
                        fishCallCtx.activeVillains,
                        0.55);
            }
            double foldProb = Math.min(1.0, Math.max(0.0, foldBase + (-p.mood) * 0.2));
            if (foldProb > 0 && p.random.nextDouble() < foldProb) {
                return new BotAction(BotActionType.FOLD, 0);
            }
        }

        double r = p.random.nextDouble();

        double callOrCheckProb = 0.68 - p.mood * 0.1;
        if (!"preflop".equals(stage)) {
            double baseRaiseProb = 1.0 - callOrCheckProb;
            double raiseProbPart = baseRaiseProb * (0.7 + 0.6 * p.aggression);
            if (raiseProbPart < 0.0) {
                raiseProbPart = 0.0;
            }
            if (raiseProbPart > 0.9) {
                raiseProbPart = 0.9;
            }
            callOrCheckProb = 1.0 - raiseProbPart;
            if (fishCallPlan == HandPlanType.VALUE
                    && (p.strength == SimpleStrength.STRONG || p.strength == SimpleStrength.MONSTER)) {
                callOrCheckProb *= 0.86;
            } else if (fishCallPlan == HandPlanType.POT_CONTROL) {
                callOrCheckProb *= 1.07;
            } else if (fishCallPlan == HandPlanType.GIVE_UP
                    && (p.strength == SimpleStrength.WEAK || p.strength == SimpleStrength.MEDIUM)) {
                callOrCheckProb *= 1.14;
            } else if (fishCallPlan == HandPlanType.BLUFF && p.strength == SimpleStrength.WEAK) {
                callOrCheckProb *= 1.06;
            }
        }
        if (p.callAmount == 0
                && !"preflop".equals(stage)
                && (p.strength == SimpleStrength.WEAK || p.strength == SimpleStrength.MEDIUM)
                && !DpNpcEngine.shouldSkipAggressiveActionByPlan(p.bot, stage)) {
            double baseBluffProb = (p.strength == SimpleStrength.MEDIUM) ? 0.20 : 0.12;
            double bluffProb = baseBluffProb * (0.5 + 1.0 * p.bluffFrequency);
            if (fishCallPlan == HandPlanType.BLUFF) {
                bluffProb *= 1.12;
            } else if (fishCallPlan == HandPlanType.GIVE_UP) {
                bluffProb *= 0.42;
            }
            if (fishCallCtx != null && fishCallCtx.activeVillains >= 2) {
                bluffProb *= 0.5;
            }
            bluffProb = DpNpcEngine.applySoftNoise(
                    Math.min(0.6, Math.max(0.0, bluffProb)),
                    RuleNpcConfig.PROB_NOISE_DELTA,
                    p.random);
            if (p.random.nextDouble() < bluffProb) {
                int pot = p.room.getPot();
                int bb = p.room.getBigBlindChips();
                int sb = p.room.getSmallBlindChips();
                double factor = 0.3;
                int target = (int) Math.round(pot * factor);
                int minBet = bb * 2;
                if (target < minBet) {
                    target = minBet;
                }
                int raiseAmountBluff = Math.min(p.chips, target);
                if (sb > 0 && raiseAmountBluff > 0) {
                    int units = Math.max(1, Math.round(raiseAmountBluff * 1.0f / sb));
                    raiseAmountBluff = units * sb;
                    if (raiseAmountBluff > p.chips) {
                        units = Math.max(1, p.chips / sb);
                        raiseAmountBluff = units * sb;
                    }
                }
                if (raiseAmountBluff > 0) {
                    DpNpcEngine.consumeOneBarrelIfAny(p.bot, stage);
                    return new BotAction(BotActionType.RAISE, raiseAmountBluff);
                }
            }
        }
        callOrCheckProb = DpNpcEngine.applySoftNoise(
                Math.min(0.95, Math.max(0.05, callOrCheckProb)),
                RuleNpcConfig.PROB_NOISE_DELTA,
                p.random);

        if (r < callOrCheckProb) {
            return new BotAction(BotActionType.CALL_OR_CHECK, 0);
        }

        if (!"preflop".equals(stage) && p.chips > p.callAmount) {
            int pot = p.room.getPot();
            int bb = p.room.getBigBlindChips();
            double multi;
            if (p.strength == SimpleStrength.STRONG || p.strength == SimpleStrength.MONSTER) {
                multi = 3.0;
            } else if (p.strength == SimpleStrength.MEDIUM) {
                multi = 2.5;
            } else {
                multi = 2.0;
            }
            if (fishCallCtx != null && fishCallCtx.activeVillains >= 2) {
                if (p.strength == SimpleStrength.WEAK) {
                    multi *= 0.62;
                } else if (p.strength == SimpleStrength.MEDIUM) {
                    multi *= 0.78;
                }
            }
            if (fishCallCtx != null && p.callAmount > 0) {
                double sprF = DpNpcEngine.computeHeroPotSpr(p.room, p.bot);
                if (sprF > 14.0) {
                    multi *= 0.9;
                }
            }
            int targetByRaise = (int) Math.round(p.callAmount * multi);
            int targetByPot = (int) Math.round(pot * 0.6);
            int target = Math.max(targetByRaise, targetByPot);
            int minExtra = (bb > 0 ? bb * 2 : 0);
            int minTarget = p.callAmount + minExtra;
            if (target < minTarget) {
                target = minTarget;
            }
            int raiseAmount = Math.min(p.chips, target);
            if (raiseAmount <= p.callAmount || raiseAmount <= 0) {
                return new BotAction(BotActionType.CALL_OR_CHECK, 0);
            }
            if (DpNpcEngine.shouldSkipAggressiveActionByPlan(p.bot, stage)) {
                return new BotAction(BotActionType.CALL_OR_CHECK, 0);
            }
            DpNpcEngine.consumeOneBarrelIfAny(p.bot, stage);
            return new BotAction(BotActionType.RAISE, raiseAmount);
        }

        return new BotAction(BotActionType.CALL_OR_CHECK, 0);
    }
}
