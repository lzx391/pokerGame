package com.example.mgdemoplus.npc.strategypro.postflop.maniac;

import com.example.mgdemoplus.npc.engine.DpNpcEngine;
import com.example.mgdemoplus.npc.engine.DpNpcEngine.ActionCredibility;
import com.example.mgdemoplus.npc.engine.DpNpcEngine.BoardDanger;
import com.example.mgdemoplus.npc.engine.DpNpcEngine.BotAction;
import com.example.mgdemoplus.npc.engine.DpNpcEngine.BotActionType;
import com.example.mgdemoplus.npc.engine.DpNpcEngine.BotType;
import com.example.mgdemoplus.npc.engine.DpNpcEngine.HandPlanType;
import com.example.mgdemoplus.npc.engine.DpNpcEngine.RuleNpcConfig;
import com.example.mgdemoplus.npc.engine.DpNpcEngine.VillainRangeTier;
import com.example.mgdemoplus.npc.eval.DpBoardTexture;
import com.example.mgdemoplus.npc.eval.DpNpcDrawCategory;
import com.example.mgdemoplus.npc.eval.DpNpcMadeHandCategory;
import com.example.mgdemoplus.npc.eval.DpNpcPostflopFormula;
import com.example.mgdemoplus.npc.strategypro.DpNpcRuleDecisionParams;
import com.example.mgdemoplus.npc.strategypro.l1.DpNpcHardConstraints;
import com.example.mgdemoplus.utils.DpUtilSmartContext;

/**
 * MANIAC 独立翻后策略：极少 fold、高频 raise/all-in，空气也持续施压。
 * <p>禁止套壳旧 LooseAggro 聚合决策类。</p>
 */
public final class DpNpcManiacPostflopStrategy {

    /** MANIAC preset cbet 频率基准（§4.6 ≈88%） */
    private static final double MANIAC_CBET_FREQ = 0.88;
    /** 面对下注基础弃牌率（§4.6 基础 fold &lt; 5%） */
    private static final double MANIAC_BASE_FACING_FOLD = 0.02;
    /** 面对下注弃牌率上限（验收 fold ≤ 10%） */
    private static final double MANIAC_MAX_FACING_FOLD = 0.10;
    /** 权益收缩系数（比 LAG 更不愿弃牌） */
    private static final double MANIAC_EQUITY_SHRINK = 0.52;
    /** 开池 raise 基准 */
    private static final double MANIAC_BASE_OPEN_RAISE = 0.88;
    /** 面对 all-in 跟注/反击基准 */
    private static final double MANIAC_BASE_ALL_IN_CALL = 0.90;

    private DpNpcManiacPostflopStrategy() {
    }

    public static BotAction decide(DpNpcRuleDecisionParams p) {
        BoardDanger bd = p.boardDanger;
        String stage = p.room.getCurrentStage();
        int callAmount = Math.max(0, p.room.getCurrentBetToCall() - p.bot.getBet());
        DpUtilSmartContext ctx = DpNpcEngine.buildSmartContext(
                p.room, p.bot, p.handSnapshot, stage, callAmount, p.random);

        DpNpcEngine.initHandPlanIfNeededForPostflop(
                p.room, p.bot, BotType.MANIAC, p.handSnapshot, bd, p.position, ctx, p.random);
        DpNpcEngine.updateHandPlanForLaterStreetIfNeeded(
                p.room, p.bot, BotType.MANIAC, p.handSnapshot, bd, ctx);

        DpNpcMadeHandCategory made = DpNpcPostflopFormula.madeOrHigh(p.handSnapshot);
        DpNpcDrawCategory draw = DpNpcPostflopFormula.drawOrNone(p.handSnapshot);
        DpBoardTexture tex = DpNpcPostflopFormula.textureOrDry(p.handSnapshot);
        HandPlanType plan = DpNpcEngine.getHandPlanType(p.bot);

        double commitFactor = maniacCommitFactor(made, draw, tex, bd, p, ctx);
        final double commitThreshold = (p.bot.getBet() + p.bot.getChips()) * commitFactor;

        if (callAmount >= p.chips) {
            return facingAllIn(p, ctx, made, draw, stage);
        }
        if (callAmount > 0) {
            BotAction foldAction = tryFoldFacingBet(p, callAmount, made, draw, ctx);
            if (foldAction != null) {
                return foldAction;
            }
            return decideFacingBet(p, stage, callAmount, made, draw, tex, plan, ctx, commitThreshold);
        }
        return decideNoBet(p, stage, made, draw, tex, plan, ctx);
    }

