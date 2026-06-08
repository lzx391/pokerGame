package com.example.mgdemoplus.npc.strategypro.postflop.fish;

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
 * FISH 独立翻后策略：松弱跟注站，宽 call、听牌不弃、极少 raise；偶有 flop donk。
 * <p>禁止套壳旧 PassiveStation 聚合决策类。</p>
 */
public final class DpNpcFishPostflopStrategy {

    /** FISH preset cbet 频率基准（§4.1 ≈20%） */
    private static final double FISH_CBET_FREQ = 0.20;
    /** 相对 TAG（1.0）更松的弃牌系数 */
    private static final double FISH_FOLD_TIGHTNESS = 0.82;
    /** flop 小额 donk 诈唬基准（§4.1 ≈9%） */
    private static final double FISH_FLOP_DONK_BLUFF = 0.09;

    private DpNpcFishPostflopStrategy() {
    }

    public static BotAction decide(DpNpcRuleDecisionParams p) {
        BoardDanger bd = p.boardDanger;
        String stage = p.room.getCurrentStage();
        int callAmount = Math.max(0, p.room.getCurrentBetToCall() - p.bot.getBet());
        DpUtilSmartContext ctx = DpNpcEngine.buildSmartContext(
                p.room, p.bot, p.handSnapshot, stage, callAmount, p.random);

        DpNpcEngine.initHandPlanIfNeededForPostflop(
                p.room, p.bot, BotType.FISH, p.handSnapshot, bd, p.position, ctx, p.random);
        DpNpcEngine.updateHandPlanForLaterStreetIfNeeded(
                p.room, p.bot, BotType.FISH, p.handSnapshot, bd, ctx);

        DpNpcMadeHandCategory made = DpNpcPostflopFormula.madeOrHigh(p.handSnapshot);
        DpNpcDrawCategory draw = DpNpcPostflopFormula.drawOrNone(p.handSnapshot);
        DpBoardTexture tex = DpNpcPostflopFormula.textureOrDry(p.handSnapshot);
        HandPlanType plan = DpNpcEngine.getHandPlanType(p.bot);

        if (callAmount > 0) {
            BotAction foldAction = tryFoldFacingBet(p, stage, callAmount, made, draw, tex, plan, ctx);
            if (foldAction != null) {
                return foldAction;
            }
            return decideFacingBet(p, stage, callAmount, made, draw, tex, plan, ctx);
        }
        return decideNoBet(p, stage, made, draw, tex, plan, ctx);
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
                made, draw, tex, callRatio, p.boardDanger, FISH_FOLD_TIGHTNESS);
        baseFold = Math.min(1.0, baseFold + DpNpcHardConstraints.multiwayFoldBoost(
                made, ctx.activeVillains, p.checkRaiseFear, callRatio));
        baseFold = DpNpcEngine.adjustFoldProbForEquityVsPotOdds(
                baseFold, callAmount, ctx.potOdds, ctx.equityEst, ctx.activeVillains, 0.55);
        baseFold = Math.max(0.0, baseFold - DpNpcPostflopFormula.drawCallFoldReduction(draw, ctx.potOdds));

        // FISH：高牌需较大注才弃；小额压力跟注偏多
        if (made == DpNpcMadeHandCategory.HIGH_CARD) {
            if (callRatio > 0 && callRatio < 0.38) {
                baseFold *= 0.52;
            }
            if (DpNpcPostflopFormula.hasSemiBluffDraw(draw)) {
                baseFold *= 0.42;
            }
            if (ctx.potOdds > 0.55 && !DpNpcPostflopFormula.hasSemiBluffDraw(draw)) {
                baseFold = Math.min(1.0, baseFold + 0.14);
            }
        } else if (ctx.potOdds > 0 && ctx.potOdds < 0.34 && callRatio < 0.55
                && made.isAtLeast(DpNpcMadeHandCategory.TOP_PAIR_WEAK_KICKER)) {
            baseFold *= 0.68;
        }

        ActionCredibility cred = ctx.credibility;
        if (cred == ActionCredibility.HIGH || ctx.showdownBluffiness < 0.1) {
            baseFold = Math.min(1.0, baseFold + 0.06);
        } else if (cred == ActionCredibility.LOW || ctx.showdownBluffiness > 0.4) {
            baseFold = Math.max(0.0, baseFold * 0.78);
        }

