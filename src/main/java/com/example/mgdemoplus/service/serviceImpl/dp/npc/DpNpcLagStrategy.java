package com.example.mgdemoplus.service.serviceImpl.dp.npc;

import static com.example.mgdemoplus.utils.dp.DpUtilHandEvaluator.SimpleStrength;

import com.example.mgdemoplus.bo.DpRoomBO;
import com.example.mgdemoplus.entity.dp.DpPlayer;
import com.example.mgdemoplus.service.serviceImpl.dp.DpNpcEngine;
import com.example.mgdemoplus.service.serviceImpl.dp.DpNpcEngine.ActionCredibility;
import com.example.mgdemoplus.service.serviceImpl.dp.DpNpcEngine.BoardDanger;
import com.example.mgdemoplus.service.serviceImpl.dp.DpNpcEngine.BotAction;
import com.example.mgdemoplus.service.serviceImpl.dp.DpNpcEngine.BotActionType;
import com.example.mgdemoplus.service.serviceImpl.dp.DpNpcEngine.BotType;
import com.example.mgdemoplus.service.serviceImpl.dp.DpNpcEngine.HandPlanType;
import com.example.mgdemoplus.service.serviceImpl.dp.DpNpcEngine.RuleNpcConfig;
import com.example.mgdemoplus.service.serviceImpl.dp.DpNpcEngine.VillainRangeTier;
import com.example.mgdemoplus.utils.dp.DpUtilSmartContext;

/**
 * 松凶（LAG）：翻前仅由 {@link com.example.mgdemoplus.service.serviceImpl.dp.DpNpcUnifiedPreflopStrategy}，
 * 翻后逻辑全部在本类维护（HandPlan / barrels）。
 */
public final class DpNpcLagStrategy {
    private DpNpcLagStrategy() {
    }

