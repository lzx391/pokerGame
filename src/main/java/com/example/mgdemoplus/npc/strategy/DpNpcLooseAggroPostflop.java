package com.example.mgdemoplus.npc.strategy;

import com.example.mgdemoplus.common.bo.DpRoomBO;
import com.example.mgdemoplus.common.entity.DpPlayer;
import com.example.mgdemoplus.npc.engine.DpNpcEngine;
import com.example.mgdemoplus.npc.engine.DpNpcEngine.ActionCredibility;
import com.example.mgdemoplus.npc.engine.DpNpcEngine.BoardDanger;
import com.example.mgdemoplus.npc.engine.DpNpcEngine.BotAction;
import com.example.mgdemoplus.npc.engine.DpNpcEngine.BotActionType;
import com.example.mgdemoplus.npc.engine.DpNpcEngine.BotType;
import com.example.mgdemoplus.npc.engine.DpNpcEngine.HandPlanType;
import com.example.mgdemoplus.npc.engine.DpNpcEngine.RuleNpcConfig;
import com.example.mgdemoplus.npc.engine.DpNpcEngine.VillainRangeTier;
import com.example.mgdemoplus.npc.eval.DpNpcDrawCategory;
import com.example.mgdemoplus.npc.eval.DpNpcMadeHandCategory;
import com.example.mgdemoplus.npc.eval.DpNpcPostflopFormula;
import com.example.mgdemoplus.utils.DpUtilSmartContext;

/**
 * LAG / MANIAC 共享翻后：高侵略 + equityEst 驱动 + 12 档成牌分支。
 */
final class DpNpcLooseAggroPostflop {

    private DpNpcLooseAggroPostflop() {
    }

    static BotAction decide(DpNpcRuleDecisionParams p, BotType botType, LooseProfile profile) {
        DpRoomBO room = p.room;
        DpPlayer bot = p.bot;
        int chips = p.chips;
        int callAmount = p.callAmount;
        BoardDanger boardDanger = p.boardDanger;
        String stage = room.getCurrentStage();

        DpUtilSmartContext ctx = DpNpcEngine.buildSmartContext(room, bot, p.handSnapshot, stage, callAmount, p.random);
        DpNpcEngine.initHandPlanIfNeededForPostflop(
                room, bot, botType, p.handSnapshot, boardDanger, p.position, ctx, p.random);
        DpNpcEngine.updateHandPlanForLaterStreetIfNeeded(
                room, bot, botType, p.handSnapshot, boardDanger, ctx);

        DpNpcMadeHandCategory made = DpNpcPostflopFormula.madeOrHigh(p.handSnapshot);
        DpNpcDrawCategory draw = DpNpcPostflopFormula.drawOrNone(p.handSnapshot);

        double commitFactor = DpNpcPostflopFormula.commitFactor(
                made, draw, DpNpcPostflopFormula.textureOrDry(p.handSnapshot), boardDanger);
        if (boardDanger == BoardDanger.WET) {
            commitFactor *= 0.8;
        }
        if (ctx.stackCtx != null && ctx.stackCtx.avgStackBB >= RuleNpcConfig.DEEP_TABLE_AVG_BB
                && !made.isAtLeast(DpNpcMadeHandCategory.TWO_PAIR)) {
            commitFactor *= 0.85;
        }
        commitFactor *= profile.commitMul;
        commitFactor = Math.max(0.1, Math.min(0.98, commitFactor));
        double commitThreshold = (bot.getBet() + bot.getChips()) * commitFactor;

        if (callAmount >= chips) {
            return facingAllIn(p, ctx, made, draw, profile);
        }

        double r = p.random.nextDouble();
        if (callAmount == 0) {
            return noBetLine(p, room, bot, chips, stage, ctx, made, draw, r, profile);
        }
        return facingBetLine(p, room, bot, chips, callAmount, stage, ctx, made, draw,
                commitThreshold, r, profile);
    }

