package com.example.mgdemoplus.npc.strategypro.postflop.custom;

import com.example.mgdemoplus.common.entity.DpPlayer;
import com.example.mgdemoplus.npc.engine.DpNpcEngine;
import com.example.mgdemoplus.npc.engine.DpNpcEngine.ActionCredibility;
import com.example.mgdemoplus.npc.engine.DpNpcEngine.BoardDanger;
import com.example.mgdemoplus.npc.engine.DpNpcEngine.BotAction;
import com.example.mgdemoplus.npc.engine.DpNpcEngine.BotActionType;
import com.example.mgdemoplus.npc.engine.DpNpcEngine.BotType;
import com.example.mgdemoplus.npc.engine.DpNpcEngine.HandPlanType;
import com.example.mgdemoplus.npc.eval.DpBoardTexture;
import com.example.mgdemoplus.npc.eval.DpNpcDrawCategory;
import com.example.mgdemoplus.npc.eval.DpNpcMadeHandCategory;
import com.example.mgdemoplus.npc.eval.DpNpcPostflopFormula;
import com.example.mgdemoplus.npc.strategypro.DpNpcRuleDecisionParams;
import com.example.mgdemoplus.npc.strategypro.l1.DpNpcHardConstraints;
import com.example.mgdemoplus.npc.strategypro.l4.DpNpcHeroCall;
import com.example.mgdemoplus.npc.strategypro.l4.DpNpcRaiseEscalation;
import com.example.mgdemoplus.npc.strategypro.l4.DpNpcRaiseEscalation.EscalationResult;
import com.example.mgdemoplus.utils.DpUtilSmartContext;

/**
 * CUSTOM 独立翻后策略：六维 L5 偏移器，各维度独立参与 fold / cbet / bluff / raise 门控。
 * <p>禁止委托 {@link com.example.mgdemoplus.npc.strategypro.DpNpcCustomStrategy} 或 preset postflop 类。</p>
 */
public final class DpNpcCustomPostflopStrategy {

    /** 跟注站倾向高于此阈值时禁用 raise（§4.7 L5） */
    private static final double CALL_STATION_RAISE_BLOCK = 0.72;

    private DpNpcCustomPostflopStrategy() {
    }

    public static BotAction decide(DpNpcRuleDecisionParams p) {
        CustomSixAxis axis = CustomSixAxis.from(p.bot);
        BotType planType = resolveNearestBotType(axis);

        BoardDanger bd = p.boardDanger;
        String stage = p.room.getCurrentStage();
        int callAmount = Math.max(0, p.room.getCurrentBetToCall() - p.bot.getBet());
        DpUtilSmartContext ctx = DpNpcEngine.buildSmartContext(
                p.room, p.bot, p.handSnapshot, stage, callAmount, p.random);

        DpNpcEngine.initHandPlanIfNeededForPostflop(
                p.room, p.bot, planType, p.handSnapshot, bd, p.position, ctx, p.random);
        DpNpcEngine.updateHandPlanForLaterStreetIfNeeded(
                p.room, p.bot, planType, p.handSnapshot, bd, ctx);

        DpNpcMadeHandCategory made = DpNpcPostflopFormula.madeOrHigh(p.handSnapshot);
        DpNpcDrawCategory draw = DpNpcPostflopFormula.drawOrNone(p.handSnapshot);
        DpBoardTexture tex = DpNpcPostflopFormula.textureOrDry(p.handSnapshot);
        HandPlanType plan = DpNpcEngine.getHandPlanType(p.bot);

        if (callAmount > 0) {
            BotAction foldAction = tryFoldFacingBet(p, axis, callAmount, made, draw, tex, plan, ctx);
            if (foldAction != null) {
                return foldAction;
            }
            return decideFacingBet(p, axis, stage, callAmount, made, draw, tex, plan, ctx);
        }
        return decideNoBet(p, axis, stage, made, draw, tex, plan, ctx);
    }

    /** 六维快照（0~1），直接从 {@link DpPlayer} 读取。 */
    private static final class CustomSixAxis {
        final double vpip;
        final double pfr;
        final double cbetFreq;
        final double bluffFreq;
        final double callStation;
        final double foldToPressure;