        if (plan == HandPlanType.GIVE_UP && callAmount > 0
                && !made.isAtLeast(DpNpcMadeHandCategory.TRIPS)) {
            baseFold = Math.min(1.0, baseFold + 0.11);
        }

        // FISH 决策分叉：中对/弱顶对宽跟，但面对大注有基本恐惧（比 CALL 更易弃边缘牌）
        if (made == DpNpcMadeHandCategory.MIDDLE_PAIR || made == DpNpcMadeHandCategory.BOTTOM_PAIR) {
            if (callRatio < 0.45) {
                baseFold = Math.max(0.0, baseFold * 0.55);
            } else if (callRatio >= 0.50) {
                double boost = made == DpNpcMadeHandCategory.MIDDLE_PAIR ? 0.18 : 0.24;
                baseFold = Math.min(1.0, baseFold + boost + 0.12 * Math.min(1.0, (callRatio - 0.50) / 0.35));
            }
        }
        if (made == DpNpcMadeHandCategory.TOP_PAIR_WEAK_KICKER && callRatio >= 0.55) {
            baseFold = Math.min(1.0, baseFold + 0.10);
        }

        double foldProb = Math.min(1.0, Math.max(0.0, baseFold * (1.0 - 0.62 * p.callStation)));
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

    private static BotAction decideFacingBet(
            DpNpcRuleDecisionParams p,
            String stage,
            int callAmount,
            DpNpcMadeHandCategory made,
            DpNpcDrawCategory draw,
            DpBoardTexture tex,
            HandPlanType plan,
            DpUtilSmartContext ctx) {
        double raiseProb = DpNpcPostflopFormula.raiseProb(made, draw, tex, plan);
        double aggro = (0.55 + 0.35 * p.aggression);
        if (plan == HandPlanType.VALUE && made.isAtLeast(DpNpcMadeHandCategory.TWO_PAIR)) {
            aggro *= 1.08;
        } else if (plan == HandPlanType.POT_CONTROL || plan == HandPlanType.GIVE_UP) {
            aggro *= 0.45;
        } else if (plan == HandPlanType.BLUFF) {
            aggro *= 0.55;
        }
        raiseProb *= aggro * 0.42;

        if (made == DpNpcMadeHandCategory.HIGH_CARD) {
            raiseProb *= 0.12;
        } else if (made.ordinal() <= DpNpcMadeHandCategory.TOP_PAIR_WEAK_KICKER.ordinal()) {
            raiseProb *= 0.35;
        }
        if (ctx.activeVillains >= 2) {
            raiseProb *= 0.40;
        }
        raiseProb = Math.min(0.35, Math.max(0.02, raiseProb));

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
            target = (int) Math.round(target * 0.75);
        }
        int raiseAmount = Math.min(p.chips, target);
        raiseAmount = snapRaiseToSb(p, callAmount, raiseAmount, made, bb);