    private static double maniacCommitFactor(
            DpNpcMadeHandCategory made,
            DpNpcDrawCategory draw,
            DpBoardTexture tex,
            BoardDanger bd,
            DpNpcRuleDecisionParams p,
            DpUtilSmartContext ctx) {
        double factor = DpNpcPostflopFormula.commitFactor(made, draw, tex, bd);
        if (bd == BoardDanger.WET) {
            factor *= 0.82;
        }
        if (ctx.stackCtx != null
                && ctx.stackCtx.avgStackBB >= RuleNpcConfig.DEEP_TABLE_AVG_BB
                && !made.isAtLeast(DpNpcMadeHandCategory.TWO_PAIR)) {
            factor *= 0.88;
        }
        factor *= 1.0;
        double spr = DpNpcEngine.computeHeroPotSpr(p.room, p.bot);
        if (spr < 3.0) {
            factor = Math.min(0.98, factor * 1.12);
        }
        return Math.max(0.15, Math.min(0.98, factor));
    }

    private static BotAction facingAllIn(
            DpNpcRuleDecisionParams p,
            DpUtilSmartContext ctx,
            DpNpcMadeHandCategory made,
            DpNpcDrawCategory draw,
            String stage) {
        double allInProb = MANIAC_BASE_ALL_IN_CALL;
        double foldProb = MANIAC_BASE_FACING_FOLD;
        if (made == DpNpcMadeHandCategory.HIGH_CARD && !DpNpcPostflopFormula.hasSemiBluffDraw(draw)) {
            allInProb -= 0.12;
            foldProb += 0.04;
        }
        if (made == DpNpcMadeHandCategory.HIGH_CARD && ctx.activeVillains >= 2) {
            allInProb -= 0.08;
            foldProb += 0.03;
        }
        double[] credAdj = adjustCredibility(ctx, allInProb, foldProb);
        allInProb = Math.max(0.55, Math.min(0.98, credAdj[0]));
        foldProb = DpNpcEngine.adjustFoldProbForEquityVsPotOdds(
                Math.min(MANIAC_MAX_FACING_FOLD, Math.max(0.01, credAdj[1])),
                p.callAmount, ctx.potOdds, ctx.equityEst, ctx.activeVillains, MANIAC_EQUITY_SHRINK);
        foldProb = DpNpcHardConstraints.capFoldProb(
                foldProb,
                made,
                p.handSnapshot != null ? p.handSnapshot.handStrength : null,
                p.callAmount,
                ctx.equityEst,
                true);
        if (DpNpcHardConstraints.mustNotFold(
                made,
                p.handSnapshot != null ? p.handSnapshot.handStrength : null,
                p.callAmount,
                ctx.equityEst,
                true)) {
            foldProb = 0.0;
        }
        double x = p.random.nextDouble();
        if (x < foldProb) {
            return new BotAction(BotActionType.FOLD, 0);
        }
        if (x < foldProb + allInProb) {
            return new BotAction(BotActionType.ALL_IN, p.chips);
        }
        return new BotAction(BotActionType.CALL_OR_CHECK, 0);
    }

    private static double[] adjustCredibility(DpUtilSmartContext ctx, double allInProb, double foldProb) {
        ActionCredibility cred = ctx.credibility;
        VillainRangeTier tier = ctx.villainTier;
        if (cred == ActionCredibility.LOW || ctx.showdownBluffiness > 0.4
                || tier == VillainRangeTier.LOOSE || tier == VillainRangeTier.MANIAC) {
            allInProb += 0.10;
            foldProb = Math.max(0.01, foldProb - 0.03);
        } else if (cred == ActionCredibility.HIGH || ctx.showdownBluffiness < 0.1
                || tier == VillainRangeTier.NIT || tier == VillainRangeTier.TIGHT) {
            allInProb -= 0.10;
            foldProb += 0.03;
        }
        return new double[] {allInProb, foldProb};
    }