        private CustomSixAxis(
                double vpip,
                double pfr,
                double cbetFreq,
                double bluffFreq,
                double callStation,
                double foldToPressure) {
            this.vpip = clamp01(vpip);
            this.pfr = clamp01(pfr);
            this.cbetFreq = clamp01(cbetFreq);
            this.bluffFreq = clamp01(bluffFreq);
            this.callStation = clamp01(callStation);
            this.foldToPressure = clamp01(foldToPressure);
        }

        static CustomSixAxis from(DpPlayer bot) {
            return new CustomSixAxis(
                    bot.getNpcStyleVpip(),
                    bot.getNpcStylePfr(),
                    bot.getNpcStyleCbetFreq(),
                    bot.getNpcStyleBluffFreq(),
                    bot.getNpcStyleCallStation(),
                    bot.getNpcStyleFoldToPressure());
        }
    }

    private static BotType resolveNearestBotType(CustomSixAxis axis) {
        BotType best = BotType.TAG;
        double bestDist = Double.MAX_VALUE;
        for (PresetRef ref : PresetRef.values()) {
            double d = sq(axis.vpip - ref.vpip)
                    + sq(axis.pfr - ref.pfr)
                    + sq(axis.cbetFreq - ref.cbetFreq)
                    + sq(axis.bluffFreq - ref.bluffFreq)
                    + sq(axis.callStation - ref.callStation)
                    + sq(axis.foldToPressure - ref.foldToPressure);
            if (d < bestDist) {
                bestDist = d;
                best = ref.botType;
            }
        }
        return best;
    }

    private enum PresetRef {
        LAG(0.41, 0.74, 0.79, 0.47, 0.24, 0.22, BotType.LAG),
        TAG(0.24, 0.76, 0.82, 0.36, 0.18, 0.22, BotType.TAG),
        NIT(0.11, 0.15, 0.28, 0.035, 0.37, 0.92, BotType.NIT),
        FISH(0.52, 0.15, 0.20, 0.09, 0.74, 0.38, BotType.FISH),
        CALL(0.64, 0.05, 0.10, 0.025, 0.96, 0.10, BotType.CALL),
        MANIAC(0.48, 0.90, 0.88, 0.68, 0.22, 0.10, BotType.MANIAC);

        final double vpip;
        final double pfr;
        final double cbetFreq;
        final double bluffFreq;
        final double callStation;
        final double foldToPressure;
        final BotType botType;

        PresetRef(
                double vpip,
                double pfr,
                double cbetFreq,
                double bluffFreq,
                double callStation,
                double foldToPressure,
                BotType botType) {
            this.vpip = vpip;
            this.pfr = pfr;
            this.cbetFreq = cbetFreq;
            this.bluffFreq = bluffFreq;
            this.callStation = callStation;
            this.foldToPressure = foldToPressure;
            this.botType = botType;
        }
    }

