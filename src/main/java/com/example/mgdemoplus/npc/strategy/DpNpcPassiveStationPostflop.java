package com.example.mgdemoplus.npc.strategy;

import com.example.mgdemoplus.npc.engine.DpNpcEngine;
import com.example.mgdemoplus.npc.engine.DpNpcEngine.BotAction;
import com.example.mgdemoplus.npc.engine.DpNpcEngine.BotActionType;
import com.example.mgdemoplus.npc.engine.DpNpcEngine.BotType;
import com.example.mgdemoplus.npc.engine.DpNpcEngine.HandPlanType;
import com.example.mgdemoplus.npc.eval.DpNpcDrawCategory;
import com.example.mgdemoplus.npc.eval.DpNpcMadeHandCategory;
import com.example.mgdemoplus.npc.eval.DpNpcPostflopFormula;
import com.example.mgdemoplus.utils.DpUtilSmartContext;

/**
 * FISH / CALL 共享翻后：宽跟注 + 听牌不弃 + 少量诈唬。
 */
final class DpNpcPassiveStationPostflop {

    private DpNpcPassiveStationPostflop() {
    }

    static BotAction decide(DpNpcRuleDecisionParams p, BotType botType, StationProfile profile) {
        String stage = p.room.getCurrentStage();
        DpUtilSmartContext ctx = null;
        HandPlanType plan = null;

        if (!"preflop".equals(stage)) {
            ctx = DpNpcEngine.buildSmartContext(p.room, p.bot, p.handSnapshot, stage, p.callAmount, p.random);
            DpNpcEngine.initHandPlanIfNeededForPostflop(
                    p.room, p.bot, botType, p.handSnapshot, p.boardDanger,
                    p.position, ctx, p.random);
            DpNpcEngine.updateHandPlanForLaterStreetIfNeeded(
                    p.room, p.bot, botType, p.handSnapshot, p.boardDanger, ctx);
            plan = DpNpcEngine.getHandPlanType(p.bot);
        }

        DpNpcMadeHandCategory made = DpNpcPostflopFormula.madeOrHigh(p.handSnapshot);
        DpNpcDrawCategory draw = DpNpcPostflopFormula.drawOrNone(p.handSnapshot);

        if (p.callAmount > 0 && !"preflop".equals(stage)) {
            double foldBase = DpNpcPostflopFormula.baseFoldProb(
                    made, draw, DpNpcPostflopFormula.textureOrDry(p.handSnapshot),
                    p.callRatio, p.boardDanger, profile.foldTightnessMul);
            if (made == DpNpcMadeHandCategory.HIGH_CARD
                    && p.callRatio > 0 && p.callRatio < 0.38) {
                foldBase *= 0.52;
            }
            if (plan == HandPlanType.GIVE_UP) {
                foldBase = Math.min(1.0, foldBase + profile.giveUpFoldBoost);
            }
            if (ctx != null) {
                foldBase = Math.min(1.0, foldBase + DpNpcEngine.multiwayFoldProbBoost(
                        ctx.activeVillains, p.checkRaiseFear));
                foldBase = DpNpcEngine.adjustFoldProbForEquityVsPotOdds(
                        foldBase, p.callAmount, ctx.potOdds, ctx.equityEst,
                        ctx.activeVillains, profile.equityFoldShrink);
                foldBase = Math.max(0.0, foldBase - DpNpcPostflopFormula.drawCallFoldReduction(draw, ctx.potOdds));
                if (ctx.potOdds > 0 && ctx.potOdds < 0.34 && p.callRatio < 0.55
                        && made.isAtLeast(DpNpcMadeHandCategory.TOP_PAIR_WEAK_KICKER)) {
                    foldBase *= 0.68;
                }
            }
            if (p.random.nextDouble() < Math.min(1.0, foldBase)) {
                return new BotAction(BotActionType.FOLD, 0);
            }
        }

        double r = p.random.nextDouble();
        double callOrCheckProb = profile.baseCallProb;
        if (!"preflop".equals(stage)) {
            double raiseProbPart = (1.0 - callOrCheckProb) * (0.7 + 0.6 * p.aggression);
            raiseProbPart = Math.min(0.9, Math.max(0.0, raiseProbPart));
            callOrCheckProb = 1.0 - raiseProbPart;
            if (plan == HandPlanType.VALUE && made.isAtLeast(DpNpcMadeHandCategory.TRIPS)) {
                callOrCheckProb *= 0.86;
            } else if (plan == HandPlanType.POT_CONTROL) {
                callOrCheckProb *= 1.07;
            } else if (plan == HandPlanType.GIVE_UP
                    && made.ordinal() <= DpNpcMadeHandCategory.TOP_PAIR_WEAK_KICKER.ordinal()) {
                callOrCheckProb *= 1.14;
            } else if (plan == HandPlanType.BLUFF && made == DpNpcMadeHandCategory.HIGH_CARD) {
                callOrCheckProb *= 1.06;
            }
        }

        if (p.callAmount == 0 && !"preflop".equals(stage)
                && !DpNpcEngine.shouldSkipAggressiveActionByPlan(p.bot, stage)) {
            double bluffProb = DpNpcPostflopFormula.semiBluffProb(made, draw, 0.5 + p.bluffFrequency) * profile.bluffMul;
            if (plan == HandPlanType.BLUFF) {
                bluffProb *= 1.12;
            } else if (plan == HandPlanType.GIVE_UP) {
                bluffProb *= 0.42;
            }
            if (ctx != null && ctx.activeVillains >= 2) {
                bluffProb *= profile.multiwayBluffMul;
            }
            bluffProb = Math.min(0.6, Math.max(0.0, bluffProb));
            if (p.random.nextDouble() < bluffProb) {
                BotAction bluff = potBluffRaise(p, stage, profile.bluffPotFactor);
                if (bluff != null) {
                    return bluff;
                }
            }
        }

        callOrCheckProb = Math.min(0.95, Math.max(0.05, callOrCheckProb));
        if (r < callOrCheckProb) {
            return new BotAction(BotActionType.CALL_OR_CHECK, 0);
        }

        if (!"preflop".equals(stage) && p.chips > p.callAmount) {
            double multi = raiseMulti(made);
            if (ctx != null && ctx.activeVillains >= 2) {
                if (made == DpNpcMadeHandCategory.HIGH_CARD) {
                    multi *= 0.62;
                } else if (made.ordinal() <= DpNpcMadeHandCategory.TOP_PAIR_WEAK_KICKER.ordinal()) {
                    multi *= 0.78;
                }
            }
            if (ctx != null && p.callAmount > 0) {
                double spr = DpNpcEngine.computeHeroPotSpr(p.room, p.bot);
                if (spr > 14.0) {
                    multi *= 0.9;
                }
            }
            int pot = p.room.getPot();
            int bb = p.room.getBigBlindChips();
            int target = Math.max((int) Math.round(p.callAmount * multi), (int) Math.round(pot * 0.6));
            int minTarget = p.callAmount + (bb > 0 ? bb * 2 : 0);
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

    private static BotAction potBluffRaise(DpNpcRuleDecisionParams p, String stage, double factor) {
        int pot = p.room.getPot();
        int bb = p.room.getBigBlindChips();
        int sb = p.room.getSmallBlindChips();
        int target = (int) Math.round(pot * factor);
        int minBet = bb * 2;
        if (target < minBet) {
            target = minBet;
        }
        int raiseAmount = Math.min(p.chips, target);
        if (sb > 0 && raiseAmount > 0) {
            int units = Math.max(1, Math.round(raiseAmount * 1.0f / sb));
            raiseAmount = units * sb;
            if (raiseAmount > p.chips) {
                units = Math.max(1, p.chips / sb);
                raiseAmount = units * sb;
            }
        }
        if (raiseAmount > 0) {
            DpNpcEngine.consumeOneBarrelIfAny(p.bot, stage);
            return new BotAction(BotActionType.RAISE, raiseAmount);
        }
        return null;
    }

    private static double raiseMulti(DpNpcMadeHandCategory made) {
        if (made.isAtLeast(DpNpcMadeHandCategory.STRAIGHT)) {
            return 3.0;
        }
        if (made.isAtLeast(DpNpcMadeHandCategory.TWO_PAIR)) {
            return 2.8;
        }
        if (made.isAtLeast(DpNpcMadeHandCategory.TOP_PAIR_WEAK_KICKER)) {
            return 2.5;
        }
        return 2.0;
    }

    static final class StationProfile {
        final double baseCallProb;
        final double foldTightnessMul;
        final double equityFoldShrink;
        final double bluffMul;
        final double multiwayBluffMul;
        final double bluffPotFactor;
        final double giveUpFoldBoost;

        StationProfile(double baseCallProb, double foldTightnessMul, double equityFoldShrink,
                double bluffMul, double multiwayBluffMul, double bluffPotFactor, double giveUpFoldBoost) {
            this.baseCallProb = baseCallProb;
            this.foldTightnessMul = foldTightnessMul;
            this.equityFoldShrink = equityFoldShrink;
            this.bluffMul = bluffMul;
            this.multiwayBluffMul = multiwayBluffMul;
            this.bluffPotFactor = bluffPotFactor;
            this.giveUpFoldBoost = giveUpFoldBoost;
        }

        static final StationProfile FISH = new StationProfile(0.68, 0.82, 0.55, 1.0, 0.5, 0.3, 0.11);
        static final StationProfile CALL = new StationProfile(0.80, 0.65, 0.72, 0.95, 0.48, 0.25, 0.06);
    }
}
