package com.example.mgdemoplus.npc.strategypro.postflop.nit;

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
import com.example.mgdemoplus.npc.strategypro.DpNpcRuleDecisionParams;
import com.example.mgdemoplus.npc.strategypro.l1.DpNpcHardConstraints;
import com.example.mgdemoplus.npc.strategypro.l4.DpNpcHeroCall;
import com.example.mgdemoplus.npc.strategypro.l4.DpNpcRaiseEscalation;
import com.example.mgdemoplus.npc.strategypro.l4.DpNpcRaiseEscalation.EscalationResult;
import com.example.mgdemoplus.npc.trace.DpNpcPostflopTraceHooks;
import com.example.mgdemoplus.utils.DpUtilSmartContext;

/**
 * NIT 独立翻后策略：极紧、高弃牌、极少诈唬；湿面/多人池显著收紧。
 * <p>禁止套壳旧 TightAggro 聚合决策类或 TAG 策略类。</p>
 */
public final class DpNpcNitPostflopStrategy {

    /** NIT preset cbet 频率基准（§4.5 ≈28%） */
    private static final double NIT_CBET_FREQ = 0.28;
    /** 相对 TAG（1.0）更紧的弃牌系数 */
    private static final double NIT_FOLD_TIGHTNESS = 1.18;

    private DpNpcNitPostflopStrategy() {
    }

    public static BotAction decide(DpNpcRuleDecisionParams p) {
        BoardDanger bd = p.boardDanger;
        String stage = p.room.getCurrentStage();
        int callAmount = Math.max(0, p.room.getCurrentBetToCall() - p.bot.getBet());
        DpUtilSmartContext ctx = DpNpcEngine.buildSmartContext(
                p.room, p.bot, p.handSnapshot, stage, callAmount, p.random);

        DpNpcEngine.initHandPlanIfNeededForPostflop(
                p.room, p.bot, BotType.NIT, p.handSnapshot, bd, p.position, ctx, p.random);
        DpNpcEngine.updateHandPlanForLaterStreetIfNeeded(
                p.room, p.bot, BotType.NIT, p.handSnapshot, bd, ctx);

        DpNpcMadeHandCategory made = DpNpcPostflopFormula.madeOrHigh(p.handSnapshot);
        DpNpcDrawCategory draw = DpNpcPostflopFormula.drawOrNone(p.handSnapshot);
        DpBoardTexture tex = DpNpcPostflopFormula.textureOrDry(p.handSnapshot);
        HandPlanType plan = DpNpcEngine.getHandPlanType(p.bot);

        double commitFactor = nitCommitFactor(made, draw, tex, bd, p, ctx);
        final double commitThreshold = (p.bot.getBet() + p.bot.getChips()) * commitFactor;

        DpNpcPostflopTraceHooks.postflopContext(
                stage, callAmount, plan, made, draw, tex, bd, p, ctx, commitFactor, commitThreshold);

        if (callAmount > 0) {
            BotAction foldAction = tryFoldFacingBet(p, stage, callAmount, made, draw, tex, plan, ctx);
            if (foldAction != null) {
                return foldAction;
            }
            return decideFacingBet(p, stage, callAmount, made, draw, tex, plan, ctx, commitThreshold);
        }
        return decideNoBet(p, stage, made, draw, tex, plan, ctx);
    }