    /** L5：foldToPressure / callStation / vpip 独立偏移，非公式 × 系数。 */
    private static double l5FacingBetFoldProb(
            CustomSixAxis axis,
            DpNpcMadeHandCategory made,
            DpNpcDrawCategory draw,
            DpBoardTexture tex,
            double callRatio,
            BoardDanger bd,
            DpUtilSmartContext ctx,
            DpNpcRuleDecisionParams p) {
        double fold = categoryBaselineFold(made, callRatio, tex, bd);

        // L5 独立偏移：foldToPressure / callStation 各读一次（checkRaiseFear 与 foldToPressure 同源，不重复叠加）
        fold += 0.48 * axis.foldToPressure;
        fold -= 0.45 * axis.callStation;

        if (draw != DpNpcDrawCategory.NONE) {
            double drawRelief = DpNpcPostflopFormula.drawCallFoldReduction(draw, ctx.potOdds);
            fold -= drawRelief * (0.28 + 0.42 * axis.vpip);
        }

        if (made == DpNpcMadeHandCategory.HIGH_CARD && callRatio < 0.12) {
            fold -= 0.10 * (1.0 - axis.foldToPressure);
        }
        if (made == DpNpcMadeHandCategory.HIGH_CARD && callRatio < 0.30) {
            fold -= 0.28 * (1.0 - axis.foldToPressure);
        }
        fold = Math.min(1.0, fold + DpNpcHardConstraints.multiwayFoldBoost(
                made, ctx.activeVillains, p.checkRaiseFear, callRatio));
        fold = DpNpcEngine.adjustFoldProbForEquityVsPotOdds(
                fold, p.callAmount, ctx.potOdds, ctx.equityEst, ctx.activeVillains, 1.0);

        if (callRatio >= 0.45 && made.ordinal() <= DpNpcMadeHandCategory.TOP_PAIR_WEAK_KICKER.ordinal()
                && made.ordinal() >= DpNpcMadeHandCategory.MIDDLE_PAIR.ordinal()) {
            fold = Math.min(1.0, fold + 0.12 + 0.28 * axis.foldToPressure);
        }

        return DpNpcHardConstraints.capFoldProb(
                clamp01(fold),
                made,
                p.handSnapshot != null ? p.handSnapshot.handStrength : null,
                p.callAmount,
                ctx.equityEst,
                p.callAmount >= p.chips);
    }

    private static double categoryBaselineFold(
            DpNpcMadeHandCategory made,
            double callRatio,
            DpBoardTexture tex,
            BoardDanger bd) {
        double fold;
        if (made.isAtLeast(DpNpcMadeHandCategory.TRIPS)) {
            fold = 0.02;
        } else if (made.isAtLeast(DpNpcMadeHandCategory.TWO_PAIR)) {
            fold = 0.08;
        } else if (made.isAtLeast(DpNpcMadeHandCategory.TOP_PAIR_TOP_KICKER)) {
            fold = 0.14;
        } else if (made.isAtLeast(DpNpcMadeHandCategory.TOP_PAIR_WEAK_KICKER)) {
            fold = 0.26;
        } else if (made == DpNpcMadeHandCategory.MIDDLE_PAIR) {
            fold = 0.36;
        } else if (made == DpNpcMadeHandCategory.BOTTOM_PAIR) {
            fold = 0.46;
        } else {
            fold = 0.54;
        }
        if (callRatio >= 0.50) {
            fold += 0.08 + 0.12 * Math.min(1.0, (callRatio - 0.50) / 0.35);
        }
        if (tex.wet || bd == BoardDanger.WET) {
            fold += 0.06;
        }
        return fold;
    }

    /** L5：cbetFreq 独立映射为领先下注概率。 */
    private static double l5LeadBetProbability(
            CustomSixAxis axis,
            DpNpcMadeHandCategory made,
            DpNpcDrawCategory draw,
            DpBoardTexture tex,
            String stage,
            HandPlanType plan) {
        double prob = 0.05 + 0.88 * axis.cbetFreq;

        if (made.isAtLeast(DpNpcMadeHandCategory.TRIPS)) {
            prob = Math.max(prob, 0.72 + 0.18 * axis.cbetFreq);
        } else if (made.isAtLeast(DpNpcMadeHandCategory.TWO_PAIR)) {
            prob = Math.max(prob, 0.55 + 0.30 * axis.cbetFreq);
        } else if (made == DpNpcMadeHandCategory.HIGH_CARD) {
            prob += l5BluffLeadAdd(axis, draw, stage, tex);
        } else if (made.isAtLeast(DpNpcMadeHandCategory.TOP_PAIR_WEAK_KICKER)) {
            prob += 0.04 + 0.10 * axis.vpip;
        }

        if (plan == HandPlanType.POT_CONTROL) {
            prob *= 0.78;
        } else if (plan == HandPlanType.GIVE_UP && made != DpNpcMadeHandCategory.HIGH_CARD) {
            prob *= 0.55;
        } else if (plan == HandPlanType.VALUE) {
            prob *= 1.08;
        }
        return clamp01(prob);
    }

