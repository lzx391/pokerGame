package com.example.mgdemoplus.npc.strategypro.postflop.tag;

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
import com.example.mgdemoplus.npc.trace.DpNpcTagDecisionTraceCollector;
import com.example.mgdemoplus.utils.DpUtilSmartContext;

/**
 * TAG 独立翻后策略：紧凶标杆，强牌主动价值下注，VALUE / POT_CONTROL 分线清晰。
 * <p>禁止套壳旧 TightAggro 聚合决策类。</p>
 */
public final class DpNpcTagPostflopStrategy {

    /** TAG preset cbet 频率基准（§4.4 ≈82%） */
    private static final double TAG_CBET_FREQ = 0.82;
    /** 相对 NIT（1.18）保持基准紧度 */
    private static final double TAG_FOLD_TIGHTNESS = 1.0;

    private DpNpcTagPostflopStrategy() {
    }

    public static BotAction decide(DpNpcRuleDecisionParams p) {
        BoardDanger bd = p.boardDanger;
        String stage = p.room.getCurrentStage();
        int callAmount = Math.max(0, p.room.getCurrentBetToCall() - p.bot.getBet());
        DpUtilSmartContext ctx = DpNpcEngine.buildSmartContext(
                p.room, p.bot, p.handSnapshot, stage, callAmount, p.random);

        DpNpcEngine.initHandPlanIfNeededForPostflop(
                p.room, p.bot, BotType.TAG, p.handSnapshot, bd, p.position, ctx, p.random);
        DpNpcEngine.updateHandPlanForLaterStreetIfNeeded(
                p.room, p.bot, BotType.TAG, p.handSnapshot, bd, ctx);

        DpNpcMadeHandCategory made = DpNpcPostflopFormula.madeOrHigh(p.handSnapshot);
        DpNpcDrawCategory draw = DpNpcPostflopFormula.drawOrNone(p.handSnapshot);
        DpBoardTexture tex = DpNpcPostflopFormula.textureOrDry(p.handSnapshot);
        HandPlanType plan = DpNpcEngine.getHandPlanType(p.bot);

        tracePostflopContext(stage, callAmount, plan, made, draw, tex);

        double commitFactor = tagCommitFactor(made, draw, tex, bd, p, ctx);
        final double commitThreshold = (p.bot.getBet() + p.bot.getChips()) * commitFactor;

        if (callAmount > 0) {
            BotAction foldAction = tryFoldFacingBet(p, stage, callAmount, made, draw, tex, plan, ctx);
            if (foldAction != null) {
                return foldAction;
            }
            return decideFacingBet(p, stage, callAmount, made, draw, tex, plan, ctx, commitThreshold);
        }
        return decideNoBet(p, stage, made, draw, tex, plan, ctx);
    }

