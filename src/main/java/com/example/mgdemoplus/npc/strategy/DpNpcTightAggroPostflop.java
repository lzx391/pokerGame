package com.example.mgdemoplus.npc.strategy;

import com.example.mgdemoplus.npc.engine.DpNpcEngine;
import com.example.mgdemoplus.npc.engine.DpNpcEngine.ActionCredibility;
import com.example.mgdemoplus.npc.engine.DpNpcEngine.BoardDanger;
import com.example.mgdemoplus.npc.engine.DpNpcEngine.BotAction;
import com.example.mgdemoplus.npc.engine.DpNpcEngine.BotActionType;
import com.example.mgdemoplus.npc.engine.DpNpcEngine.BotType;
import com.example.mgdemoplus.npc.engine.DpNpcEngine.HandPlanType;
import com.example.mgdemoplus.npc.engine.DpNpcEngine.RuleNpcConfig;
import com.example.mgdemoplus.npc.eval.DpBoardTexture;
import com.example.mgdemoplus.npc.eval.DpNpcDrawCategory;
import com.example.mgdemoplus.npc.eval.DpNpcMadeHandCategory;
import com.example.mgdemoplus.npc.eval.DpNpcPostflopFormula;
import com.example.mgdemoplus.utils.DpUtilSmartContext;

/**
 * TAG / NIT 共享翻后公式化决策（12 档）。
 */
final class DpNpcTightAggroPostflop {

    private DpNpcTightAggroPostflop() {
    }

    static BotAction decide(DpNpcRuleDecisionParams p, BotType botType, PersonalityProfile profile) {
        BoardDanger bd = p.boardDanger;
        String stage = p.room.getCurrentStage();
        int callAmount = Math.max(0, p.room.getCurrentBetToCall() - p.bot.getBet());
        double callRatio = p.chips == 0 ? 1.0 : (callAmount * 1.0 / p.chips);
        DpUtilSmartContext ctx = DpNpcEngine.buildSmartContext(
                p.room, p.bot, p.handSnapshot, stage, callAmount, p.random);

        DpNpcEngine.initHandPlanIfNeededForPostflop(
                p.room, p.bot, botType, p.handSnapshot, bd, p.position, ctx, p.random);
        DpNpcEngine.updateHandPlanForLaterStreetIfNeeded(
                p.room, p.bot, botType, p.handSnapshot, bd, ctx);

        DpNpcMadeHandCategory made = DpNpcPostflopFormula.madeOrHigh(p.handSnapshot);
        DpNpcDrawCategory draw = DpNpcPostflopFormula.drawOrNone(p.handSnapshot);
        DpBoardTexture tex = DpNpcPostflopFormula.textureOrDry(p.handSnapshot);

        double tagCommitFactor = DpNpcPostflopFormula.commitFactor(made, draw, tex, bd) * profile.commitMul;
        if (!"preflop".equals(stage) && bd == BoardDanger.WET) {
            tagCommitFactor *= 0.85;
        }
        if (!"preflop".equals(stage)
                && ctx.stackCtx != null
                && ctx.stackCtx.avgStackBB >= RuleNpcConfig.DEEP_TABLE_AVG_BB
                && !made.isAtLeast(DpNpcMadeHandCategory.TWO_PAIR)) {
            tagCommitFactor *= 0.85;
        }
        if (!"preflop".equals(stage)) {
            double spr = DpNpcEngine.computeHeroPotSpr(p.room, p.bot);
            if (spr < 2.5 && made.isAtLeast(DpNpcMadeHandCategory.TRIPS)) {
                tagCommitFactor = Math.min(0.92, tagCommitFactor * 1.06);
            } else if (spr > 14.0 && made.ordinal() <= DpNpcMadeHandCategory.TOP_PAIR_WEAK_KICKER.ordinal()) {
                tagCommitFactor *= 0.88;
            }
        }
        tagCommitFactor = Math.max(0.15, Math.min(0.9, tagCommitFactor));
        final double tagCommitThreshold = (p.bot.getBet() + p.bot.getChips()) * tagCommitFactor;

        double baseFold = DpNpcPostflopFormula.baseFoldProb(made, draw, tex, callRatio, bd, profile.foldTightnessMul);
        baseFold = Math.min(1.0, baseFold + DpNpcEngine.multiwayFoldProbBoost(ctx.activeVillains, p.checkRaiseFear));
        baseFold = DpNpcEngine.adjustFoldProbForEquityVsPotOdds(
                baseFold, callAmount, ctx.potOdds, ctx.equityEst, ctx.activeVillains, 1.0);

        if (made == DpNpcMadeHandCategory.HIGH_CARD && ctx.potOdds > 0.58) {
            baseFold = Math.min(1.0, baseFold + 0.06);
        } else if (ctx.potOdds > 0 && ctx.potOdds < 0.22
                && made.isAtLeast(DpNpcMadeHandCategory.TOP_PAIR_WEAK_KICKER)) {
            baseFold = Math.max(0.0, baseFold * 0.92);
        }

        ActionCredibility cred = ctx.credibility;
        if (cred == ActionCredibility.HIGH || ctx.showdownBluffiness < 0.1) {
            baseFold = Math.min(1.0, baseFold + 0.1);
        } else if (cred == ActionCredibility.LOW || ctx.showdownBluffiness > 0.4) {
            baseFold = Math.max(0.0, baseFold * 0.7);
        }

        HandPlanType plan = DpNpcEngine.getHandPlanType(p.bot);
        if (plan == HandPlanType.GIVE_UP && callAmount > 0) {
            baseFold = Math.min(1.0, baseFold + 0.15);
        }
        if (ctx.counterStrategy != null && callAmount > 0
                && ctx.counterStrategy.foldMoreToBigBets && callRatio > 0.5) {
            baseFold = Math.min(1.0, baseFold + 0.1);
        }

        double foldProb = Math.min(1.0, Math.max(0.0, baseFold * (1.0 - 0.5 * p.callStation)));
        if (callAmount > 0 && foldProb > 0 && p.random.nextDouble() < foldProb) {
            return new BotAction(BotActionType.FOLD, 0);
        }

        if (callAmount == 0) {
            return decideNoBetLine(p, stage, made, draw, tex, plan, ctx, profile);
        }
        return decideFacingBetLine(p, stage, callAmount, made, draw, tex, plan, ctx,
                tagCommitThreshold, profile);
    }

