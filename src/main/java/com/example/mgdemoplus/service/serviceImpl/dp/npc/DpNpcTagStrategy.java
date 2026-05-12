package com.example.mgdemoplus.service.serviceImpl.dp.npc;

import static com.example.mgdemoplus.utils.dp.DpUtilHandEvaluator.SimpleStrength;

import com.example.mgdemoplus.service.serviceImpl.dp.DpNpcEngine;
import com.example.mgdemoplus.service.serviceImpl.dp.DpNpcEngine.ActionCredibility;
import com.example.mgdemoplus.service.serviceImpl.dp.DpNpcEngine.BoardDanger;
import com.example.mgdemoplus.service.serviceImpl.dp.DpNpcEngine.BotAction;
import com.example.mgdemoplus.service.serviceImpl.dp.DpNpcEngine.BotActionType;
import com.example.mgdemoplus.service.serviceImpl.dp.DpNpcEngine.BotType;
import com.example.mgdemoplus.service.serviceImpl.dp.DpNpcEngine.CounterStrategyProfile;
import com.example.mgdemoplus.service.serviceImpl.dp.DpNpcEngine.HandPlanType;
import com.example.mgdemoplus.service.serviceImpl.dp.DpNpcEngine.RuleNpcConfig;
import com.example.mgdemoplus.utils.dp.DpUtilSmartContext;

/**
 * 紧凶（TAG）：翻后规则独立维护。
 */
public final class DpNpcTagStrategy {
    private DpNpcTagStrategy() {
    }