    private static double tagCommitFactor(
            DpNpcMadeHandCategory made,
            DpNpcDrawCategory draw,
            DpBoardTexture tex,
            BoardDanger bd,
            DpNpcRuleDecisionParams p,
            DpUtilSmartContext ctx) {
        double factor = DpNpcPostflopFormula.commitFactor(made, draw, tex, bd);
        if (!"preflop".equals(p.room.getCurrentStage()) && bd == BoardDanger.WET) {
            factor *= 0.85;
        }
        if (!"preflop".equals(p.room.getCurrentStage()) && ctx.stackCtx != null) {
            factor = DpNpcRaiseEscalation.adjustDeepStackCommitFactor(
                    factor, made, ctx.stackCtx.avgStackBB);
        }
        if (!"preflop".equals(p.room.getCurrentStage())) {
            double spr = DpNpcEngine.computeHeroPotSpr(p.room, p.bot);
            if (spr < 2.5 && made.isAtLeast(DpNpcMadeHandCategory.TRIPS)) {
                factor = Math.min(0.92, factor * 1.06);
            } else if (spr > 14.0
                    && made.ordinal() <= DpNpcMadeHandCategory.TOP_PAIR_WEAK_KICKER.ordinal()) {
                factor *= 0.88;
            }
        }
        return Math.max(0.15, Math.min(0.9, factor));
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
        double baseFold = DpNpcPostflopFormula.baseFoldProb(
                made, draw, tex, callRatio, p.boardDanger, TAG_FOLD_TIGHTNESS);
        baseFold = Math.min(1.0, baseFold + DpNpcHardConstraints.multiwayFoldBoost(
                made, ctx.activeVillains, p.checkRaiseFear, callRatio));
        baseFold = DpNpcEngine.adjustFoldProbForEquityVsPotOdds(
                baseFold, callAmount, ctx.potOdds, ctx.equityEst, ctx.activeVillains, TAG_FOLD_TIGHTNESS);

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

        // GIVE_UP 仅削弱边缘牌；TRIPS+ 不因计划弃牌
        if (plan == HandPlanType.GIVE_UP && callAmount > 0
                && !made.isAtLeast(DpNpcMadeHandCategory.TRIPS)) {
            baseFold = Math.min(1.0, baseFold + 0.15);
        }
        if (ctx.counterStrategy != null && callAmount > 0
                && ctx.counterStrategy.foldMoreToBigBets && callRatio > 0.5) {
            baseFold = Math.min(1.0, baseFold + 0.1);
        }

        // TAG：中等/弱对面对大注更纪律性弃牌（预埋与 NIT 差异 — NIT 更紧）
        if (made == DpNpcMadeHandCategory.MIDDLE_PAIR || made == DpNpcMadeHandCategory.BOTTOM_PAIR) {
            if (callRatio >= 0.30) {
                double boost = made == DpNpcMadeHandCategory.MIDDLE_PAIR ? 0.26 : 0.32;
                baseFold = Math.min(1.0, baseFold + boost + 0.16 * Math.min(1.0, (callRatio - 0.30) / 0.40));
            }
            if ((p.boardDanger == BoardDanger.WET || tex.wet) && callRatio >= 0.25) {
                baseFold = Math.min(1.0, baseFold + 0.20);
            }
        }
        if (made == DpNpcMadeHandCategory.TOP_PAIR_WEAK_KICKER && callRatio >= 0.55) {
            baseFold = Math.min(1.0, baseFold + 0.08);
        }

        if (DpNpcHeroCall.shouldHeroCall(p, stage, callAmount, made, draw, ctx)) {
            return null;
        }

        double foldProb = Math.min(1.0, Math.max(0.0, baseFold * (1.0 - 0.5 * p.callStation)));
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
            traceFoldResult(baseFold, foldProb);
            return new BotAction(BotActionType.FOLD, 0);
        }
        return null;
    }

    private static void tracePostflopContext(
            String stage,
            int callAmount,
            HandPlanType plan,
            DpNpcMadeHandCategory made,
            DpNpcDrawCategory draw,
            DpBoardTexture tex) {
        if (DpNpcTagDecisionTraceCollector.current() == null) {
            return;
        }
        DpNpcTagDecisionTraceCollector.step(
                "CONTEXT",
                "POSTFLOP_SPOT",
                "stage=" + stage + " callAmount=" + callAmount + " plan=" + plan,
                DpNpcTagDecisionTraceCollector.dataOf(
                        "stage", stage,
                        "callAmount", callAmount,
                        "plan", plan != null ? plan.name() : "",
                        "made", made != null ? made.name() : "",
                        "draw", draw != null ? draw.name() : "",
                        "tex", tex != null ? (tex.wet ? "wet" : "dry") : ""));
    }

    private static void traceFoldResult(double baseFold, double foldProb) {
        if (DpNpcTagDecisionTraceCollector.current() == null) {
            return;
        }
        DpNpcTagDecisionTraceCollector.step(
                "PROB",
                "FOLD_ROLL",
                "baseFold=" + String.format("%.3f", baseFold) + " foldProb=" + String.format("%.3f", foldProb),
                DpNpcTagDecisionTraceCollector.dataOf("baseFold", baseFold, "foldProb", foldProb));
        DpNpcTagDecisionTraceCollector.step("RESULT", "FOLD", "fold facing bet", null);
    }

    private static void tracePostflopResult(String message) {
        if (DpNpcTagDecisionTraceCollector.current() == null) {
            return;
        }
        DpNpcTagDecisionTraceCollector.step("RESULT", "POSTFLOP_ACTION", message, null);
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
            if (made == DpNpcMadeHandCategory.HIGH_CARD && plan == HandPlanType.GIVE_UP) {
                return new BotAction(BotActionType.CALL_OR_CHECK, 0);
            }
            if (!made.isAtLeast(DpNpcMadeHandCategory.TOP_PAIR_TOP_KICKER)) {
                return new BotAction(BotActionType.CALL_OR_CHECK, 0);
            }
        }

        if (made == DpNpcMadeHandCategory.HIGH_CARD) {
            return decideNoBetHighCard(p, stage, draw, plan, tex, ctx);
        }

        if (made.ordinal() <= DpNpcMadeHandCategory.TOP_PAIR_WEAK_KICKER.ordinal()
                && !DpNpcPostflopFormula.hasSemiBluffDraw(draw)) {
            if (plan == HandPlanType.POT_CONTROL || plan == HandPlanType.GIVE_UP) {
                return new BotAction(BotActionType.CALL_OR_CHECK, 0);
            }
        }

        double valueBetProb = tagNoBetValueProb(made, tex, stage, plan, ctx, p);
        if (p.random.nextDouble() < valueBetProb) {
            if (DpNpcEngine.shouldSkipAggressiveActionByPlan(p.bot, stage)
                    && !made.isAtLeast(DpNpcMadeHandCategory.TOP_PAIR_TOP_KICKER)) {
                tracePostflopResult("check (plan skip aggressive)");
                return new BotAction(BotActionType.CALL_OR_CHECK, 0);
            }
            double factor = DpNpcPostflopFormula.cbetPotFactor(made, tex, stage);
            tracePostflopResult("value bet potFraction=" + String.format("%.2f", factor));
            return raisePotFraction(p, stage, factor);
        }
        tracePostflopResult("check (no bet line)");
        return new BotAction(BotActionType.CALL_OR_CHECK, 0);
    }

    private static BotAction decideNoBetHighCard(
            DpNpcRuleDecisionParams p,
            String stage,
            DpNpcDrawCategory draw,
            HandPlanType plan,
            DpBoardTexture tex,
            DpUtilSmartContext ctx) {
        if ("river".equals(stage) && tex.isDry() && plan != HandPlanType.BLUFF) {
            double riverBluff = p.bluffFrequency * 0.22;
            if (ctx.activeVillains >= 2) {
                riverBluff *= 0.55;
            }
            if (p.random.nextDouble() < riverBluff) {
                return raisePotFraction(p, stage, RuleNpcConfig.CBET_BASE_WEAK);
            }
            return new BotAction(BotActionType.CALL_OR_CHECK, 0);
        }
        if (plan == HandPlanType.BLUFF && DpNpcPostflopFormula.hasSemiBluffDraw(draw)) {
            double semi = DpNpcPostflopFormula.semiBluffProb(
                    DpNpcMadeHandCategory.HIGH_CARD, draw, 0.5 + p.bluffFrequency);
            if (ctx.activeVillains >= 2) {
                semi *= 0.65;
            }
            if (p.random.nextDouble() < semi) {
                return raisePotFraction(p, stage, RuleNpcConfig.CBET_BASE_WEAK);
            }
        }
        return new BotAction(BotActionType.CALL_OR_CHECK, 0);
    }

    /**
     * TAG 价值下注概率：干面 TPTK+ 抬高下限，减少无意义慢打。
     */
    private static double tagNoBetValueProb(
            DpNpcMadeHandCategory made,
            DpBoardTexture tex,
            String stage,
            HandPlanType plan,
            DpUtilSmartContext ctx,
            DpNpcRuleDecisionParams p) {
        double prob = DpNpcPostflopFormula.valueBetProb(made, tex, stage, plan);
        double aggro = 0.7 + 0.6 * p.aggression;
        if (plan == HandPlanType.VALUE) {
            aggro *= 1.12;
        } else if (plan == HandPlanType.POT_CONTROL) {
            aggro *= 0.82;
        }
        prob *= aggro;

        if (made.isAtLeast(DpNpcMadeHandCategory.TRIPS)) {
            prob = Math.max(prob, 0.86);
        } else if (made.isAtLeast(DpNpcMadeHandCategory.TWO_PAIR)) {
            prob = Math.max(prob, tex.dangerousForTwoPairPlus() ? 0.62 : 0.78);
        } else if (made == DpNpcMadeHandCategory.TOP_PAIR_TOP_KICKER) {
            if (tex.isDry()) {
                double floor = plan == HandPlanType.VALUE ? 0.85 : 0.72;
                prob = Math.max(prob, floor);
            } else if (!tex.dangerousForTopPair()) {
                prob = Math.max(prob, plan == HandPlanType.VALUE ? 0.72 : 0.48);
            }
        }

        if (ctx.activeVillains >= 2) {
            if (made.ordinal() <= DpNpcMadeHandCategory.TOP_PAIR_TOP_KICKER.ordinal()
                    && made.ordinal() >= DpNpcMadeHandCategory.MIDDLE_PAIR.ordinal()) {
                prob *= 0.72;
            } else if (made == DpNpcMadeHandCategory.HIGH_CARD) {
                prob *= 0.35;
            }
        }

        if ("flop".equals(stage) && made.isAtLeast(DpNpcMadeHandCategory.TOP_PAIR_TOP_KICKER)) {
            prob = Math.max(prob, TAG_CBET_FREQ * (plan == HandPlanType.VALUE ? 1.0 : 0.88));
        }
        return Math.min(0.95, Math.max(0.05, prob));
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
        double aggro = (0.7 + 0.6 * p.aggression);
        if (plan == HandPlanType.VALUE) {
            aggro *= 1.12;
        } else if (plan == HandPlanType.POT_CONTROL) {
            aggro *= 0.82;
        } else if (plan == HandPlanType.BLUFF) {
            aggro *= 1.05;
        }
        raiseProb *= aggro;

        if (ctx.activeVillains >= 2) {
            if (made == DpNpcMadeHandCategory.HIGH_CARD) {
                raiseProb *= 0.40;
            } else if (made.ordinal() <= DpNpcMadeHandCategory.TOP_PAIR_WEAK_KICKER.ordinal()) {
                raiseProb *= 0.74;
            }
        }
        raiseProb = Math.min(0.9, Math.max(0.05, raiseProb));

        if (p.random.nextDouble() > raiseProb || p.chips <= callAmount) {
            tracePostflopResult("call facing bet (raiseProb miss)");
            return new BotAction(BotActionType.CALL_OR_CHECK, 0);
        }

        int extraMin = DpNpcPostflopFormula.raiseExtraMinBb(made);
        int extraMax = DpNpcPostflopFormula.raiseExtraMaxBb(made, stage);
        EscalationResult esc = DpNpcRaiseEscalation.computeFacingBetRaise(
                p.room, callAmount, p.chips, made, stage, p.type, extraMin, extraMax, p.random);
        if (esc.suggestJam && p.random.nextDouble() < 0.55) {
            tracePostflopResult("all-in facing bet (escalation jam)");
            return new BotAction(BotActionType.ALL_IN, p.chips);
        }
        int raiseAmount = esc.raiseAmount;
        if (raiseAmount <= callAmount) {
            tracePostflopResult("call (raise amount too small)");
            return new BotAction(BotActionType.CALL_OR_CHECK, 0);
        }
        int heroInvestAfter = p.bot.getBet() + raiseAmount;
        if (heroInvestAfter > commitThreshold) {
            return commitThresholdAction(p, ctx, callAmount, made);
        }
        if (DpNpcEngine.shouldSkipAggressiveActionByPlan(p.bot, stage)
                && !made.isAtLeast(DpNpcMadeHandCategory.TWO_PAIR)) {
            return new BotAction(BotActionType.CALL_OR_CHECK, 0);
        }
        DpNpcEngine.consumeOneBarrelIfAny(p.bot, stage);
        tracePostflopResult("raise facing bet amount=" + raiseAmount);
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
            y = DpNpcEngine.skewCommitThresholdRandom(y, ctx, callAmount, TAG_FOLD_TIGHTNESS, false);
            return y < 0.8
                    ? new BotAction(BotActionType.CALL_OR_CHECK, 0)
                    : new BotAction(BotActionType.ALL_IN, p.chips);
        }
        if (made.isAtLeast(DpNpcMadeHandCategory.TOP_PAIR_TOP_KICKER)) {
            y = DpNpcEngine.skewCommitThresholdRandom(y, ctx, callAmount, TAG_FOLD_TIGHTNESS, false);
            return y < 0.8
                    ? new BotAction(BotActionType.CALL_OR_CHECK, 0)
                    : new BotAction(BotActionType.ALL_IN, p.chips);
        }
        if (DpNpcHardConstraints.mustNotFold(
                made,
                p.handSnapshot != null ? p.handSnapshot.handStrength : null,
                callAmount,
                ctx.equityEst,
                false)) {
            return new BotAction(BotActionType.CALL_OR_CHECK, 0);
        }
        if (made.ordinal() >= DpNpcMadeHandCategory.MIDDLE_PAIR.ordinal()
                && made.ordinal() <= DpNpcMadeHandCategory.TOP_PAIR_WEAK_KICKER.ordinal()) {
            y = DpNpcEngine.skewCommitThresholdRandom(y, ctx, callAmount, TAG_FOLD_TIGHTNESS, true);
            BotAction marginal = y < 0.7
                    ? new BotAction(BotActionType.CALL_OR_CHECK, 0)
                    : new BotAction(BotActionType.FOLD, 0);
            return guardFold(marginal, p, made, callAmount, ctx);
        }
        y = DpNpcEngine.skewCommitThresholdRandom(y, ctx, callAmount, TAG_FOLD_TIGHTNESS, false);
        BotAction weak = y < 0.85
                ? new BotAction(BotActionType.FOLD, 0)
                : new BotAction(BotActionType.CALL_OR_CHECK, 0);
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