    private static BotAction decideNoBetLine(
            DpNpcRuleDecisionParams p,
            String stage,
            DpNpcMadeHandCategory made,
            DpNpcDrawCategory draw,
            DpBoardTexture tex,
            HandPlanType plan,
            DpUtilSmartContext ctx,
            PersonalityProfile profile) {
        double valueBetProb = DpNpcPostflopFormula.valueBetProb(made, tex, stage, plan) * profile.valueMul;
        double factor = DpNpcPostflopFormula.cbetPotFactor(made, tex, stage);
        if (made.ordinal() <= DpNpcMadeHandCategory.TOP_PAIR_WEAK_KICKER.ordinal()
                && !DpNpcPostflopFormula.hasSemiBluffDraw(draw)) {
            if (made == DpNpcMadeHandCategory.HIGH_CARD) {
                double semi = DpNpcPostflopFormula.semiBluffProb(made, draw, 0.5 + p.bluffFrequency);
                if (p.random.nextDouble() < semi && !DpNpcEngine.shouldSkipAggressiveActionByPlan(p.bot, stage)) {
                    return raisePotFraction(p, stage, RuleNpcConfig.CBET_BASE_WEAK);
                }
            }
            return new BotAction(BotActionType.CALL_OR_CHECK, 0);
        }

        double aggroFactor = (0.7 + 0.6 * p.aggression) * profile.aggressionMul;
        if (plan == HandPlanType.VALUE) {
            aggroFactor *= 1.1;
        } else if (plan == HandPlanType.POT_CONTROL) {
            aggroFactor *= 0.8;
        }
        valueBetProb *= aggroFactor;
        if (ctx.activeVillains >= 2
                && made.ordinal() <= DpNpcMadeHandCategory.TOP_PAIR_TOP_KICKER.ordinal()
                && made.ordinal() >= DpNpcMadeHandCategory.MIDDLE_PAIR.ordinal()) {
            valueBetProb *= 0.72;
        }
        valueBetProb = Math.min(0.95, Math.max(0.05, valueBetProb));
        if (p.random.nextDouble() < valueBetProb) {
            if (DpNpcEngine.shouldSkipAggressiveActionByPlan(p.bot, stage)) {
                return new BotAction(BotActionType.CALL_OR_CHECK, 0);
            }
            return raisePotFraction(p, stage, factor);
        }
        return new BotAction(BotActionType.CALL_OR_CHECK, 0);
    }