        if (raiseAmount <= callAmount) {
            return new BotAction(BotActionType.CALL_OR_CHECK, 0);
        }
        if (DpNpcEngine.shouldSkipAggressiveActionByPlan(p.bot, stage)
                && !made.isAtLeast(DpNpcMadeHandCategory.TWO_PAIR)) {
            return new BotAction(BotActionType.CALL_OR_CHECK, 0);
        }
        DpNpcEngine.consumeOneBarrelIfAny(p.bot, stage);
        return new BotAction(BotActionType.RAISE, raiseAmount);
    }

    private static BotAction decideNoBet(
            DpNpcRuleDecisionParams p,
            String stage,
            DpNpcMadeHandCategory made,
            DpNpcDrawCategory draw,
            DpBoardTexture tex,
            HandPlanType plan,
            DpUtilSmartContext ctx) {
        // FISH 人格化 flop donk：先于 HandPlan skip 守卫，避免 GIVE_UP/barrels=0 完全屏蔽 lead
        if (made == DpNpcMadeHandCategory.HIGH_CARD) {
            BotAction donk = tryFishFlopDonk(p, stage, draw, plan, ctx);
            if (donk != null) {
                return donk;
            }
            if ("river".equals(stage)) {
                return decideNoBetHighCardRiver(p, plan, ctx);
            }
            return new BotAction(BotActionType.CALL_OR_CHECK, 0);
        }

        if (DpNpcEngine.shouldSkipAggressiveActionByPlan(p.bot, stage)) {
            if (!made.isAtLeast(DpNpcMadeHandCategory.TOP_PAIR_TOP_KICKER)) {
                return new BotAction(BotActionType.CALL_OR_CHECK, 0);
            }
        }

        if (made.ordinal() <= DpNpcMadeHandCategory.TOP_PAIR_WEAK_KICKER.ordinal()
                && !DpNpcPostflopFormula.hasSemiBluffDraw(draw)) {
            if (plan == HandPlanType.POT_CONTROL || plan == HandPlanType.GIVE_UP) {
                return new BotAction(BotActionType.CALL_OR_CHECK, 0);
            }
        }

        double valueBetProb = fishNoBetValueProb(made, tex, stage, plan, ctx, p);
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

    private static BotAction tryFishFlopDonk(
            DpNpcRuleDecisionParams p,
            String stage,
            DpNpcDrawCategory draw,
            HandPlanType plan,
            DpUtilSmartContext ctx) {
        if (!"flop".equals(stage)) {
            return null;
        }
        double donk = FISH_FLOP_DONK_BLUFF * (0.85 + 0.3 * p.bluffFrequency);
        if (plan == HandPlanType.BLUFF) {
            donk *= 1.15;
        } else if (plan == HandPlanType.GIVE_UP) {
            donk *= 0.42;
        }
        if (ctx.activeVillains >= 2) {
            donk *= 0.50;
        }
        if (DpNpcPostflopFormula.hasSemiBluffDraw(draw)) {
            donk *= 1.25;
        }
        donk = Math.min(0.15, Math.max(0.0, donk));
        if (p.random.nextDouble() < donk) {
            return raisePotFraction(p, stage, 0.30);
        }
        return null;
    }

    private static BotAction decideNoBetHighCardRiver(
            DpNpcRuleDecisionParams p,
            HandPlanType plan,
            DpUtilSmartContext ctx) {
        double riverBluff = p.bluffFrequency * 0.04;
        if (ctx.activeVillains >= 2) {
            riverBluff *= 0.20;
        }
        if (p.random.nextDouble() < riverBluff) {
            return raisePotFraction(p, "river", RuleNpcConfig.CBET_BASE_WEAK * 0.45);
        }
        return new BotAction(BotActionType.CALL_OR_CHECK, 0);
    }

    /**
     * FISH 价值下注概率：有牌才 bet，cbet 低频。
     */
    private static double fishNoBetValueProb(
            DpNpcMadeHandCategory made,
            DpBoardTexture tex,
            String stage,
            HandPlanType plan,
            DpUtilSmartContext ctx,
            DpNpcRuleDecisionParams p) {
        double prob = DpNpcPostflopFormula.valueBetProb(made, tex, stage, plan);
        double aggro = 0.55 + 0.40 * p.aggression;
        if (plan == HandPlanType.VALUE) {
            aggro *= 1.06;
        } else if (plan == HandPlanType.POT_CONTROL || plan == HandPlanType.GIVE_UP) {
            aggro *= 0.88;
        }
        prob *= aggro;

        if (made.isAtLeast(DpNpcMadeHandCategory.TRIPS)) {
            prob = Math.max(prob, 0.72);
        } else if (made.isAtLeast(DpNpcMadeHandCategory.TWO_PAIR)) {
            prob = Math.max(prob, tex.dangerousForTwoPairPlus() ? 0.38 : 0.52);
        } else if (made == DpNpcMadeHandCategory.TOP_PAIR_TOP_KICKER) {
            if (tex.isDry()) {
                prob = Math.min(prob, FISH_CBET_FREQ + 0.08);
            } else {
                prob = Math.min(prob, FISH_CBET_FREQ * 0.55);
            }
        } else if (made.ordinal() <= DpNpcMadeHandCategory.TOP_PAIR_WEAK_KICKER.ordinal()) {
            prob = Math.min(prob, 0.12);
        }

        if (ctx.activeVillains >= 2) {
            if (made.ordinal() <= DpNpcMadeHandCategory.TOP_PAIR_TOP_KICKER.ordinal()) {
                prob *= 0.62;
            }
        }

        if ("flop".equals(stage) && made.isAtLeast(DpNpcMadeHandCategory.TOP_PAIR_TOP_KICKER)) {
            prob = Math.min(prob, FISH_CBET_FREQ + (plan == HandPlanType.VALUE ? 0.06 : 0.0));
        }
        return Math.min(0.75, Math.max(0.03, prob));
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
            int minExtraBB = made.isAtLeast(DpNpcMadeHandCategory.TRIPS) ? 3 : 2;
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
}