    /** L5：bluffFreq 独立映射为诈唬/半诈唬增量。 */
    private static double l5BluffLeadAdd(
            CustomSixAxis axis,
            DpNpcDrawCategory draw,
            String stage,
            DpBoardTexture tex) {
        double add = 0.06 + 0.55 * axis.bluffFreq;
        if (DpNpcPostflopFormula.hasSemiBluffDraw(draw)) {
            add += 0.08 + 0.28 * axis.bluffFreq;
        }
        if ("river".equals(stage)) {
            add *= tex.isDry() ? 0.90 : 0.65;
        }
        return add;
    }

    /** L5：callStation 门控 — 高跟注站禁用 raise。 */
    private static boolean l5RaisePermitted(CustomSixAxis axis) {
        return axis.callStation < CALL_STATION_RAISE_BLOCK;
    }

    /** L5：pfr / cbetFreq / bluffFreq 独立合成 raise 概率。 */
    private static double l5RaiseProbability(
            CustomSixAxis axis,
            DpNpcMadeHandCategory made,
            DpNpcDrawCategory draw,
            DpBoardTexture tex,
            HandPlanType plan,
            DpUtilSmartContext ctx) {
        double prob = 0.04 + 0.42 * axis.pfr + 0.18 * axis.cbetFreq;

        if (made == DpNpcMadeHandCategory.HIGH_CARD) {
            prob = Math.max(prob, 0.08 + 0.52 * axis.bluffFreq);
            if (DpNpcPostflopFormula.hasSemiBluffDraw(draw)) {
                prob += 0.06 * axis.bluffFreq;
            }
        } else if (made.isAtLeast(DpNpcMadeHandCategory.TRIPS)) {
            prob = Math.max(prob, 0.32 + 0.28 * axis.pfr);
        } else if (made.isAtLeast(DpNpcMadeHandCategory.TWO_PAIR)) {
            prob = Math.max(prob, 0.18 + 0.22 * axis.pfr);
        }

        if (plan == HandPlanType.GIVE_UP) {
            prob *= 0.40;
        } else if (plan == HandPlanType.POT_CONTROL) {
            prob *= 0.72;
        } else if (plan == HandPlanType.BLUFF) {
            prob *= 1.10;
        }

        ActionCredibility cred = ctx.credibility;
        if (cred == ActionCredibility.HIGH && made == DpNpcMadeHandCategory.HIGH_CARD) {
            prob *= 0.55;
        } else if (cred == ActionCredibility.LOW && !DpNpcPostflopFormula.isMonster(made)) {
            prob = Math.min(0.88, prob * 1.12);
        }

        if (ctx.activeVillains >= 2 && made == DpNpcMadeHandCategory.HIGH_CARD) {
            prob *= 0.45;
        }

        return clamp01(prob * (0.75 + 0.40 * axis.pfr));
    }

    private static BotAction tryFoldFacingBet(
            DpNpcRuleDecisionParams p,
            CustomSixAxis axis,
            int callAmount,
            DpNpcMadeHandCategory made,
            DpNpcDrawCategory draw,
            DpBoardTexture tex,
            HandPlanType plan,
            DpUtilSmartContext ctx) {
        if (DpNpcHardConstraints.mustNotFold(
                made,
                p.handSnapshot != null ? p.handSnapshot.handStrength : null,
                callAmount,
                ctx.equityEst,
                callAmount >= p.chips)) {
            return null;
        }

        double callRatio = p.chips == 0 ? 1.0 : (callAmount * 1.0 / p.chips);
        double foldProb = l5FacingBetFoldProb(axis, made, draw, tex, callRatio, p.boardDanger, ctx, p);

        if (plan == HandPlanType.GIVE_UP && !made.isAtLeast(DpNpcMadeHandCategory.TRIPS)) {
            foldProb = Math.min(1.0, foldProb + (0.12 + 0.10 * axis.foldToPressure) * axis.foldToPressure);
        }

        if (DpNpcHeroCall.shouldHeroCall(p, p.room.getCurrentStage(), callAmount, made, draw, ctx)) {
            return null;
        }

        if (foldProb > 0 && p.random.nextDouble() < foldProb) {
            return new BotAction(BotActionType.FOLD, 0);
        }
        return null;
    }