    private static BotAction tryFoldFacingBet(
            DpNpcRuleDecisionParams p,
            int callAmount,
            DpNpcMadeHandCategory made,
            DpNpcDrawCategory draw,
            DpUtilSmartContext ctx) {
        double foldProb = MANIAC_BASE_FACING_FOLD * (1.0 - 0.4 * p.callStation);
        foldProb += DpNpcHardConstraints.multiwayFoldBoost(
                made, ctx.activeVillains, p.checkRaiseFear, p.callRatio);
        foldProb = DpNpcEngine.adjustFoldProbForEquityVsPotOdds(
                foldProb, callAmount, ctx.potOdds, ctx.equityEst, ctx.activeVillains, MANIAC_EQUITY_SHRINK);
        if (foldProb > MANIAC_MAX_FACING_FOLD) {
            foldProb = MANIAC_MAX_FACING_FOLD;
        }
        foldProb = DpNpcHardConstraints.capFoldProb(
                foldProb,
                made,
                p.handSnapshot != null ? p.handSnapshot.handStrength : null,
                callAmount,
                ctx.equityEst,
                false);
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
        double raiseProb = MANIAC_BASE_OPEN_RAISE * (0.75 + 0.5 * p.aggression);
        if (plan == HandPlanType.VALUE && made.isAtLeast(DpNpcMadeHandCategory.TRIPS)) {
            raiseProb *= 1.10;
        } else if (plan == HandPlanType.GIVE_UP && made == DpNpcMadeHandCategory.HIGH_CARD) {
            raiseProb *= 0.72;
        } else if (DpNpcPostflopFormula.hasSemiBluffDraw(draw)) {
            raiseProb = Math.min(0.98, raiseProb + 0.10);
        }
        if ("flop".equals(stage) && made == DpNpcMadeHandCategory.HIGH_CARD) {
            raiseProb = Math.max(raiseProb, MANIAC_CBET_FREQ);
        }
        if (ctx.activeVillains >= 2 && made == DpNpcMadeHandCategory.HIGH_CARD) {
            raiseProb *= 0.75;
            raiseProb = Math.max(0.55, raiseProb);
        }
        raiseProb = Math.min(0.98, Math.max(0.50, raiseProb));

        double spr = DpNpcEngine.computeHeroPotSpr(p.room, p.bot);
        if (spr < 2.5 && p.random.nextDouble() < 0.55) {
            return new BotAction(BotActionType.ALL_IN, p.chips);
        }

        if (p.random.nextDouble() < raiseProb) {
            int minMulti = openMinMulti(made, draw);
            int maxMulti = openMaxMulti(made, draw);
            int multiplier = minMulti + p.random.nextInt(Math.max(1, maxMulti - minMulti + 1));
            int raiseAmount = Math.min(p.chips, p.room.getBigBlindChips() * multiplier);
            if (raiseAmount <= 0) {
                return new BotAction(BotActionType.CALL_OR_CHECK, 0);
            }
            DpNpcEngine.consumeOneBarrelIfAny(p.bot, stage);
            return new BotAction(BotActionType.RAISE, raiseAmount);
        }
        return new BotAction(BotActionType.CALL_OR_CHECK, 0);
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
        double r = p.random.nextDouble();
        double spr = DpNpcEngine.computeHeroPotSpr(p.room, p.bot);

        if (spr < 2.5 && r < 0.62) {
            return new BotAction(BotActionType.ALL_IN, p.chips);
        }
        if (r < 0.18) {
            return new BotAction(BotActionType.CALL_OR_CHECK, 0);
        }
        if (r < 0.82) {
            return raiseFacingBet(p, stage, callAmount, ctx, made, commitThreshold);
        }
        if (made == DpNpcMadeHandCategory.HIGH_CARD && !DpNpcPostflopFormula.hasSemiBluffDraw(draw)) {
            return new BotAction(BotActionType.CALL_OR_CHECK, 0);
        }
        return new BotAction(BotActionType.ALL_IN, p.chips);
    }

