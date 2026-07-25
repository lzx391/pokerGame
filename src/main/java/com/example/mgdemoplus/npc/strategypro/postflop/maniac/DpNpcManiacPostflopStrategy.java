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
import com.example.mgdemoplus.npc.strategypro.l4.DpNpcHeroCall;
import com.example.mgdemoplus.npc.strategypro.l4.DpNpcRaiseEscalation;
import com.example.mgdemoplus.npc.strategypro.l4.DpNpcRaiseEscalation.EscalationResult;
import com.example.mgdemoplus.npc.trace.DpNpcPostflopTraceHooks;
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

        DpNpcPostflopTraceHooks.postflopContext(
                stage, callAmount, plan, made, draw, tex, bd, p, ctx, commitFactor, commitThreshold);

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
        if (ctx.stackCtx != null) {
            factor = DpNpcRaiseEscalation.adjustDeepStackCommitFactor(
                    factor, made, ctx.stackCtx.avgStackBB);
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
            DpNpcPostflopTraceHooks.foldRollHit(foldProb, foldProb);
            return new BotAction(BotActionType.FOLD, 0);
        }
        if (x < foldProb + allInProb) {
            DpNpcPostflopTraceHooks.foldRollMiss(foldProb, foldProb, DpNpcEngine.getHandPlanType(p.bot));
            DpNpcPostflopTraceHooks.postflopAction("ALL_IN", "面对 all-in 反击");
            return new BotAction(BotActionType.ALL_IN, p.chips);
        }
        DpNpcPostflopTraceHooks.foldRollMiss(foldProb, foldProb, DpNpcEngine.getHandPlanType(p.bot));
        DpNpcPostflopTraceHooks.postflopAction("CALL_OR_CHECK", "面对 all-in 跟注");
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
        if (DpNpcHeroCall.shouldHeroCall(
                p, p.room.getCurrentStage(), callAmount, made, draw, ctx)) {
            DpNpcPostflopTraceHooks.heroCall();
            return null;
        }
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
            DpNpcPostflopTraceHooks.postflopAction("ALL_IN", "低 SPR 直接 jam");
            return new BotAction(BotActionType.ALL_IN, p.chips);
        }

        if (p.random.nextDouble() < raiseProb) {
            int minMulti = openMinMulti(made, draw);
            int maxMulti = openMaxMulti(made, draw);
            int multiplier = minMulti + p.random.nextInt(Math.max(1, maxMulti - minMulti + 1));
            int raiseAmount = Math.min(p.chips, p.room.getBigBlindChips() * multiplier);
            if (raiseAmount <= 0) {
                DpNpcPostflopTraceHooks.valueBetRollMiss(raiseProb);
                DpNpcPostflopTraceHooks.postflopAction("CALL_OR_CHECK", "check（raise amount zero）");
                return new BotAction(BotActionType.CALL_OR_CHECK, 0);
            }
            DpNpcPostflopTraceHooks.valueBetRoll(raiseProb, multiplier);
            DpNpcPostflopTraceHooks.postflopAction("RAISE", "开池 raise multiplier=" + multiplier);
            DpNpcEngine.consumeOneBarrelIfAny(p.bot, stage);
            return new BotAction(BotActionType.RAISE, raiseAmount);
        }
        DpNpcPostflopTraceHooks.valueBetRollMiss(raiseProb);
        DpNpcPostflopTraceHooks.postflopAction("CALL_OR_CHECK", "check（no bet line）");
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
        int raiseLevel = p.room.getRaiseLevel();

        if (raiseLevel >= 2
                && r < DpNpcRaiseEscalation.maniacReRaiseJamProb(raiseLevel, made)) {
            DpNpcPostflopTraceHooks.postflopAction("ALL_IN", "re-raise jam");
            return new BotAction(BotActionType.ALL_IN, p.chips);
        }

        if (spr < 2.5 && r < 0.62) {
            DpNpcPostflopTraceHooks.postflopAction("ALL_IN", "低 SPR facing bet jam");
            return new BotAction(BotActionType.ALL_IN, p.chips);
        }
        if (r < 0.18) {
            DpNpcPostflopTraceHooks.foldRollMiss(0.0, 0.0, plan);
            DpNpcPostflopTraceHooks.postflopAction("CALL_OR_CHECK", "面对下注跟注");
            return new BotAction(BotActionType.CALL_OR_CHECK, 0);
        }
        if (r < 0.82) {
            return raiseFacingBet(p, stage, callAmount, ctx, made, commitThreshold, plan);
        }
        if (made == DpNpcMadeHandCategory.HIGH_CARD && !DpNpcPostflopFormula.hasSemiBluffDraw(draw)) {
            DpNpcPostflopTraceHooks.postflopAction("CALL_OR_CHECK", "高牌无听牌跟注");
            return new BotAction(BotActionType.CALL_OR_CHECK, 0);
        }
        DpNpcPostflopTraceHooks.postflopAction("ALL_IN", "边缘牌 all-in");
        return new BotAction(BotActionType.ALL_IN, p.chips);
    }

    private static BotAction raiseFacingBet(
            DpNpcRuleDecisionParams p,
            String stage,
            int callAmount,
            DpUtilSmartContext ctx,
            DpNpcMadeHandCategory made,
            double commitThreshold,
            HandPlanType plan) {
        int extraMin = Math.max(2, facingMinMulti(made));
        int extraMax = Math.max(extraMin, facingMaxMulti(made));
        EscalationResult esc = DpNpcRaiseEscalation.computeFacingBetRaise(
                p.room, callAmount, p.chips, made, stage, p.type, extraMin, extraMax, p.random);
        if (esc.suggestJam && p.random.nextDouble() < 0.65) {
            DpNpcPostflopTraceHooks.raiseRoll(0.5, 1.0, plan, made);
            DpNpcPostflopTraceHooks.postflopAction("ALL_IN", "escalation jam");
            return new BotAction(BotActionType.ALL_IN, p.chips);
        }
        int raiseAmount = esc.raiseAmount;
        if (raiseAmount <= 0) {
            DpNpcPostflopTraceHooks.postflopAction("CALL_OR_CHECK", "跟注（raise amount zero）");
            return new BotAction(BotActionType.CALL_OR_CHECK, 0);
        }
        if (p.bot.getBet() + raiseAmount >= commitThreshold) {
            return commitThresholdAction(p, ctx, callAmount, made, commitThreshold, p.bot.getBet() + raiseAmount);
        }
        int bb = p.room.getBigBlindChips();
        if (bb > 0 && p.room.getPot() > bb * 20 && p.random.nextDouble() < 0.45) {
            if (ctx.activeVillains <= 1 || made.isAtLeast(DpNpcMadeHandCategory.TOP_PAIR_WEAK_KICKER)) {
                DpNpcPostflopTraceHooks.raiseRoll(0.5, 1.0, plan, made);
                DpNpcPostflopTraceHooks.postflopAction("ALL_IN", "大池 jam");
                return new BotAction(BotActionType.ALL_IN, p.chips);
            }
        }
        DpNpcEngine.consumeOneBarrelIfAny(p.bot, stage);
        DpNpcPostflopTraceHooks.raiseRoll(0.5, 1.0, plan, made);
        DpNpcPostflopTraceHooks.postflopAction("RAISE", "raise facing bet amount=" + raiseAmount);
        return new BotAction(BotActionType.RAISE, raiseAmount);
    }

    /**
     * 强牌永不随机 fold（移除旧版 LooseAggro 强牌弃牌支路）。
     */
    private static BotAction commitThresholdAction(
            DpNpcRuleDecisionParams p,
            DpUtilSmartContext ctx,
            int callAmount,
            DpNpcMadeHandCategory made,
            double commitThreshold,
            int heroInvestAfter) {
        if (DpNpcHardConstraints.mustNotFold(
                made,
                p.handSnapshot != null ? p.handSnapshot.handStrength : null,
                callAmount,
                ctx.equityEst,
                false)) {
            double y = p.random.nextDouble();
            BotAction action = y < 0.35
                    ? new BotAction(BotActionType.CALL_OR_CHECK, 0)
                    : new BotAction(BotActionType.ALL_IN, p.chips);
            DpNpcPostflopTraceHooks.commitThreshold(
                    commitThreshold, heroInvestAfter, y < 0.35 ? "call" : "jam", action.getType());
            DpNpcPostflopTraceHooks.postflopAction(action.getType().name(), "commit threshold → " + action.getType().name());
            return action;
        }
        if (made.isAtLeast(DpNpcMadeHandCategory.TRIPS)) {
            BotAction action = p.random.nextDouble() < 0.75
                    ? new BotAction(BotActionType.ALL_IN, p.chips)
                    : new BotAction(BotActionType.CALL_OR_CHECK, 0);
            DpNpcPostflopTraceHooks.commitThreshold(
                    commitThreshold, heroInvestAfter, action.getType() == BotActionType.ALL_IN ? "jam" : "call", action.getType());
            DpNpcPostflopTraceHooks.postflopAction(action.getType().name(), "commit threshold → " + action.getType().name());
            return action;
        }
        if (made.isAtLeast(DpNpcMadeHandCategory.TOP_PAIR_TOP_KICKER)) {
            BotAction action = p.random.nextDouble() < 0.65
                    ? new BotAction(BotActionType.ALL_IN, p.chips)
                    : new BotAction(BotActionType.CALL_OR_CHECK, 0);
            DpNpcPostflopTraceHooks.commitThreshold(
                    commitThreshold, heroInvestAfter, action.getType() == BotActionType.ALL_IN ? "jam" : "call", action.getType());
            DpNpcPostflopTraceHooks.postflopAction(action.getType().name(), "commit threshold → " + action.getType().name());
            return action;
        }
        double y = p.random.nextDouble();
        y = DpNpcEngine.skewCommitThresholdRandom(y, ctx, callAmount, 0.82, true);
        BotAction action;
        if (made == DpNpcMadeHandCategory.HIGH_CARD) {
            if (ctx.activeVillains >= 2) {
                action = new BotAction(BotActionType.CALL_OR_CHECK, 0);
            } else {
                action = y < 0.55
                        ? new BotAction(BotActionType.CALL_OR_CHECK, 0)
                        : new BotAction(BotActionType.ALL_IN, p.chips);
            }
        } else {
            action = y < 0.55
                    ? new BotAction(BotActionType.CALL_OR_CHECK, 0)
                    : new BotAction(BotActionType.ALL_IN, p.chips);
        }
        DpNpcPostflopTraceHooks.commitThreshold(
                commitThreshold, heroInvestAfter, action.getType() == BotActionType.ALL_IN ? "jam" : "call", action.getType());
        DpNpcPostflopTraceHooks.postflopAction(action.getType().name(), "commit threshold → " + action.getType().name());
        return action;
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