    private static BotAction decideNoBet(
            DpNpcRuleDecisionParams p,
            CustomSixAxis axis,
            String stage,
            DpNpcMadeHandCategory made,
            DpNpcDrawCategory draw,
            DpBoardTexture tex,
            HandPlanType plan,
            DpUtilSmartContext ctx) {
        double leadProb = l5LeadBetProbability(axis, made, draw, tex, stage, plan);
        boolean l5LeadOverride = axis.cbetFreq >= 0.65 || axis.bluffFreq >= 0.55 || plan == HandPlanType.BLUFF;
        if (!l5LeadOverride && DpNpcEngine.shouldSkipAggressiveActionByPlan(p.bot, stage)
                && made != DpNpcMadeHandCategory.HIGH_CARD
                && !made.isAtLeast(DpNpcMadeHandCategory.TOP_PAIR_TOP_KICKER)) {
            return new BotAction(BotActionType.CALL_OR_CHECK, 0);
        }
        if (ctx.activeVillains >= 2 && made == DpNpcMadeHandCategory.HIGH_CARD) {
            leadProb *= 0.55;
        }

        if (p.random.nextDouble() < leadProb) {
            double factor = DpNpcPostflopFormula.cbetPotFactor(made, tex, stage);
            factor *= 0.72 + 0.36 * axis.cbetFreq + 0.18 * axis.pfr;
            return raisePotFraction(p, stage, factor);
        }
        return new BotAction(BotActionType.CALL_OR_CHECK, 0);
    }

    private static BotAction decideFacingBet(
            DpNpcRuleDecisionParams p,
            CustomSixAxis axis,
            String stage,
            int callAmount,
            DpNpcMadeHandCategory made,
            DpNpcDrawCategory draw,
            DpBoardTexture tex,
            HandPlanType plan,
            DpUtilSmartContext ctx) {
        if (!l5RaisePermitted(axis)) {
            return new BotAction(BotActionType.CALL_OR_CHECK, 0);
        }

        double raiseProb = l5RaiseProbability(axis, made, draw, tex, plan, ctx);
        if (p.random.nextDouble() >= raiseProb || p.chips <= callAmount) {
            return new BotAction(BotActionType.CALL_OR_CHECK, 0);
        }

        int extraMin = DpNpcPostflopFormula.raiseExtraMinBb(made);
        int extraMax = Math.max(extraMin, DpNpcPostflopFormula.raiseExtraMaxBb(made, stage));
        EscalationResult esc = DpNpcRaiseEscalation.computeFacingBetRaise(
                p.room, callAmount, p.chips, made, stage, p.type, extraMin, extraMax, p.random);
        if (esc.suggestJam && p.random.nextDouble() < 0.40 + 0.25 * axis.pfr) {
            return new BotAction(BotActionType.ALL_IN, p.chips);
        }
        int raise = esc.raiseAmount;
        if (raise >= p.chips) {
            return new BotAction(BotActionType.ALL_IN, p.chips);
        }
        if (raise <= callAmount) {
            return new BotAction(BotActionType.CALL_OR_CHECK, 0);
        }
        if (DpNpcEngine.shouldSkipAggressiveActionByPlan(p.bot, stage)
                && !made.isAtLeast(DpNpcMadeHandCategory.TWO_PAIR)) {
            return new BotAction(BotActionType.CALL_OR_CHECK, 0);
        }
        DpNpcEngine.consumeOneBarrelIfAny(p.bot, stage);
        return new BotAction(BotActionType.RAISE, raise);
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
            DpNpcMadeHandCategory made) {
        int bb = p.room.getBigBlindChips();
        if (callAmount > 0 && bb > 0) {
            int minExtraBB = made.isAtLeast(DpNpcMadeHandCategory.TRIPS) ? 4 : 3;
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

    private static double sq(double x) {
        return x * x;
    }

    private static double clamp01(double v) {
        return Math.max(0.0, Math.min(1.0, v));
    }
}