    private static double nitCommitFactor(
            DpNpcMadeHandCategory made,
            DpNpcDrawCategory draw,
            DpBoardTexture tex,
            BoardDanger bd,
            DpNpcRuleDecisionParams p,
            DpUtilSmartContext ctx) {
        double factor = DpNpcPostflopFormula.commitFactor(made, draw, tex, bd) * 0.92;
        if (!"preflop".equals(p.room.getCurrentStage()) && bd == BoardDanger.WET) {
            factor *= 0.78;
        }
        if (!"preflop".equals(p.room.getCurrentStage()) && ctx.stackCtx != null) {
            factor = DpNpcRaiseEscalation.adjustDeepStackCommitFactor(
                    factor, made, ctx.stackCtx.avgStackBB);
            if (!made.isAtLeast(DpNpcMadeHandCategory.TWO_PAIR)) {
                factor *= 0.94;
            }
        }
        if (!"preflop".equals(p.room.getCurrentStage())) {
            double spr = DpNpcEngine.computeHeroPotSpr(p.room, p.bot);
            if (spr < 2.5 && made.isAtLeast(DpNpcMadeHandCategory.TRIPS)) {
                factor = Math.min(0.88, factor * 1.04);
            } else if (spr > 14.0
                    && made.ordinal() <= DpNpcMadeHandCategory.TOP_PAIR_WEAK_KICKER.ordinal()) {
                factor *= 0.82;
            }
        }
        return Math.max(0.12, Math.min(0.82, factor));
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
        double callRatio = p.chips == 0 ? 1.0 : (callAmount * 1.0 / p.chips);
        boolean wetBoard = p.boardDanger == BoardDanger.WET || tex.wet;

        BotAction marginalFold = tryNitMarginalFoldGate(
                p, callAmount, made, callRatio, wetBoard, ctx);
        if (marginalFold != null) {
            return marginalFold;
        }

        double baseFold = DpNpcPostflopFormula.baseFoldProb(
                made, draw, tex, callRatio, p.boardDanger, NIT_FOLD_TIGHTNESS);
        baseFold = Math.min(1.0, baseFold + DpNpcHardConstraints.multiwayFoldBoost(
                made, ctx.activeVillains, p.checkRaiseFear, callRatio));
        baseFold = DpNpcEngine.adjustFoldProbForEquityVsPotOdds(
                baseFold, callAmount, ctx.potOdds, ctx.equityEst, ctx.activeVillains, NIT_FOLD_TIGHTNESS);

        if (made == DpNpcMadeHandCategory.HIGH_CARD) {
            if (DpNpcPostflopFormula.hasSemiBluffDraw(draw) && callRatio >= 0.20) {
                baseFold = Math.min(1.0, baseFold + 0.22);
            }
            if (ctx.potOdds > 0.45) {
                baseFold = Math.min(1.0, baseFold + 0.12);
            }
        } else if (ctx.potOdds > 0 && ctx.potOdds < 0.22
                && made.isAtLeast(DpNpcMadeHandCategory.TOP_PAIR_WEAK_KICKER)) {
            baseFold = Math.max(0.0, baseFold * 0.88);
        }

        ActionCredibility cred = ctx.credibility;
        if (cred == ActionCredibility.HIGH || ctx.showdownBluffiness < 0.1) {
            baseFold = Math.min(1.0, baseFold + 0.14);
        } else if (cred == ActionCredibility.LOW || ctx.showdownBluffiness > 0.4) {
            baseFold = Math.max(0.0, baseFold * 0.62);
        }

        if (plan == HandPlanType.GIVE_UP && callAmount > 0
                && !made.isAtLeast(DpNpcMadeHandCategory.TRIPS)) {
            double beforeGiveUp = baseFold;
            baseFold = Math.min(1.0, baseFold + 0.22);
            DpNpcPostflopTraceHooks.giveUpFoldBoost(plan, made, beforeGiveUp, baseFold, 0.22);
        }
        if (plan == HandPlanType.POT_CONTROL && callAmount > 0
                && made.ordinal() <= DpNpcMadeHandCategory.TOP_PAIR_TOP_KICKER.ordinal()) {
            baseFold = Math.min(1.0, baseFold + 0.10);
        }
        if (ctx.counterStrategy != null && callAmount > 0
                && ctx.counterStrategy.foldMoreToBigBets && callRatio > 0.5) {
            baseFold = Math.min(1.0, baseFold + 0.14);
        }

        // NIT：顶对弱踢脚湿面/大注极易弃牌（决策分叉，非 TAG 系数套壳）
        if (made == DpNpcMadeHandCategory.TOP_PAIR_WEAK_KICKER) {
            if (callRatio >= 0.20) {
                baseFold = Math.min(1.0, baseFold + 0.38 + 0.24 * Math.min(1.0, (callRatio - 0.20) / 0.40));
            }
            if ((p.boardDanger == BoardDanger.WET || tex.wet)) {
                baseFold = Math.min(1.0, baseFold + 0.32);
                if (callRatio >= 0.33) {
                    baseFold = Math.min(1.0, baseFold + 0.18);
                }
            }
        }
        if (made == DpNpcMadeHandCategory.MIDDLE_PAIR || made == DpNpcMadeHandCategory.BOTTOM_PAIR) {
            if (callRatio >= 0.22) {
                double boost = made == DpNpcMadeHandCategory.MIDDLE_PAIR ? 0.34 : 0.40;
                baseFold = Math.min(1.0, baseFold + boost + 0.20 * Math.min(1.0, (callRatio - 0.22) / 0.38));
            }
            if ((p.boardDanger == BoardDanger.WET || tex.wet) && callRatio >= 0.18) {
                baseFold = Math.min(1.0, baseFold + 0.26);
            }
        }
        if (made == DpNpcMadeHandCategory.TOP_PAIR_TOP_KICKER
                && (p.boardDanger == BoardDanger.WET || tex.wet)
                && callRatio >= 0.55) {
            baseFold = Math.min(1.0, baseFold + 0.18);
        }
        if (ctx.activeVillains >= 2 && made.ordinal() <= DpNpcMadeHandCategory.TOP_PAIR_TOP_KICKER.ordinal()) {
            baseFold = Math.min(1.0, baseFold + 0.12 + 0.06 * Math.min(2.0, ctx.activeVillains - 1));
        }

        if (DpNpcHeroCall.shouldHeroCall(p, stage, callAmount, made, draw, ctx)) {
            DpNpcPostflopTraceHooks.heroCall();
            return null;
        }

        double foldProb = Math.min(1.0, Math.max(0.0, baseFold * (1.0 - 0.35 * p.callStation)));
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

    /**
     * NIT 决策分叉：湿面顶对弱踢脚 / 中对 facing ≥1/3 pot 高概率弃牌（显著紧于 TAG）。
     */
    private static BotAction tryNitMarginalFoldGate(
            DpNpcRuleDecisionParams p,
            int callAmount,
            DpNpcMadeHandCategory made,
            double callRatio,
            boolean wetBoard,
            DpUtilSmartContext ctx) {
        if (!wetBoard || callRatio < 0.33) {
            return null;
        }
        double foldProb = 0.0;
        if (made == DpNpcMadeHandCategory.TOP_PAIR_WEAK_KICKER) {
            foldProb = 0.74 + 0.18 * Math.min(1.0, (callRatio - 0.33) / 0.35);
        } else if (made == DpNpcMadeHandCategory.MIDDLE_PAIR
                || made == DpNpcMadeHandCategory.BOTTOM_PAIR) {
            foldProb = 0.68 + 0.20 * Math.min(1.0, (callRatio - 0.33) / 0.40);
        } else {
            return null;
        }
        if (ctx.activeVillains >= 2) {
            foldProb = Math.min(1.0, foldProb + 0.08);
        }
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
            DpNpcPostflopTraceHooks.foldRollHit(foldProb, foldProb);
            return new BotAction(BotActionType.FOLD, 0);
        }
        DpNpcPostflopTraceHooks.foldRollMiss(foldProb, foldProb, DpNpcEngine.getHandPlanType(p.bot));
        return null;
    }

