package com.example.mgdemoplus.npc.strategypro.postflop.lag;

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
import com.example.mgdemoplus.utils.DpUtilSmartContext;

/**
 * LAG 独立翻后策略：松凶，高 cbet/raise，听牌 semi-bluff；空气面对大注有基本纪律。
 * <p>禁止套壳旧 LooseAggro 聚合决策类。</p>
 */
public final class DpNpcLagPostflopStrategy {

    /** LAG preset cbet 频率基准（§4.3 ≈79%） */
    private static final double LAG_CBET_FREQ = 0.79;
    /** 相对 TAG（1.0）略松 — 边缘牌更愿意继续 */
    private static final double LAG_FOLD_TIGHTNESS = 0.88;

    private DpNpcLagPostflopStrategy() {
    }

    public static BotAction decide(DpNpcRuleDecisionParams p) {
        BoardDanger bd = p.boardDanger;
        String stage = p.room.getCurrentStage();
        int callAmount = Math.max(0, p.room.getCurrentBetToCall() - p.bot.getBet());
        DpUtilSmartContext ctx = DpNpcEngine.buildSmartContext(
                p.room, p.bot, p.handSnapshot, stage, callAmount, p.random);

        DpNpcEngine.initHandPlanIfNeededForPostflop(
                p.room, p.bot, BotType.LAG, p.handSnapshot, bd, p.position, ctx, p.random);
        DpNpcEngine.updateHandPlanForLaterStreetIfNeeded(
                p.room, p.bot, BotType.LAG, p.handSnapshot, bd, ctx);

        DpNpcMadeHandCategory made = DpNpcPostflopFormula.madeOrHigh(p.handSnapshot);
        DpNpcDrawCategory draw = DpNpcPostflopFormula.drawOrNone(p.handSnapshot);
        DpBoardTexture tex = DpNpcPostflopFormula.textureOrDry(p.handSnapshot);
        HandPlanType plan = DpNpcEngine.getHandPlanType(p.bot);

        double commitFactor = lagCommitFactor(made, draw, tex, bd, p, ctx);
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

    private static double lagCommitFactor(
            DpNpcMadeHandCategory made,
            DpNpcDrawCategory draw,
            DpBoardTexture tex,
            BoardDanger bd,
            DpNpcRuleDecisionParams p,
            DpUtilSmartContext ctx) {
        double factor = DpNpcPostflopFormula.commitFactor(made, draw, tex, bd);
        if (!"preflop".equals(p.room.getCurrentStage()) && bd == BoardDanger.WET) {
            factor *= 0.82;
        }
        if (!"preflop".equals(p.room.getCurrentStage())
                && ctx.stackCtx != null
                && ctx.stackCtx.avgStackBB >= RuleNpcConfig.DEEP_TABLE_AVG_BB
                && !made.isAtLeast(DpNpcMadeHandCategory.TWO_PAIR)) {
            factor *= 0.88;
        }
        if (!"preflop".equals(p.room.getCurrentStage())) {
            double spr = DpNpcEngine.computeHeroPotSpr(p.room, p.bot);
            if (spr < 2.5 && made.isAtLeast(DpNpcMadeHandCategory.TRIPS)) {
                factor = Math.min(0.94, factor * 1.08);
            } else if (spr > 14.0
                    && made.ordinal() <= DpNpcMadeHandCategory.TOP_PAIR_WEAK_KICKER.ordinal()) {
                factor *= 0.90;
            }
        }
        // LAG 比 MANIAC 更选择性 — 整体 commit 略低于旧 LooseAggro LAG profile
        factor *= 0.88;
        return Math.max(0.18, Math.min(0.92, factor));
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
                made, draw, tex, callRatio, p.boardDanger, LAG_FOLD_TIGHTNESS);
        baseFold = Math.min(1.0, baseFold + DpNpcHardConstraints.multiwayFoldBoost(
                made, ctx.activeVillains, p.checkRaiseFear, callRatio));
        baseFold = DpNpcEngine.adjustFoldProbForEquityVsPotOdds(
                baseFold, callAmount, ctx.potOdds, ctx.equityEst, ctx.activeVillains, LAG_FOLD_TIGHTNESS);

        // LAG 纪律：纯空气面对大注比 TAG 更快弃牌（预埋与 MANIAC 差异）
        if (made == DpNpcMadeHandCategory.HIGH_CARD && !DpNpcPostflopFormula.hasSemiBluffDraw(draw)) {
            if (callRatio >= 0.38) {
                if (!DpNpcHardConstraints.mustNotFold(
                        made,
                        p.handSnapshot != null ? p.handSnapshot.handStrength : null,
                        callAmount,
                        ctx.equityEst,
                        callAmount >= p.chips)) {
                    return new BotAction(BotActionType.FOLD, 0);
                }
                return null;
            }
            if (callRatio >= 0.28) {
                baseFold = Math.min(1.0, baseFold + 0.22);
            }
            if (ctx.potOdds > 0.52) {
                baseFold = Math.min(1.0, baseFold + 0.10);
            }
        } else if (made == DpNpcMadeHandCategory.HIGH_CARD
                && DpNpcPostflopFormula.hasSemiBluffDraw(draw)) {
            baseFold = Math.max(0.0, baseFold * 0.72);
        } else if (ctx.potOdds > 0 && ctx.potOdds < 0.24
                && made.isAtLeast(DpNpcMadeHandCategory.TOP_PAIR_WEAK_KICKER)) {
            baseFold = Math.max(0.0, baseFold * 0.88);
        }

        ActionCredibility cred = ctx.credibility;
        if (cred == ActionCredibility.HIGH || ctx.showdownBluffiness < 0.1) {
            baseFold = Math.min(1.0, baseFold + 0.08);
        } else if (cred == ActionCredibility.LOW || ctx.showdownBluffiness > 0.4) {
            baseFold = Math.max(0.0, baseFold * 0.75);
        }

        if (plan == HandPlanType.GIVE_UP && callAmount > 0
                && !made.isAtLeast(DpNpcMadeHandCategory.TRIPS)) {
            baseFold = Math.min(1.0, baseFold + 0.12);
        }
        if (ctx.counterStrategy != null && callAmount > 0
                && ctx.counterStrategy.foldMoreToBigBets && callRatio > 0.5) {
            baseFold = Math.min(1.0, baseFold + 0.08);
        }

        if (made == DpNpcMadeHandCategory.MIDDLE_PAIR || made == DpNpcMadeHandCategory.BOTTOM_PAIR) {
            if (callRatio >= 0.38) {
                double boost = made == DpNpcMadeHandCategory.MIDDLE_PAIR ? 0.18 : 0.24;
                baseFold = Math.min(1.0, baseFold + boost + 0.12 * Math.min(1.0, (callRatio - 0.38) / 0.42));
            }
        }

        double foldProb = Math.min(1.0, Math.max(0.0, baseFold * (1.0 - 0.42 * p.callStation)));
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
            return new BotAction(BotActionType.FOLD, 0);
        }
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
            if (made == DpNpcMadeHandCategory.HIGH_CARD && plan == HandPlanType.GIVE_UP) {
                return new BotAction(BotActionType.CALL_OR_CHECK, 0);
            }
            if (!made.isAtLeast(DpNpcMadeHandCategory.TOP_PAIR_TOP_KICKER)) {
                if (made == DpNpcMadeHandCategory.HIGH_CARD
                        && "flop".equals(stage)
                        && DpNpcPostflopFormula.hasSemiBluffDraw(draw)) {
                    // flop 听牌仍可能 semi-bluff
                } else {
                    return new BotAction(BotActionType.CALL_OR_CHECK, 0);
                }
            }
        }

        if (made == DpNpcMadeHandCategory.HIGH_CARD) {
            return decideNoBetHighCard(p, stage, draw, plan, tex, ctx);
        }

        if (made.ordinal() <= DpNpcMadeHandCategory.TOP_PAIR_WEAK_KICKER.ordinal()
                && !DpNpcPostflopFormula.hasSemiBluffDraw(draw)) {
            if (plan == HandPlanType.POT_CONTROL) {
                return new BotAction(BotActionType.CALL_OR_CHECK, 0);
            }
        }

        double valueBetProb = lagNoBetValueProb(made, tex, stage, plan, ctx, p);
        if (p.random.nextDouble() < valueBetProb) {
            if (DpNpcEngine.shouldSkipAggressiveActionByPlan(p.bot, stage)
                    && !made.isAtLeast(DpNpcMadeHandCategory.TOP_PAIR_TOP_KICKER)) {
                return new BotAction(BotActionType.CALL_OR_CHECK, 0);
            }
            double factor = DpNpcPostflopFormula.cbetPotFactor(made, tex, stage);
            return raisePotFraction(p, stage, factor);
        }
        return new BotAction(BotActionType.CALL_OR_CHECK, 0);
    }

    private static BotAction decideNoBetHighCard(
            DpNpcRuleDecisionParams p,
            String stage,
            DpNpcDrawCategory draw,
            HandPlanType plan,
            DpBoardTexture tex,
            DpUtilSmartContext ctx) {
        if ("flop".equals(stage)) {
            double flopAggro = LAG_CBET_FREQ * (0.7 + 0.6 * p.aggression);
            if (DpNpcPostflopFormula.hasSemiBluffDraw(draw)) {
                double semi = DpNpcPostflopFormula.semiBluffProb(
                        DpNpcMadeHandCategory.HIGH_CARD, draw, 0.62 + p.bluffFrequency);
                flopAggro = Math.max(flopAggro, semi);
            } else if (plan == HandPlanType.BLUFF || tex.isDry()) {
                flopAggro = Math.max(flopAggro, 0.52 + 0.18 * p.bluffFrequency);
            }
            if (ctx.activeVillains >= 2) {
                flopAggro *= 0.62;
            }
            if (p.random.nextDouble() < Math.min(0.92, flopAggro)) {
                return raisePotFraction(p, stage, RuleNpcConfig.CBET_BASE_WEAK);
            }
            return new BotAction(BotActionType.CALL_OR_CHECK, 0);
        }
        if ("river".equals(stage) && tex.isDry()
                && (plan == HandPlanType.BLUFF || ctx.showdownBluffiness > 0.35)) {
            double riverBluff = p.bluffFrequency * 0.32;
            if (ctx.credibility == ActionCredibility.HIGH || ctx.showdownBluffiness < 0.12) {
                riverBluff *= 1.18;
            }
            if (ctx.activeVillains >= 2) {
                riverBluff *= 0.48;
            }
            if (p.random.nextDouble() < riverBluff) {
                return raisePotFraction(p, stage, RuleNpcConfig.CBET_BASE_WEAK);
            }
        }
        if (plan == HandPlanType.BLUFF && DpNpcPostflopFormula.hasSemiBluffDraw(draw)) {
            double semi = DpNpcPostflopFormula.semiBluffProb(
                    DpNpcMadeHandCategory.HIGH_CARD, draw, 0.55 + p.bluffFrequency);
            if (ctx.activeVillains >= 2) {
                semi *= 0.60;
            }
            if (p.random.nextDouble() < semi) {
                return raisePotFraction(p, stage, RuleNpcConfig.CBET_BASE_WEAK);
            }
        }
        return new BotAction(BotActionType.CALL_OR_CHECK, 0);
    }

    private static double lagNoBetValueProb(
            DpNpcMadeHandCategory made,
            DpBoardTexture tex,
            String stage,
            HandPlanType plan,
            DpUtilSmartContext ctx,
            DpNpcRuleDecisionParams p) {
        double prob = DpNpcPostflopFormula.valueBetProb(made, tex, stage, plan);
        double aggro = 0.82 + 0.72 * p.aggression;
        if (plan == HandPlanType.VALUE) {
            aggro *= 1.14;
        } else if (plan == HandPlanType.POT_CONTROL) {
            aggro *= 0.86;
        } else if (plan == HandPlanType.BLUFF) {
            aggro *= 1.08;
        }
        prob *= aggro;

        if (made.isAtLeast(DpNpcMadeHandCategory.TRIPS)) {
            prob = Math.max(prob, 0.88);
        } else if (made.isAtLeast(DpNpcMadeHandCategory.TWO_PAIR)) {
            prob = Math.max(prob, tex.dangerousForTwoPairPlus() ? 0.68 : 0.82);
        } else if (made.isAtLeast(DpNpcMadeHandCategory.TOP_PAIR_TOP_KICKER)) {
            if (tex.isDry()) {
                prob = Math.max(prob, plan == HandPlanType.VALUE ? 0.88 : 0.76);
            } else if (!tex.dangerousForTopPair()) {
                prob = Math.max(prob, plan == HandPlanType.VALUE ? 0.78 : 0.58);
            }
        } else if (made.ordinal() <= DpNpcMadeHandCategory.TOP_PAIR_WEAK_KICKER.ordinal()
                && made.ordinal() >= DpNpcMadeHandCategory.MIDDLE_PAIR.ordinal()) {
            prob = Math.max(prob, 0.42);
        }

        if (ctx.activeVillains >= 2) {
            if (made.ordinal() <= DpNpcMadeHandCategory.TOP_PAIR_TOP_KICKER.ordinal()
                    && made.ordinal() >= DpNpcMadeHandCategory.MIDDLE_PAIR.ordinal()) {
                prob *= 0.68;
            } else if (made == DpNpcMadeHandCategory.HIGH_CARD) {
                prob *= 0.42;
            }
        }

        if ("flop".equals(stage) && made.isAtLeast(DpNpcMadeHandCategory.TOP_PAIR_WEAK_KICKER)) {
            prob = Math.max(prob, LAG_CBET_FREQ * (plan == HandPlanType.VALUE ? 1.0 : 0.92));
        }
        return Math.min(0.96, Math.max(0.08, prob));
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
        double aggro = (0.82 + 0.72 * p.aggression);
        if (plan == HandPlanType.VALUE) {
            aggro *= 1.14;
        } else if (plan == HandPlanType.POT_CONTROL) {
            aggro *= 0.88;
        } else if (plan == HandPlanType.BLUFF) {
            aggro *= 1.12;
        }
        raiseProb *= aggro;

        // LAG：听牌 semi-bluff raise；顶对 raise 保护
        if (DpNpcPostflopFormula.hasSemiBluffDraw(draw)) {
            raiseProb = Math.max(raiseProb, 0.28 + 0.22 * p.aggression);
        } else if (made == DpNpcMadeHandCategory.TOP_PAIR_TOP_KICKER) {
            raiseProb = Math.max(raiseProb, 0.22 + 0.18 * p.aggression);
        } else if (made == DpNpcMadeHandCategory.HIGH_CARD) {
            raiseProb *= 0.35;
        }

        if (ctx.activeVillains >= 2) {
            if (made == DpNpcMadeHandCategory.HIGH_CARD) {
                raiseProb *= 0.38;
            } else if (made.ordinal() <= DpNpcMadeHandCategory.TOP_PAIR_WEAK_KICKER.ordinal()) {
                raiseProb *= 0.78;
            }
        }
        raiseProb = Math.min(0.92, Math.max(0.06, raiseProb));

        if (p.random.nextDouble() > raiseProb || p.chips <= callAmount) {
            return new BotAction(BotActionType.CALL_OR_CHECK, 0);
        }

        int bb = p.room.getBigBlindChips();
        int extraMin = DpNpcPostflopFormula.raiseExtraMinBb(made);
        int extraMax = DpNpcPostflopFormula.raiseExtraMaxBb(made, stage);
        if (DpNpcPostflopFormula.hasSemiBluffDraw(draw)) {
            extraMin = Math.max(extraMin, 2);
            extraMax = Math.max(extraMax, 4);
        }
        int extraBB = extraMin + p.random.nextInt(Math.max(1, extraMax - extraMin + 1));
        int target = callAmount + extraBB * bb;
        if (!"river".equals(stage)
                && made.ordinal() <= DpNpcMadeHandCategory.TOP_PAIR_WEAK_KICKER.ordinal()
                && made.ordinal() >= DpNpcMadeHandCategory.MIDDLE_PAIR.ordinal()) {
            target = (int) Math.round(target * 0.85);
        }
        int raiseAmount = Math.min(p.chips, target);
        raiseAmount = snapRaiseToSb(p, callAmount, raiseAmount, made, bb);

        if (raiseAmount <= callAmount) {
            return new BotAction(BotActionType.CALL_OR_CHECK, 0);
        }
        int heroInvestAfter = p.bot.getBet() + raiseAmount;
        if (heroInvestAfter > commitThreshold) {
            return commitThresholdAction(p, ctx, callAmount, made, draw);
        }
        if (DpNpcEngine.shouldSkipAggressiveActionByPlan(p.bot, stage)
                && !made.isAtLeast(DpNpcMadeHandCategory.TWO_PAIR)
                && !DpNpcPostflopFormula.hasSemiBluffDraw(draw)) {
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
            DpNpcMadeHandCategory made,
            DpNpcDrawCategory draw) {
        double y = p.random.nextDouble();
        if (made.isAtLeast(DpNpcMadeHandCategory.TRIPS)) {
            y = DpNpcEngine.skewCommitThresholdRandom(y, ctx, callAmount, LAG_FOLD_TIGHTNESS, false);
            return y < 0.72
                    ? new BotAction(BotActionType.CALL_OR_CHECK, 0)
                    : new BotAction(BotActionType.ALL_IN, p.chips);
        }
        if (made.isAtLeast(DpNpcMadeHandCategory.TOP_PAIR_TOP_KICKER)) {
            y = DpNpcEngine.skewCommitThresholdRandom(y, ctx, callAmount, LAG_FOLD_TIGHTNESS, false);
            return y < 0.68
                    ? new BotAction(BotActionType.CALL_OR_CHECK, 0)
                    : new BotAction(BotActionType.ALL_IN, p.chips);
        }
        if (DpNpcPostflopFormula.hasSemiBluffDraw(draw)) {
            y = DpNpcEngine.skewCommitThresholdRandom(y, ctx, callAmount, LAG_FOLD_TIGHTNESS, true);
            return y < 0.55
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
        if (made == DpNpcMadeHandCategory.HIGH_CARD) {
            y = DpNpcEngine.skewCommitThresholdRandom(y, ctx, callAmount, LAG_FOLD_TIGHTNESS, true);
            BotAction air = y < 0.78
                    ? new BotAction(BotActionType.FOLD, 0)
                    : new BotAction(BotActionType.CALL_OR_CHECK, 0);
            return guardFold(air, p, made, callAmount, ctx);
        }
        if (made.ordinal() >= DpNpcMadeHandCategory.MIDDLE_PAIR.ordinal()
                && made.ordinal() <= DpNpcMadeHandCategory.TOP_PAIR_WEAK_KICKER.ordinal()) {
            y = DpNpcEngine.skewCommitThresholdRandom(y, ctx, callAmount, LAG_FOLD_TIGHTNESS, true);
            BotAction marginal = y < 0.62
                    ? new BotAction(BotActionType.CALL_OR_CHECK, 0)
                    : new BotAction(BotActionType.FOLD, 0);
            return guardFold(marginal, p, made, callAmount, ctx);
        }
        y = DpNpcEngine.skewCommitThresholdRandom(y, ctx, callAmount, LAG_FOLD_TIGHTNESS, false);
        BotAction weak = y < 0.72
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