    private static BotAction facingAllIn(
            DpNpcRuleDecisionParams p,
            DpUtilSmartContext ctx,
            DpNpcMadeHandCategory made,
            DpNpcDrawCategory draw,
            LooseProfile profile) {
        double allInProb = profile.baseAllInProb;
        double foldProb = profile.baseFoldProb;
        if (made == DpNpcMadeHandCategory.HIGH_CARD && !DpNpcPostflopFormula.hasSemiBluffDraw(draw)) {
            allInProb -= 0.25;
            foldProb += 0.25;
        }
        if (made == DpNpcMadeHandCategory.HIGH_CARD && ctx.activeVillains >= 2) {
            allInProb -= 0.12;
            foldProb += 0.08;
        }
        adjustCredibility(ctx, allInProb, foldProb, profile);
        allInProb = Math.max(profile.minAllInProb, Math.min(0.98, allInProb));
        foldProb = DpNpcEngine.adjustFoldProbForEquityVsPotOdds(
                Math.min(profile.maxFoldFacingAi, Math.max(0.03, foldProb)),
                p.callAmount, ctx.potOdds, ctx.equityEst, ctx.activeVillains, profile.equityShrink);
        double x = p.random.nextDouble();
        if (x < foldProb) {
            return new BotAction(BotActionType.FOLD, 0);
        }
        if (x < foldProb + allInProb) {
            if (DpNpcEngine.shouldSkipAggressiveActionByPlan(p.bot, p.room.getCurrentStage())) {
                return new BotAction(BotActionType.CALL_OR_CHECK, 0);
            }
            return new BotAction(BotActionType.ALL_IN, p.chips);
        }
        return new BotAction(BotActionType.CALL_OR_CHECK, 0);
    }

    private static void adjustCredibility(DpUtilSmartContext ctx, double allInProb, double foldProb, LooseProfile profile) {
        ActionCredibility cred = ctx.credibility;
        VillainRangeTier tier = ctx.villainTier;
        if (cred == ActionCredibility.LOW || ctx.showdownBluffiness > 0.4
                || tier == VillainRangeTier.LOOSE || tier == VillainRangeTier.MANIAC) {
            allInProb += profile.credLooseAllInBoost;
            foldProb = Math.max(0.05, foldProb - 0.05);
        } else if (cred == ActionCredibility.HIGH || ctx.showdownBluffiness < 0.1
                || tier == VillainRangeTier.NIT || tier == VillainRangeTier.TIGHT) {
            allInProb -= profile.credTightAllInPenalty;
            foldProb += 0.05;
        }
    }

    private static BotAction noBetLine(
            DpNpcRuleDecisionParams p,
            DpRoomBO room,
            DpPlayer bot,
            int chips,
            String stage,
            DpUtilSmartContext ctx,
            DpNpcMadeHandCategory made,
            DpNpcDrawCategory draw,
            double r,
            LooseProfile profile) {
        double raiseProb = profile.baseOpenRaiseProb * (0.7 + 0.6 * p.aggression);
        HandPlanType lp = DpNpcEngine.getHandPlanType(bot);
        if (lp == HandPlanType.GIVE_UP && made == DpNpcMadeHandCategory.HIGH_CARD) {
            raiseProb *= 0.58;
        } else if (lp == HandPlanType.VALUE && made.isAtLeast(DpNpcMadeHandCategory.TRIPS)) {
            raiseProb *= 1.08;
        } else if (lp == HandPlanType.POT_CONTROL) {
            raiseProb *= 0.9;
        } else if (DpNpcPostflopFormula.hasSemiBluffDraw(draw)) {
            raiseProb = Math.min(0.95, raiseProb + 0.12);
        }
        raiseProb = Math.max(profile.minOpenRaiseProb, Math.min(0.98, raiseProb));
        if (ctx.activeVillains >= 2 && made == DpNpcMadeHandCategory.HIGH_CARD) {
            raiseProb *= 0.68;
            raiseProb = Math.max(0.45, raiseProb);
        }
        if (r < raiseProb) {
            int minMulti = openMinMulti(made, draw);
            int maxMulti = openMaxMulti(made, draw);
            int multiplier = minMulti + p.random.nextInt(Math.max(1, maxMulti - minMulti + 1));
            int raiseAmount = Math.min(chips, room.getBigBlindChips() * multiplier);
            if (raiseAmount <= 0) {
                return new BotAction(BotActionType.CALL_OR_CHECK, 0);
            }
            if (DpNpcEngine.shouldSkipAggressiveActionByPlan(bot, stage)) {
                return new BotAction(BotActionType.CALL_OR_CHECK, 0);
            }
            DpNpcEngine.consumeOneBarrelIfAny(bot, stage);
            return new BotAction(BotActionType.RAISE, raiseAmount);
        }
        return new BotAction(BotActionType.CALL_OR_CHECK, 0);
    }