    public static BotAction decide(DpNpcRuleDecisionParams p) {
        // System.out.println("DpNpcLagStrategy");
        DpRoomBO room = p.room;
        DpPlayer bot = p.bot;
        int chips = p.chips;
        int callAmount = p.callAmount;
        double callRatio = p.callRatio;
        SimpleStrength strength = p.strength;
        BoardDanger boardDanger = p.boardDanger;
        double mood = p.mood;
        double aggression = p.aggression;
        double callStation = p.callStation;

        final double looseAggroMul = 0.88;
        String stage = room.getCurrentStage();
        DpUtilSmartContext ctx = DpNpcEngine.buildSmartContext(room, bot, strength, stage, callAmount, p.random);
        DpNpcEngine.initHandPlanIfNeededForPostflop(
                room, bot, BotType.LAG, strength, boardDanger, p.position, ctx, p.random);
        DpNpcEngine.updateHandPlanForLaterStreetIfNeeded(room, bot, BotType.LAG, strength, boardDanger, ctx);
        DpNpcEngine.StackContext maniStackCtx = ctx.stackCtx;
        int maniTotalStack = bot.getBet() + bot.getChips();
        double maniCommitFactor;
        if (strength == SimpleStrength.MONSTER || strength == SimpleStrength.STRONG) {
            maniCommitFactor = 0.9;
        } else if (strength == SimpleStrength.MEDIUM) {
            maniCommitFactor = 0.6;
        } else {
            maniCommitFactor = 0.35;
        }
        if (boardDanger == BoardDanger.WET) {
            maniCommitFactor *= 0.8;
        }
        if (maniStackCtx != null
                && maniStackCtx.avgStackBB >= RuleNpcConfig.DEEP_TABLE_AVG_BB
                && (strength == SimpleStrength.WEAK || strength == SimpleStrength.MEDIUM)) {
            maniCommitFactor *= 0.85;
        }
        maniCommitFactor *= looseAggroMul;
        if (!"preflop".equals(stage)) {
            double sprL = DpNpcEngine.computeHeroPotSpr(room, bot);
            if (sprL > 13.0 && (strength == SimpleStrength.WEAK || strength == SimpleStrength.MEDIUM)) {
                maniCommitFactor *= 0.9;
            }
        }
        if (maniCommitFactor < 0.1) {
            maniCommitFactor = 0.1;
        }
        if (maniCommitFactor > 0.98) {
            maniCommitFactor = 0.98;
        }
        double maniCommitThreshold = maniTotalStack * maniCommitFactor;

        if (callAmount >= chips) {
            double allInProb = 0.9 * looseAggroMul + 0.1 * (1.0 - looseAggroMul);
            double foldProb = 0.1;
            if (strength == SimpleStrength.WEAK) {
                allInProb -= 0.25;
                foldProb += 0.25;
            }
            if (strength == SimpleStrength.WEAK && ctx.activeVillains >= 2) {
                allInProb -= 0.12;
                foldProb += 0.08;
            }
            ActionCredibility maniCred = ctx.credibility;
            double maniShowdownBluff = ctx.showdownBluffiness;
            VillainRangeTier maniTier = ctx.villainTier;
            if (maniCred == ActionCredibility.LOW || maniShowdownBluff > 0.4
                    || maniTier == VillainRangeTier.LOOSE || maniTier == VillainRangeTier.MANIAC) {
                allInProb += 0.1;
                foldProb = Math.max(0.05, foldProb - 0.05);
            } else if (maniCred == ActionCredibility.HIGH || maniShowdownBluff < 0.1
                    || maniTier == VillainRangeTier.NIT || maniTier == VillainRangeTier.TIGHT) {
                allInProb -= 0.15;
                foldProb += 0.15;
            }
            if (allInProb < 0.5) {
                allInProb = 0.5;
            }
            if (allInProb > 0.98) {
                allInProb = 0.98;
            }
            foldProb = DpNpcEngine.adjustFoldProbForEquityVsPotOdds(
                    foldProb,
                    callAmount,
                    ctx.potOdds,
                    ctx.equityEst,
                    ctx.activeVillains,
                    0.78);
            foldProb = Math.min(0.5, Math.max(0.03, foldProb));
            double x = p.random.nextDouble();
            if (x < foldProb) {
                return new BotAction(BotActionType.FOLD, 0);
            }
            if (x < foldProb + allInProb) {
                if (DpNpcEngine.shouldSkipAggressiveActionByPlan(bot, stage)) {
                    return new BotAction(BotActionType.CALL_OR_CHECK, 0);
                }
                return new BotAction(BotActionType.ALL_IN, chips);
            }
            return new BotAction(BotActionType.CALL_OR_CHECK, 0);
        }

        double r = p.random.nextDouble();

        if (callAmount == 0) {
            double raiseProb = (0.75 + mood * 0.15) * looseAggroMul;
            double aggroFactor = 0.7 + 0.6 * aggression;
            raiseProb *= aggroFactor;
            HandPlanType lp = DpNpcEngine.getHandPlanType(bot);
            if (lp == HandPlanType.GIVE_UP && strength == SimpleStrength.WEAK) {
                raiseProb *= 0.58;
            } else if (lp == HandPlanType.VALUE
                    && (strength == SimpleStrength.STRONG || strength == SimpleStrength.MONSTER)) {
                raiseProb *= 1.08;
            } else if (lp == HandPlanType.POT_CONTROL) {
                raiseProb *= 0.9;
            }
            raiseProb = Math.max(0.5, Math.min(0.98, raiseProb));

            if (ctx.activeVillains >= 2 && strength == SimpleStrength.WEAK) {
                raiseProb *= 0.68;
                raiseProb = Math.max(0.45, raiseProb);
            }

            if (r < raiseProb) {
                int maxMulti;
                int minMulti;
                if (strength == SimpleStrength.STRONG) {
                    minMulti = 3;
                    maxMulti = 5;
                } else if (strength == SimpleStrength.MEDIUM) {
                    minMulti = 2;
                    maxMulti = 4;
                } else {
                    minMulti = 1;
                    maxMulti = 3;
                }
                int range = Math.max(1, maxMulti - minMulti + 1);
                int multiplier = minMulti + p.random.nextInt(range);
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

        double maniFoldBase = 0.03;
        if (strength == SimpleStrength.WEAK && callRatio > 0.85 && boardDanger == BoardDanger.WET) {
            maniFoldBase = 0.2;
        }
        ActionCredibility maniCred2 = ctx.credibility;
        double maniShowdownBluff2 = ctx.showdownBluffiness;
        VillainRangeTier maniTier2 = ctx.villainTier;
        if (maniCred2 == ActionCredibility.LOW || maniShowdownBluff2 > 0.4
                || maniTier2 == VillainRangeTier.LOOSE || maniTier2 == VillainRangeTier.MANIAC) {
            maniFoldBase *= 0.5;
        } else if (maniCred2 == ActionCredibility.HIGH || maniShowdownBluff2 < 0.1
                || maniTier2 == VillainRangeTier.NIT || maniTier2 == VillainRangeTier.TIGHT) {
            maniFoldBase += 0.05;
        }
        double maniFoldProb = maniFoldBase - mood * 0.2;
        maniFoldProb *= (1.0 - 0.4 * callStation);
        if (maniFoldProb < 0.0) {
            maniFoldProb = 0.0;
        }
        maniFoldProb += DpNpcEngine.multiwayFoldProbBoost(ctx.activeVillains, p.checkRaiseFear);
        if (callAmount > 0) {
            maniFoldProb = DpNpcEngine.adjustFoldProbForEquityVsPotOdds(
                    maniFoldProb,
                    callAmount,
                    ctx.potOdds,
                    ctx.equityEst,
                    ctx.activeVillains,
                    0.78);
        }
        if (maniFoldProb > 0.58) {
            maniFoldProb = 0.58;
        }
        if (callAmount > 0 && p.random.nextDouble() < maniFoldProb) {
            return new BotAction(BotActionType.FOLD, 0);
        }

        if (r < 0.25 + mood * 0.05) {
            return new BotAction(BotActionType.CALL_OR_CHECK, 0);
        }
        if (r < 0.85 + mood * 0.05) {
            int minMulti;
            int maxMulti;
            if (strength == SimpleStrength.STRONG) {
                minMulti = 3;
                maxMulti = 6;
            } else if (strength == SimpleStrength.MEDIUM) {
                minMulti = 3;
                maxMulti = 5;
            } else {
                minMulti = 2;
                maxMulti = 4;
            }
            int range2 = Math.max(1, maxMulti - minMulti + 1);
            int multiplier2 = minMulti + p.random.nextInt(range2);
            int extra = room.getBigBlindChips() * multiplier2;
            int target = callAmount + extra;
            int raiseAmount = Math.min(chips, target);
            if (raiseAmount <= 0) {
                return new BotAction(BotActionType.CALL_OR_CHECK, 0);
            }
            int heroInvestAfterRaise = bot.getBet() + raiseAmount;
            if (heroInvestAfterRaise >= maniCommitThreshold) {
                double y = p.random.nextDouble();
                y = DpNpcEngine.skewCommitThresholdRandom(y, ctx, callAmount, 0.88, true);
                if (strength == SimpleStrength.WEAK) {
                    if (ctx.activeVillains >= 2) {
                        return new BotAction(BotActionType.CALL_OR_CHECK, 0);
                    }
                    if (y < 0.9) {
                        return new BotAction(BotActionType.CALL_OR_CHECK, 0);
                    }
                    return new BotAction(BotActionType.FOLD, 0);
                }
                if (DpNpcEngine.shouldSkipAggressiveActionByPlan(bot, stage)) {
                    if (y < 0.55) {
                        return new BotAction(BotActionType.CALL_OR_CHECK, 0);
                    }
                    return new BotAction(BotActionType.FOLD, 0);
                }
                if (strength == SimpleStrength.MEDIUM && ctx.activeVillains >= 2 && y < 0.62) {
                    return new BotAction(BotActionType.CALL_OR_CHECK, 0);
                }
                if (y < 0.7) {
                    return new BotAction(BotActionType.ALL_IN, chips);
                }
                if (y < 0.9) {
                    return new BotAction(BotActionType.CALL_OR_CHECK, 0);
                }
                return new BotAction(BotActionType.FOLD, 0);
            }
            int bb = room.getBigBlindChips();
            if (bb > 0 && room.getPot() > bb * 20 && p.random.nextDouble() < 0.4) {
                if (!DpNpcEngine.shouldSkipAggressiveActionByPlan(bot, stage)
                        && ctx.activeVillains <= 1
                        && strength != SimpleStrength.WEAK) {
                    return new BotAction(BotActionType.ALL_IN, chips);
                }
            }
            if (DpNpcEngine.shouldSkipAggressiveActionByPlan(bot, stage)) {
                return new BotAction(BotActionType.CALL_OR_CHECK, 0);
            }
            DpNpcEngine.consumeOneBarrelIfAny(bot, stage);
            return new BotAction(BotActionType.RAISE, raiseAmount);
        }
        if (DpNpcEngine.shouldSkipAggressiveActionByPlan(bot, stage)) {
            return new BotAction(BotActionType.CALL_OR_CHECK, 0);
        }
        if (strength == SimpleStrength.WEAK) {
            return new BotAction(BotActionType.CALL_OR_CHECK, 0);
        }
        if (ctx.activeVillains >= 2
                && strength != SimpleStrength.MONSTER
                && strength != SimpleStrength.STRONG) {
            return new BotAction(BotActionType.CALL_OR_CHECK, 0);
        }
        return new BotAction(BotActionType.ALL_IN, chips);
    }
}