    private static BotAction decideNoBet(
            DpNpcRuleDecisionParams p,
            String stage,
            DpNpcMadeHandCategory made,
            DpNpcDrawCategory draw,
            DpBoardTexture tex,
            HandPlanType plan,
            DpUtilSmartContext ctx) {
        if (DpNpcEngine.shouldSkipAggressiveActionByPlan(p.bot, stage)) {
            if (!made.isAtLeast(DpNpcMadeHandCategory.TWO_PAIR)) {
                DpNpcPostflopTraceHooks.skipAggressive(plan, stage, made);
                DpNpcPostflopTraceHooks.postflopAction("CALL_OR_CHECK", "check（plan skip aggressive）");
                return new BotAction(BotActionType.CALL_OR_CHECK, 0);
            }
        }

        if (made == DpNpcMadeHandCategory.HIGH_CARD) {
            return decideNoBetHighCard(p, stage, draw, plan, tex, ctx);
        }

        if (made.ordinal() <= DpNpcMadeHandCategory.TOP_PAIR_WEAK_KICKER.ordinal()
                && !DpNpcPostflopFormula.hasSemiBluffDraw(draw)) {
            DpNpcPostflopTraceHooks.postflopAction("CALL_OR_CHECK", "check（弱牌无听牌）");
            return new BotAction(BotActionType.CALL_OR_CHECK, 0);
        }

        if (made == DpNpcMadeHandCategory.TOP_PAIR_TOP_KICKER
                && (tex.wet || tex.dangerousForTopPair())) {
            if (plan == HandPlanType.POT_CONTROL || plan == HandPlanType.GIVE_UP) {
                DpNpcPostflopTraceHooks.postflopAction("CALL_OR_CHECK", "check（pot control / give up）");
                return new BotAction(BotActionType.CALL_OR_CHECK, 0);
            }
            double wetTptk = NIT_CBET_FREQ * 0.35;
            if (p.random.nextDouble() < wetTptk) {
                double factor = DpNpcPostflopFormula.cbetPotFactor(made, tex, stage) * 0.65;
                DpNpcPostflopTraceHooks.valueBetRoll(wetTptk, factor);
                DpNpcPostflopTraceHooks.postflopAction("RAISE", "wet TPTK value bet");
                return raisePotFraction(p, stage, factor);
            }
            DpNpcPostflopTraceHooks.valueBetRollMiss(wetTptk);
            DpNpcPostflopTraceHooks.postflopAction("CALL_OR_CHECK", "check（wet TPTK miss）");
            return new BotAction(BotActionType.CALL_OR_CHECK, 0);
        }

        double valueBetProb = nitNoBetValueProb(made, tex, stage, plan, ctx, p);
        if (p.random.nextDouble() < valueBetProb) {
            if (DpNpcEngine.shouldSkipAggressiveActionByPlan(p.bot, stage)
                    && !made.isAtLeast(DpNpcMadeHandCategory.TWO_PAIR)) {
                DpNpcPostflopTraceHooks.skipAggressive(plan, stage, made);
                DpNpcPostflopTraceHooks.postflopAction("CALL_OR_CHECK", "check（plan skip aggressive）");
                return new BotAction(BotActionType.CALL_OR_CHECK, 0);
            }
            double factor = DpNpcPostflopFormula.cbetPotFactor(made, tex, stage);
            DpNpcPostflopTraceHooks.valueBetRoll(valueBetProb, factor);
            DpNpcPostflopTraceHooks.postflopAction("RAISE", "value bet potFraction=" + String.format("%.2f", factor));
            return raisePotFraction(p, stage, factor);
        }
        DpNpcPostflopTraceHooks.valueBetRollMiss(valueBetProb);
        DpNpcPostflopTraceHooks.postflopAction("CALL_OR_CHECK", "check（no bet line）");
        return new BotAction(BotActionType.CALL_OR_CHECK, 0);
    }