    private static BotAction facingBetLine(
            DpNpcRuleDecisionParams p,
            DpRoomBO room,
            DpPlayer bot,
            int chips,
            int callAmount,
            String stage,
            DpUtilSmartContext ctx,
            DpNpcMadeHandCategory made,
            DpNpcDrawCategory draw,
            double commitThreshold,
            double r,
            LooseProfile profile) {
        double foldBase = profile.baseFacingFold;
        double callRatio = p.callRatio;
        if (made == DpNpcMadeHandCategory.HIGH_CARD
                && callRatio > 0.85 && p.boardDanger == BoardDanger.WET) {
            foldBase = 0.2;
        }
        double foldProb = foldBase * (1.0 - 0.4 * p.callStation);
        foldProb += DpNpcEngine.multiwayFoldProbBoost(ctx.activeVillains, p.checkRaiseFear);
        foldProb = DpNpcEngine.adjustFoldProbForEquityVsPotOdds(
                foldProb, callAmount, ctx.potOdds, ctx.equityEst, ctx.activeVillains, profile.equityShrink);
        if (foldProb > profile.maxFacingFold) {
            foldProb = profile.maxFacingFold;
        }
        if (callAmount > 0 && p.random.nextDouble() < foldProb) {
            return new BotAction(BotActionType.FOLD, 0);
        }
        if (r < 0.25) {
            return new BotAction(BotActionType.CALL_OR_CHECK, 0);
        }
        if (r < 0.85) {
            return raiseFacingBet(p, room, bot, chips, callAmount, stage, ctx, made, commitThreshold, profile);
        }
        if (DpNpcEngine.shouldSkipAggressiveActionByPlan(bot, stage)) {
            return new BotAction(BotActionType.CALL_OR_CHECK, 0);
        }
        if (made == DpNpcMadeHandCategory.HIGH_CARD && !DpNpcPostflopFormula.hasSemiBluffDraw(draw)) {
            return new BotAction(BotActionType.CALL_OR_CHECK, 0);
        }
        if (ctx.activeVillains >= 2 && !made.isAtLeast(DpNpcMadeHandCategory.TRIPS)) {
            return new BotAction(BotActionType.CALL_OR_CHECK, 0);
        }
        return new BotAction(BotActionType.ALL_IN, chips);
    }

    private static BotAction raiseFacingBet(
            DpNpcRuleDecisionParams p,
            DpRoomBO room,
            DpPlayer bot,
            int chips,
            int callAmount,
            String stage,
            DpUtilSmartContext ctx,
            DpNpcMadeHandCategory made,
            double commitThreshold,
            LooseProfile profile) {
        int minMulti = facingMinMulti(made);
        int maxMulti = facingMaxMulti(made);
        int multiplier = minMulti + p.random.nextInt(Math.max(1, maxMulti - minMulti + 1));
        int raiseAmount = Math.min(chips, callAmount + room.getBigBlindChips() * multiplier);
        if (raiseAmount <= 0) {
            return new BotAction(BotActionType.CALL_OR_CHECK, 0);
        }
        if (bot.getBet() + raiseAmount >= commitThreshold) {
            double y = p.random.nextDouble();
            y = DpNpcEngine.skewCommitThresholdRandom(y, ctx, callAmount, profile.skewFactor, true);
            if (made == DpNpcMadeHandCategory.HIGH_CARD) {
                if (ctx.activeVillains >= 2) {
                    return new BotAction(BotActionType.CALL_OR_CHECK, 0);
                }
                return y < profile.weakCommitCallThreshold
                        ? new BotAction(BotActionType.CALL_OR_CHECK, 0)
                        : new BotAction(BotActionType.FOLD, 0);
            }
            if (DpNpcEngine.shouldSkipAggressiveActionByPlan(bot, stage)) {
                return y < 0.55
                        ? new BotAction(BotActionType.CALL_OR_CHECK, 0)
                        : new BotAction(BotActionType.FOLD, 0);
            }
            if (y < 0.7) {
                return new BotAction(BotActionType.ALL_IN, chips);
            }
            return y < 0.9
                    ? new BotAction(BotActionType.CALL_OR_CHECK, 0)
                    : new BotAction(BotActionType.FOLD, 0);
        }
        int bb = room.getBigBlindChips();
        if (bb > 0 && room.getPot() > bb * 20 && p.random.nextDouble() < profile.potAllInProb) {
            if (!DpNpcEngine.shouldSkipAggressiveActionByPlan(bot, stage)
                    && ctx.activeVillains <= 1
                    && made != DpNpcMadeHandCategory.HIGH_CARD) {
                return new BotAction(BotActionType.ALL_IN, chips);
            }
        }
        if (DpNpcEngine.shouldSkipAggressiveActionByPlan(bot, stage)) {
            return new BotAction(BotActionType.CALL_OR_CHECK, 0);
        }
        DpNpcEngine.consumeOneBarrelIfAny(bot, stage);
        return new BotAction(BotActionType.RAISE, raiseAmount);
    }