    private static BotAction raiseFacingBet(
            DpNpcRuleDecisionParams p,
            String stage,
            int callAmount,
            DpUtilSmartContext ctx,
            DpNpcMadeHandCategory made,
            double commitThreshold) {
        int minMulti = facingMinMulti(made);
        int maxMulti = facingMaxMulti(made);
        int multiplier = minMulti + p.random.nextInt(Math.max(1, maxMulti - minMulti + 1));
        int raiseAmount = Math.min(p.chips, callAmount + p.room.getBigBlindChips() * multiplier);
        if (raiseAmount <= 0) {
            return new BotAction(BotActionType.CALL_OR_CHECK, 0);
        }
        if (p.bot.getBet() + raiseAmount >= commitThreshold) {
            return commitThresholdAction(p, ctx, callAmount, made);
        }
        int bb = p.room.getBigBlindChips();
        if (bb > 0 && p.room.getPot() > bb * 20 && p.random.nextDouble() < 0.45) {
            if (ctx.activeVillains <= 1 || made.isAtLeast(DpNpcMadeHandCategory.TOP_PAIR_WEAK_KICKER)) {
                return new BotAction(BotActionType.ALL_IN, p.chips);
            }
        }
        DpNpcEngine.consumeOneBarrelIfAny(p.bot, stage);
        return new BotAction(BotActionType.RAISE, raiseAmount);
    }

    /**
     * 强牌永不随机 fold（移除旧版 LooseAggro 强牌弃牌支路）。
     */
    private static BotAction commitThresholdAction(
            DpNpcRuleDecisionParams p,
            DpUtilSmartContext ctx,
            int callAmount,
            DpNpcMadeHandCategory made) {
        if (DpNpcHardConstraints.mustNotFold(
                made,
                p.handSnapshot != null ? p.handSnapshot.handStrength : null,
                callAmount,
                ctx.equityEst,
                false)) {
            double y = p.random.nextDouble();
            return y < 0.35
                    ? new BotAction(BotActionType.CALL_OR_CHECK, 0)
                    : new BotAction(BotActionType.ALL_IN, p.chips);
        }
        if (made.isAtLeast(DpNpcMadeHandCategory.TRIPS)) {
            return p.random.nextDouble() < 0.75
                    ? new BotAction(BotActionType.ALL_IN, p.chips)
                    : new BotAction(BotActionType.CALL_OR_CHECK, 0);
        }
        if (made.isAtLeast(DpNpcMadeHandCategory.TOP_PAIR_TOP_KICKER)) {
            return p.random.nextDouble() < 0.65
                    ? new BotAction(BotActionType.ALL_IN, p.chips)
                    : new BotAction(BotActionType.CALL_OR_CHECK, 0);
        }
        double y = p.random.nextDouble();
        y = DpNpcEngine.skewCommitThresholdRandom(y, ctx, callAmount, 0.82, true);
        if (made == DpNpcMadeHandCategory.HIGH_CARD) {
            if (ctx.activeVillains >= 2) {
                return new BotAction(BotActionType.CALL_OR_CHECK, 0);
            }
            return y < 0.55
                    ? new BotAction(BotActionType.CALL_OR_CHECK, 0)
                    : new BotAction(BotActionType.ALL_IN, p.chips);
        }
        return y < 0.55
                ? new BotAction(BotActionType.CALL_OR_CHECK, 0)
                : new BotAction(BotActionType.ALL_IN, p.chips);
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
            return 6;
        }
        if (made.isAtLeast(DpNpcMadeHandCategory.TWO_PAIR)) {
            return 5;
        }
        if (DpNpcPostflopFormula.hasSemiBluffDraw(draw)) {
            return 5;
        }
        return 4;
    }

    private static int facingMinMulti(DpNpcMadeHandCategory made) {
        return made.isAtLeast(DpNpcMadeHandCategory.TRIPS) ? 3 : 2;
    }

    private static int facingMaxMulti(DpNpcMadeHandCategory made) {
        return made.isAtLeast(DpNpcMadeHandCategory.TRIPS) ? 7 : 6;
    }
}