    private static BotAction decideNoBetHighCard(
            DpNpcRuleDecisionParams p,
            String stage,
            DpNpcDrawCategory draw,
            HandPlanType plan,
            DpBoardTexture tex,
            DpUtilSmartContext ctx) {
        if ("river".equals(stage)) {
            double riverBluff = p.bluffFrequency * 0.08;
            if (ctx.activeVillains >= 2) {
                riverBluff *= 0.25;
            }
            if (tex.wet) {
                riverBluff *= 0.35;
            }
            if (p.random.nextDouble() < riverBluff) {
                return raisePotFraction(p, stage, RuleNpcConfig.CBET_BASE_WEAK * 0.55);
            }
            return new BotAction(BotActionType.CALL_OR_CHECK, 0);
        }
        if (plan == HandPlanType.BLUFF && DpNpcPostflopFormula.hasSemiBluffDraw(draw)) {
            if (tex.wet || ctx.activeVillains >= 2) {
                return new BotAction(BotActionType.CALL_OR_CHECK, 0);
            }
            double semi = DpNpcPostflopFormula.semiBluffProb(
                    DpNpcMadeHandCategory.HIGH_CARD, draw, 0.06 + p.bluffFrequency * 0.12);
            semi = Math.min(semi, 0.05);
            if (p.random.nextDouble() < semi) {
                return raisePotFraction(p, stage, RuleNpcConfig.CBET_BASE_WEAK * 0.55);
            }
        }
        return new BotAction(BotActionType.CALL_OR_CHECK, 0);
    }