    private static int openMinMulti(DpNpcMadeHandCategory made, DpNpcDrawCategory draw) {
        if (made.isAtLeast(DpNpcMadeHandCategory.TRIPS)) {
            return 3;
        }
        if (made.isAtLeast(DpNpcMadeHandCategory.TOP_PAIR_TOP_KICKER)) {
            return 2;
        }
        if (DpNpcPostflopFormula.hasSemiBluffDraw(draw)) {
            return 2;
        }
        return 1;
    }

    private static int openMaxMulti(DpNpcMadeHandCategory made, DpNpcDrawCategory draw) {
        if (made.isAtLeast(DpNpcMadeHandCategory.TRIPS)) {
            return 5;
        }
        if (made.isAtLeast(DpNpcMadeHandCategory.TWO_PAIR)) {
            return 4;
        }
        if (DpNpcPostflopFormula.hasSemiBluffDraw(draw)) {
            return 4;
        }
        return 3;
    }

    private static int facingMinMulti(DpNpcMadeHandCategory made) {
        return made.isAtLeast(DpNpcMadeHandCategory.TRIPS) ? 3 : 2;
    }

    private static int facingMaxMulti(DpNpcMadeHandCategory made) {
        return made.isAtLeast(DpNpcMadeHandCategory.TRIPS) ? 6 : 5;
    }

    static final class LooseProfile {
        final double commitMul;
        final double baseAllInProb;
        final double baseFoldProb;
        final double minAllInProb;
        final double maxFoldFacingAi;
        final double equityShrink;
        final double credLooseAllInBoost;
        final double credTightAllInPenalty;
        final double baseOpenRaiseProb;
        final double minOpenRaiseProb;
        final double baseFacingFold;
        final double maxFacingFold;
        final double skewFactor;
        final double weakCommitCallThreshold;
        final double potAllInProb;

        LooseProfile(double commitMul, double baseAllInProb, double baseFoldProb, double minAllInProb,
                double maxFoldFacingAi, double equityShrink, double credLooseAllInBoost,
                double credTightAllInPenalty, double baseOpenRaiseProb, double minOpenRaiseProb,
                double baseFacingFold, double maxFacingFold, double skewFactor,
                double weakCommitCallThreshold, double potAllInProb) {
            this.commitMul = commitMul;
            this.baseAllInProb = baseAllInProb;
            this.baseFoldProb = baseFoldProb;
            this.minAllInProb = minAllInProb;
            this.maxFoldFacingAi = maxFoldFacingAi;
            this.equityShrink = equityShrink;
            this.credLooseAllInBoost = credLooseAllInBoost;
            this.credTightAllInPenalty = credTightAllInPenalty;
            this.baseOpenRaiseProb = baseOpenRaiseProb;
            this.minOpenRaiseProb = minOpenRaiseProb;
            this.baseFacingFold = baseFacingFold;
            this.maxFacingFold = maxFacingFold;
            this.skewFactor = skewFactor;
            this.weakCommitCallThreshold = weakCommitCallThreshold;
            this.potAllInProb = potAllInProb;
        }

        static final LooseProfile LAG = new LooseProfile(
                0.88, 0.9, 0.1, 0.5, 0.58, 0.78, 0.1, 0.15,
                0.75 * 0.88, 0.5, 0.03, 0.58, 0.88, 0.9, 0.4);
        static final LooseProfile MANIAC = new LooseProfile(
                1.0, 0.9, 0.1, 0.5, 0.52, 0.52, 0.1, 0.15,
                0.75, 0.5, 0.03, 0.52, 0.52, 0.82, 0.4);
    }
}