    private static BotAction decideFacingBetLine(
            DpNpcRuleDecisionParams p,
            String stage,
            int callAmount,
            DpNpcMadeHandCategory made,
            DpNpcDrawCategory draw,
            DpBoardTexture tex,
            HandPlanType plan,
            DpUtilSmartContext ctx,
            double tagCommitThreshold,
            PersonalityProfile profile) {
        double raiseProb = DpNpcPostflopFormula.raiseProb(made, draw, tex, plan) * profile.raiseMul;
        double aggroFactorRaise = (0.7 + 0.6 * p.aggression) * profile.aggressionMul;
        if (plan == HandPlanType.VALUE) {
            aggroFactorRaise *= 1.1;
        } else if (plan == HandPlanType.POT_CONTROL) {
            aggroFactorRaise *= 0.8;
        } else if (plan == HandPlanType.BLUFF) {
            aggroFactorRaise *= 1.05;
        }
        raiseProb *= aggroFactorRaise;
        if (ctx.activeVillains >= 2) {
            if (made == DpNpcMadeHandCategory.HIGH_CARD) {
                raiseProb *= 0.42;
            } else if (made.ordinal() <= DpNpcMadeHandCategory.TOP_PAIR_WEAK_KICKER.ordinal()) {
                raiseProb *= 0.76;
            }
        }
        raiseProb = Math.min(0.9, Math.max(0.05, raiseProb));

        if (p.random.nextDouble() > raiseProb || p.chips <= callAmount) {
            return new BotAction(BotActionType.CALL_OR_CHECK, 0);
        }

        int bb = p.room.getBigBlindChips();
        int extraMin = DpNpcPostflopFormula.raiseExtraMinBb(made);
        int extraMax = DpNpcPostflopFormula.raiseExtraMaxBb(made, stage);
        int extraBB = extraMin + p.random.nextInt(Math.max(1, extraMax - extraMin + 1));
        int target = callAmount + extraBB * bb;
        if (!"river".equals(stage)
                && made.ordinal() <= DpNpcMadeHandCategory.TOP_PAIR_WEAK_KICKER.ordinal()
                && made.ordinal() >= DpNpcMadeHandCategory.MIDDLE_PAIR.ordinal()) {
            target = (int) Math.round(target * 0.8);
        }
        int raiseAmount = Math.min(p.chips, target);
        raiseAmount = snapRaiseToSb(p, callAmount, raiseAmount, made, bb);

        if (raiseAmount <= callAmount) {
            return new BotAction(BotActionType.CALL_OR_CHECK, 0);
        }
        int heroInvestAfter = p.bot.getBet() + raiseAmount;
        if (heroInvestAfter > tagCommitThreshold) {
            return commitThresholdAction(p, ctx, callAmount, made);
        }
        if (DpNpcEngine.shouldSkipAggressiveActionByPlan(p.bot, stage)) {
            return new BotAction(BotActionType.CALL_OR_CHECK, 0);
        }
        DpNpcEngine.consumeOneBarrelIfAny(p.bot, stage);
        return new BotAction(BotActionType.RAISE, raiseAmount);
    }