    /**
     * NIT 价值下注概率：仅强牌主动下注，顶对湿面控池。
     */
    private static double nitNoBetValueProb(
            DpNpcMadeHandCategory made,
            DpBoardTexture tex,
            String stage,
            HandPlanType plan,
            DpUtilSmartContext ctx,
            DpNpcRuleDecisionParams p) {
        double prob = DpNpcPostflopFormula.valueBetProb(made, tex, stage, plan);
        double aggro = 0.45 + 0.35 * p.aggression;
        if (plan == HandPlanType.VALUE) {
            aggro *= 1.06;
        } else if (plan == HandPlanType.POT_CONTROL || plan == HandPlanType.GIVE_UP) {
            aggro *= 0.55;
        }
        prob *= aggro;

        if (made.isAtLeast(DpNpcMadeHandCategory.TRIPS)) {
            prob = Math.max(prob, 0.78);
        } else if (made.isAtLeast(DpNpcMadeHandCategory.TWO_PAIR)) {
            prob = Math.max(prob, tex.dangerousForTwoPairPlus() ? 0.48 : 0.62);
        } else if (made == DpNpcMadeHandCategory.TOP_PAIR_TOP_KICKER) {
            if (tex.isDry()) {
                prob = Math.min(prob, NIT_CBET_FREQ);
            } else {
                prob = Math.min(prob, NIT_CBET_FREQ * 0.40);
            }
        } else {
            prob = Math.min(prob, 0.08);
        }

        if (ctx.activeVillains >= 2) {
            if (made.ordinal() <= DpNpcMadeHandCategory.TOP_PAIR_TOP_KICKER.ordinal()) {
                prob *= 0.55;
            }
        }

        if ("flop".equals(stage) && made.isAtLeast(DpNpcMadeHandCategory.TOP_PAIR_TOP_KICKER)) {
            double cap = made.isAtLeast(DpNpcMadeHandCategory.TWO_PAIR)
                    ? 0.65
                    : NIT_CBET_FREQ;
            prob = Math.min(prob, cap);
        }
        return Math.min(0.82, Math.max(0.02, prob));
    }