    public static BotAction decide(DpNpcRuleDecisionParams p) {
        // System.out.println("DpNpcTagStrategy");
        BoardDanger bd = p.boardDanger;
        SimpleStrength st = p.strength;
        String stage = p.room.getCurrentStage();
        int callAmount = Math.max(0, p.room.getCurrentBetToCall() - p.bot.getBet());
        double callRatio = p.chips == 0 ? 1.0 : (callAmount * 1.0 / p.chips);
        DpUtilSmartContext ctx = DpNpcEngine.buildSmartContext(p.room, p.bot, st, stage, callAmount, p.random);
        DpNpcEngine.initHandPlanIfNeededForPostflop(
                p.room,
                p.bot,
                BotType.TAG,
                st,
                bd,
                p.position,
                ctx,
                p.random);
        DpNpcEngine.updateHandPlanForLaterStreetIfNeeded(p.room, p.bot, BotType.TAG, st, bd, ctx);

        DpNpcEngine.StackContext tagStackCtx = ctx.stackCtx;
        int tagTotalStack = p.bot.getBet() + p.bot.getChips();
        double tagCommitFactor;
        if (st == SimpleStrength.MONSTER) {
            tagCommitFactor = 0.85;
        } else if (st == SimpleStrength.STRONG) {
            tagCommitFactor = 0.65;
        } else if (st == SimpleStrength.MEDIUM) {
            tagCommitFactor = 0.45;
        } else {
            tagCommitFactor = 0.25;
        }
        if (!"preflop".equals(stage) && bd == BoardDanger.WET) {
            tagCommitFactor *= 0.85;
        }
        if (!"preflop".equals(stage)
                && tagStackCtx != null
                && tagStackCtx.avgStackBB >= RuleNpcConfig.DEEP_TABLE_AVG_BB
                && (st == SimpleStrength.WEAK || st == SimpleStrength.MEDIUM)) {
            tagCommitFactor *= 0.85;
        }
        if (!"preflop".equals(stage)) {
            double spr = DpNpcEngine.computeHeroPotSpr(p.room, p.bot);
            if (spr < 2.5 && (st == SimpleStrength.MONSTER || st == SimpleStrength.STRONG)) {
                tagCommitFactor = Math.min(0.92, tagCommitFactor * 1.06);
            } else if (spr > 14.0 && (st == SimpleStrength.WEAK || st == SimpleStrength.MEDIUM)) {
                tagCommitFactor *= 0.88;
            }
        }
        if (tagCommitFactor < 0.15) {
            tagCommitFactor = 0.15;
        }
        if (tagCommitFactor > 0.9) {
            tagCommitFactor = 0.9;
        }
        final double tagCommitThreshold = tagTotalStack * tagCommitFactor;

        double baseFold = 0.0;
        if (st == SimpleStrength.WEAK) {
            if (callRatio > 0.5) {
                baseFold = (bd == BoardDanger.WET) ? 0.8 : 0.6;
            } else if (bd == BoardDanger.WET) {
                baseFold = 0.45;
            }
        } else if (st == SimpleStrength.MEDIUM && callRatio > 0.6 && bd == BoardDanger.WET) {
            baseFold = 0.35;
        }

        baseFold = Math.min(
                1.0,
                baseFold + DpNpcEngine.multiwayFoldProbBoost(ctx.activeVillains, p.checkRaiseFear));

        double potOdds = ctx.potOdds;
        baseFold = DpNpcEngine.adjustFoldProbForEquityVsPotOdds(
                baseFold,
                callAmount,
                potOdds,
                ctx.equityEst,
                ctx.activeVillains,
                1.0);
        if (st == SimpleStrength.WEAK && potOdds > 0.58) {
            baseFold = Math.min(1.0, baseFold + 0.06);
        } else if (potOdds > 0 && potOdds < 0.22
                && (st == SimpleStrength.MEDIUM || st == SimpleStrength.STRONG)) {
            baseFold = Math.max(0.0, baseFold * 0.92);
        }

        ActionCredibility tagCred = ctx.credibility;
        double tagShowdownBluff = ctx.showdownBluffiness;
        if (tagCred == ActionCredibility.HIGH || tagShowdownBluff < 0.1) {
            baseFold = Math.min(1.0, baseFold + 0.1);
        } else if (tagCred == ActionCredibility.LOW || tagShowdownBluff > 0.4) {
            baseFold = Math.max(0.0, baseFold * 0.7);
        }

        HandPlanType planTag = DpNpcEngine.getHandPlanType(p.bot);
        if (planTag == HandPlanType.GIVE_UP && callAmount > 0) {
            baseFold = Math.min(1.0, baseFold + 0.15);
        }

        CounterStrategyProfile counterTag = ctx.counterStrategy;
        if (counterTag != null && callAmount > 0) {
            if (counterTag.foldMoreToBigBets && callRatio > 0.5) {
                baseFold = Math.min(1.0, baseFold + 0.1);
            }
        }

        double foldProbTag = Math.min(1.0, Math.max(0.0, baseFold + (-p.mood) * 0.05));
        foldProbTag *= (1.0 - 0.5 * p.callStation);
        foldProbTag = DpNpcEngine.applySoftNoise(foldProbTag, RuleNpcConfig.PROB_NOISE_DELTA, p.random);
        if (callAmount > 0 && foldProbTag > 0 && p.random.nextDouble() < foldProbTag) {
            return new BotAction(BotActionType.FOLD, 0);
        }

        if (callAmount == 0) {
            int pot = p.room.getPot();
            int bbPost = p.room.getBigBlindChips();
            int sbPost = p.room.getSmallBlindChips();

            double valueBetProb;
            double factor;
            if (st == SimpleStrength.MONSTER) {
                valueBetProb = 0.85 + p.mood * 0.05;
                factor = "river".equals(stage) ? 0.9 : 0.7;
            } else if (st == SimpleStrength.STRONG) {
                valueBetProb = 0.7 + p.mood * 0.05;
                factor = "river".equals(stage) ? 0.7 : 0.5;
            } else if (st == SimpleStrength.MEDIUM) {
                valueBetProb = ("river".equals(stage) ? 0.2 : 0.35) + p.mood * 0.03;
                factor = 0.4;
            } else {
                return new BotAction(BotActionType.CALL_OR_CHECK, 0);
            }
            if (!"preflop".equals(stage) && st == SimpleStrength.MEDIUM) {
                double sprC = DpNpcEngine.computeHeroPotSpr(p.room, p.bot);
                if (sprC > 16.0) {
                    factor *= 0.86;
                }
            }
            counterTag = ctx.counterStrategy;
            if (counterTag != null && counterTag.moreThinValue
                    && (st == SimpleStrength.MEDIUM || st == SimpleStrength.STRONG
                            || st == SimpleStrength.MONSTER)) {
                valueBetProb *= 1.1;
            }
            double aggroFactor = 0.7 + 0.6 * p.aggression;
            if (planTag == HandPlanType.VALUE) {
                aggroFactor *= 1.1;
            } else if (planTag == HandPlanType.POT_CONTROL) {
                aggroFactor *= 0.8;
            }
            valueBetProb *= aggroFactor;
            if (ctx.activeVillains >= 2 && st == SimpleStrength.MEDIUM) {
                valueBetProb *= 0.72;
            }
            valueBetProb = DpNpcEngine.applySoftNoise(
                    Math.min(0.95, Math.max(0.05, valueBetProb)),
                    RuleNpcConfig.PROB_NOISE_DELTA,
                    p.random);
            if (p.random.nextDouble() < valueBetProb) {
                if (DpNpcEngine.shouldSkipAggressiveActionByPlan(p.bot, stage)) {
                    return new BotAction(BotActionType.CALL_OR_CHECK, 0);
                }
                int target = (int) Math.round(pot * factor);
                int minBet = bbPost * 2;
                if (target < minBet) {
                    target = minBet;
                }
                int raiseAmount = Math.min(p.chips, target);
                if (sbPost > 0 && raiseAmount > 0) {
                    int units = Math.max(1, Math.round(raiseAmount * 1.0f / sbPost));
                    raiseAmount = units * sbPost;
                    if (raiseAmount > p.chips) {
                        units = Math.max(1, p.chips / sbPost);
                        raiseAmount = units * sbPost;
                    }
                }
                DpNpcEngine.consumeOneBarrelIfAny(p.bot, stage);
                return new BotAction(BotActionType.RAISE, raiseAmount);
            }
            return new BotAction(BotActionType.CALL_OR_CHECK, 0);
        }

        double raiseProb;
        if (st == SimpleStrength.MONSTER) {
            raiseProb = 0.8 + p.mood * 0.05;
        } else if (st == SimpleStrength.STRONG) {
            raiseProb = 0.65 + p.mood * 0.05;
        } else if (st == SimpleStrength.MEDIUM) {
            raiseProb = 0.4 + p.mood * 0.03;
        } else {
            raiseProb = 0.1;
        }
        double aggroFactorRaise = 0.7 + 0.6 * p.aggression;
        if (planTag == HandPlanType.VALUE) {
            aggroFactorRaise *= 1.1;
        } else if (planTag == HandPlanType.POT_CONTROL) {
            aggroFactorRaise *= 0.8;
        } else if (planTag == HandPlanType.BLUFF
                && (st == SimpleStrength.WEAK || st == SimpleStrength.MEDIUM)) {
            aggroFactorRaise *= 1.05;
        }
        raiseProb *= aggroFactorRaise;
        if (ctx.activeVillains >= 2) {
            if (st == SimpleStrength.WEAK) {
                raiseProb *= 0.42;
            } else if (st == SimpleStrength.MEDIUM) {
                raiseProb *= 0.76;
            }
        }
        raiseProb = DpNpcEngine.applySoftNoise(
                Math.min(0.9, Math.max(0.05, raiseProb)),
                RuleNpcConfig.PROB_NOISE_DELTA,
                p.random);

        double r = p.random.nextDouble();
        if (r > raiseProb || p.chips <= callAmount) {
            return new BotAction(BotActionType.CALL_OR_CHECK, 0);
        }

        int bb = p.room.getBigBlindChips();
        int extraMinBB;
        int extraMaxBB;
        if (st == SimpleStrength.MONSTER) {
            extraMinBB = 4;
            extraMaxBB = "river".equals(stage) ? 8 : 6;
        } else if (st == SimpleStrength.STRONG) {
            extraMinBB = 3;
            extraMaxBB = "river".equals(stage) ? 6 : 5;
        } else {
            extraMinBB = 2;
            extraMaxBB = 4;
        }
        int range = Math.max(1, extraMaxBB - extraMinBB + 1);
        int extraBB = extraMinBB + p.random.nextInt(range);
        int extra = extraBB * bb;

        int target = callAmount + extra;
        if (!"river".equals(stage) && st == SimpleStrength.MEDIUM) {
            target = (int) Math.round(target * 0.8);
        }
        if (!"preflop".equals(stage)) {
            double sprR = DpNpcEngine.computeHeroPotSpr(p.room, p.bot);
            if (sprR > 12.0 && (st == SimpleStrength.MEDIUM || st == SimpleStrength.WEAK)) {
                target = (int) Math.round(target * 0.88);
            }
        }

        int raiseAmount = Math.min(p.chips, target);
        if (callAmount > 0 && bb > 0) {
            int minExtraBB = (st == SimpleStrength.MONSTER || st == SimpleStrength.STRONG) ? 4 : 3;
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
        if (raiseAmount <= callAmount) {
            return new BotAction(BotActionType.CALL_OR_CHECK, 0);
        }
        if ("preflop".equals(stage) && callRatio > 0.3) {
            double y = p.random.nextDouble();
            if (st == SimpleStrength.MONSTER || st == SimpleStrength.STRONG) {
                if (y < 0.8) {
                    return new BotAction(BotActionType.CALL_OR_CHECK, 0);
                } else {
                    return new BotAction(BotActionType.ALL_IN, p.chips);
                }
            } else if (st == SimpleStrength.MEDIUM) {
                if (y < 0.7) {
                    return new BotAction(BotActionType.CALL_OR_CHECK, 0);
                } else {
                    return new BotAction(BotActionType.FOLD, 0);
                }
            } else {
                if (y < 0.9) {
                    return new BotAction(BotActionType.FOLD, 0);
                } else {
                    return new BotAction(BotActionType.CALL_OR_CHECK, 0);
                }
            }
        }
        int heroInvestAfter = p.bot.getBet() + raiseAmount;
        if (heroInvestAfter > tagCommitThreshold) {
            double y = p.random.nextDouble();
            if (st == SimpleStrength.STRONG || st == SimpleStrength.MONSTER) {
                y = DpNpcEngine.skewCommitThresholdRandom(y, ctx, callAmount, 1.0, false);
                if (y < 0.8) {
                    return new BotAction(BotActionType.CALL_OR_CHECK, 0);
                } else {
                    return new BotAction(BotActionType.ALL_IN, p.chips);
                }
            } else if (st == SimpleStrength.MEDIUM) {
                y = DpNpcEngine.skewCommitThresholdRandom(y, ctx, callAmount, 1.0, true);
                if (y < 0.7) {
                    return new BotAction(BotActionType.CALL_OR_CHECK, 0);
                } else {
                    return new BotAction(BotActionType.FOLD, 0);
                }
            } else {
                y = DpNpcEngine.skewCommitThresholdRandom(y, ctx, callAmount, 1.0, false);
                if (y < 0.85) {
                    return new BotAction(BotActionType.FOLD, 0);
                } else {
                    return new BotAction(BotActionType.CALL_OR_CHECK, 0);
                }
            }
        }
        if (DpNpcEngine.shouldSkipAggressiveActionByPlan(p.bot, stage)) {
            return new BotAction(BotActionType.CALL_OR_CHECK, 0);
        }
        DpNpcEngine.consumeOneBarrelIfAny(p.bot, stage);
        return new BotAction(BotActionType.RAISE, raiseAmount);
    }
}