    private static BotAction raisePotFraction(DpNpcRuleDecisionParams p, String stage, double factor) {
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
        DpNpcEngine.consumeOneBarrelIfAny(p.bot, stage);
        return new BotAction(BotActionType.RAISE, raiseAmount);
    }

    private static int snapRaiseToSb(
            DpNpcRuleDecisionParams p,
            int callAmount,
            int raiseAmount,
            DpNpcMadeHandCategory made,
            int bb) {
        if (callAmount > 0 && bb > 0) {
            int minExtraBB = made.isAtLeast(DpNpcMadeHandCategory.TRIPS) ? 4 : 3;
            if (p.room.getPot() > bb * 20) {
                minExtraBB += 1;
            }
            int minRaise = callAmount + minExtraBB * bb;
            if (raiseAmount < minRaise) {
                raiseAmount = Math.min(p.chips, minRaise);
            }
        }
        int sb = p.room.getSmallBlindChips();
        if (sb > 0 && raiseAmount > 0) {
            int units = Math.max(1, Math.round(raiseAmount * 1.0f / sb));
            raiseAmount = units * sb;
            if (raiseAmount > p.chips) {
                units = Math.max(1, p.chips / sb);
                raiseAmount = units * sb;
            }
        }
        return raiseAmount;
    }

    private static BotAction commitThresholdAction(
            DpNpcRuleDecisionParams p,
            DpUtilSmartContext ctx,
            int callAmount,
            DpNpcMadeHandCategory made) {
        double y = p.random.nextDouble();
        if (made.isAtLeast(DpNpcMadeHandCategory.TRIPS)) {
            y = DpNpcEngine.skewCommitThresholdRandom(y, ctx, callAmount, 1.0, false);
            return y < 0.8
                    ? new BotAction(BotActionType.CALL_OR_CHECK, 0)
                    : new BotAction(BotActionType.ALL_IN, p.chips);
        }
        if (made.isAtLeast(DpNpcMadeHandCategory.TOP_PAIR_TOP_KICKER)) {
            y = DpNpcEngine.skewCommitThresholdRandom(y, ctx, callAmount, 1.0, false);
            return y < 0.8
                    ? new BotAction(BotActionType.CALL_OR_CHECK, 0)
                    : new BotAction(BotActionType.ALL_IN, p.chips);
        }
        if (made.ordinal() >= DpNpcMadeHandCategory.MIDDLE_PAIR.ordinal()
                && made.ordinal() <= DpNpcMadeHandCategory.TOP_PAIR_WEAK_KICKER.ordinal()) {
            y = DpNpcEngine.skewCommitThresholdRandom(y, ctx, callAmount, 1.0, true);
            return y < 0.7
                    ? new BotAction(BotActionType.CALL_OR_CHECK, 0)
                    : new BotAction(BotActionType.FOLD, 0);
        }
        y = DpNpcEngine.skewCommitThresholdRandom(y, ctx, callAmount, 1.0, false);
        return y < 0.85
                ? new BotAction(BotActionType.FOLD, 0)
                : new BotAction(BotActionType.CALL_OR_CHECK, 0);
    }

    static final class PersonalityProfile {
        final double foldTightnessMul;
        final double commitMul;
        final double valueMul;
        final double raiseMul;
        final double aggressionMul;

        PersonalityProfile(double foldTightnessMul, double commitMul, double valueMul,
                double raiseMul, double aggressionMul) {
            this.foldTightnessMul = foldTightnessMul;
            this.commitMul = commitMul;
            this.valueMul = valueMul;
            this.raiseMul = raiseMul;
            this.aggressionMul = aggressionMul;
        }

        static final PersonalityProfile TAG = new PersonalityProfile(1.0, 1.0, 1.0, 1.0, 1.0);
        static final PersonalityProfile NIT = new PersonalityProfile(1.18, 0.92, 0.88, 0.82, 0.88);
    }
}