    private static BotAction decideFacingBet(
            DpNpcRuleDecisionParams p,
            String stage,
            int callAmount,
            DpNpcMadeHandCategory made,
            DpNpcDrawCategory draw,
            DpBoardTexture tex,
            HandPlanType plan,
            DpUtilSmartContext ctx,
            double commitThreshold) {
        double raiseProb = DpNpcPostflopFormula.raiseProb(made, draw, tex, plan);
        double aggro = (0.45 + 0.35 * p.aggression);
        if (plan == HandPlanType.VALUE) {
            aggro *= 1.08;
        } else if (plan == HandPlanType.POT_CONTROL || plan == HandPlanType.GIVE_UP) {
            aggro *= 0.55;
        } else if (plan == HandPlanType.BLUFF) {
            aggro *= 0.35;
        }
        raiseProb *= aggro * 0.82;

        if (made == DpNpcMadeHandCategory.HIGH_CARD) {
            raiseProb *= 0.18;
        } else if (made.ordinal() <= DpNpcMadeHandCategory.TOP_PAIR_WEAK_KICKER.ordinal()) {
            raiseProb *= 0.55;
        }
        if (ctx.activeVillains >= 2) {
            raiseProb *= 0.50;
        }
        raiseProb = Math.min(0.55, Math.max(0.02, raiseProb));

        if (p.random.nextDouble() > raiseProb || p.chips <= callAmount) {
            DpNpcPostflopTraceHooks.raiseRollMiss(raiseProb, aggro, plan, made);
            DpNpcPostflopTraceHooks.postflopAction("CALL_OR_CHECK", "面对下注跟注");
            return new BotAction(BotActionType.CALL_OR_CHECK, 0);
        }

        DpNpcPostflopTraceHooks.raiseRoll(raiseProb, aggro, plan, made);
        int bb = p.room.getBigBlindChips();
        int extraMin = DpNpcPostflopFormula.raiseExtraMinBb(made);
        int extraMax = DpNpcPostflopFormula.raiseExtraMaxBb(made, stage);
        EscalationResult esc = DpNpcRaiseEscalation.computeFacingBetRaise(
                p.room, callAmount, p.chips, made, stage, p.type, extraMin, extraMax, p.random);
        if (esc.suggestJam && p.random.nextDouble() < 0.45) {
            DpNpcPostflopTraceHooks.postflopAction("ALL_IN", "all-in facing bet（escalation jam）");
            return new BotAction(BotActionType.ALL_IN, p.chips);
        }
        int raiseAmount = esc.raiseAmount;
        if (raiseAmount <= callAmount) {
            DpNpcPostflopTraceHooks.postflopAction("CALL_OR_CHECK", "跟注（raise amount too small）");
            return new BotAction(BotActionType.CALL_OR_CHECK, 0);
        }
        int heroInvestAfter = p.bot.getBet() + raiseAmount;
        if (heroInvestAfter > commitThreshold) {
            return commitThresholdAction(p, ctx, callAmount, made, commitThreshold, heroInvestAfter);
        }
        if (DpNpcEngine.shouldSkipAggressiveActionByPlan(p.bot, stage)
                && !made.isAtLeast(DpNpcMadeHandCategory.TWO_PAIR)) {
            DpNpcPostflopTraceHooks.skipAggressive(plan, stage, made);
            DpNpcPostflopTraceHooks.postflopAction("CALL_OR_CHECK", "check/call（plan skip aggressive）");
            return new BotAction(BotActionType.CALL_OR_CHECK, 0);
        }
        DpNpcEngine.consumeOneBarrelIfAny(p.bot, stage);
        DpNpcPostflopTraceHooks.postflopAction("RAISE", "raise facing bet amount=" + raiseAmount);
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
            DpNpcMadeHandCategory made,
            double commitThreshold,
            int heroInvestAfter) {
        double y = p.random.nextDouble();
        if (made.isAtLeast(DpNpcMadeHandCategory.TRIPS)) {
            y = DpNpcEngine.skewCommitThresholdRandom(y, ctx, callAmount, NIT_FOLD_TIGHTNESS, false);
            BotAction action = y < 0.75
                    ? new BotAction(BotActionType.CALL_OR_CHECK, 0)
                    : new BotAction(BotActionType.ALL_IN, p.chips);
            DpNpcPostflopTraceHooks.commitThreshold(
                    commitThreshold, heroInvestAfter, y < 0.75 ? "call" : "jam", action.getType());
            DpNpcPostflopTraceHooks.postflopAction(action.getType().name(), "commit threshold → " + action.getType().name());
            return action;
        }
        if (made.isAtLeast(DpNpcMadeHandCategory.TWO_PAIR)) {
            y = DpNpcEngine.skewCommitThresholdRandom(y, ctx, callAmount, NIT_FOLD_TIGHTNESS, false);
            BotAction action = y < 0.82
                    ? new BotAction(BotActionType.CALL_OR_CHECK, 0)
                    : new BotAction(BotActionType.ALL_IN, p.chips);
            DpNpcPostflopTraceHooks.commitThreshold(
                    commitThreshold, heroInvestAfter, y < 0.82 ? "call" : "jam", action.getType());
            DpNpcPostflopTraceHooks.postflopAction(action.getType().name(), "commit threshold → " + action.getType().name());
            return action;
        }
        if (DpNpcHardConstraints.mustNotFold(
                made,
                p.handSnapshot != null ? p.handSnapshot.handStrength : null,
                callAmount,
                ctx.equityEst,
                false)) {
            DpNpcPostflopTraceHooks.commitThreshold(
                    commitThreshold, heroInvestAfter, "call", BotActionType.CALL_OR_CHECK);
            DpNpcPostflopTraceHooks.postflopAction("CALL_OR_CHECK", "commit threshold → call（must not fold）");
            return new BotAction(BotActionType.CALL_OR_CHECK, 0);
        }
        if (made.ordinal() >= DpNpcMadeHandCategory.MIDDLE_PAIR.ordinal()
                && made.ordinal() <= DpNpcMadeHandCategory.TOP_PAIR_WEAK_KICKER.ordinal()) {
            y = DpNpcEngine.skewCommitThresholdRandom(y, ctx, callAmount, NIT_FOLD_TIGHTNESS, true);
            BotAction marginal = y < 0.55
                    ? new BotAction(BotActionType.CALL_OR_CHECK, 0)
                    : new BotAction(BotActionType.FOLD, 0);
            DpNpcPostflopTraceHooks.commitThreshold(
                    commitThreshold, heroInvestAfter, y < 0.55 ? "call" : "fold", marginal.getType());
            DpNpcPostflopTraceHooks.postflopAction(marginal.getType().name(), "commit threshold → " + marginal.getType().name());
            return guardFold(marginal, p, made, callAmount, ctx);
        }
        y = DpNpcEngine.skewCommitThresholdRandom(y, ctx, callAmount, NIT_FOLD_TIGHTNESS, false);
        BotAction weak = y < 0.78
                ? new BotAction(BotActionType.FOLD, 0)
                : new BotAction(BotActionType.CALL_OR_CHECK, 0);
        DpNpcPostflopTraceHooks.commitThreshold(
                commitThreshold, heroInvestAfter, y < 0.78 ? "fold" : "call", weak.getType());
        DpNpcPostflopTraceHooks.postflopAction(weak.getType().name(), "commit threshold → " + weak.getType().name());
        return guardFold(weak, p, made, callAmount, ctx);
    }

    private static BotAction guardFold(
            BotAction action,
            DpNpcRuleDecisionParams p,
            DpNpcMadeHandCategory made,
            int callAmount,
            DpUtilSmartContext ctx) {
        if (action.getType() != BotActionType.FOLD) {
            return action;
        }
        if (DpNpcHardConstraints.mustNotFold(
                made,
                p.handSnapshot != null ? p.handSnapshot.handStrength : null,
                callAmount,
                ctx.equityEst,
                callAmount >= p.chips)) {
            return new BotAction(BotActionType.CALL_OR_CHECK, 0);
        }
        return action;
    }
}
