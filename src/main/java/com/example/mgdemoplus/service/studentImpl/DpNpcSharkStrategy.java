package com.example.mgdemoplus.service.studentImpl;

import com.example.mgdemoplus.entity.DpPlayer;
import com.example.mgdemoplus.entity.DpRoom;
import com.example.mgdemoplus.entity.PlayerStats;

import java.util.List;
import java.util.Random;

import static com.example.mgdemoplus.service.studentImpl.DpHandEvaluator.SimpleStrength;

/**
 * 单独拆出来的 Shark 决策逻辑（只负责 SHARK 分支）。
 *
 * <p>目标：减少 {@link DpNpcEngine} 的体积，让你把“Shark 大脑”单文件发给 LLM 更省 token。</p>
 *
 * <p>说明：这里不改变任何策略逻辑，只是从 DpNpcEngine 的 case SHARK 中搬运出来。</p>
 */
final class DpNpcSharkStrategy {
    private DpNpcSharkStrategy() {
    }

    static DpNpcEngine.BotAction decide(
            DpRoom room,
            DpPlayer bot,
            DpNpcEngine.BotType type,
            int chips,
            int callAmount,
            double callRatio,
            DpNpcEngine.TablePosition position,
            SimpleStrength strength,
            DpNpcEngine.BoardDanger boardDanger,
            DpNpcEngine.NpcDifficulty difficulty,
            Random random,
            double mood,
            double callStation,
            double checkRaiseFear
    ) {
        DpNpcEngine.BoardDanger bd = boardDanger;
        SimpleStrength st = strength;
        String stage = room.getCurrentStage();
        // 约定：本策略类只负责翻后（preflop 仍由 DpNpcEngine 处理）
        if ("preflop".equals(stage)) {
            return null;
        }

        SmartContext ctx = DpNpcEngine.buildSmartContext(
                room,
                bot,
                st,
                stage,
                callAmount,
                difficulty,
                random
        );

        // 在 flop 首次行动时为 Shark 初始化整手 HandPlan
        DpNpcEngine.initHandPlanIfNeededForPostflop(
                room,
                bot,
                type,
                st,
                bd,
                position,
                ctx,
                random
        );

        DpNpcEngine.StackContext stackCtx = ctx.stackCtx;
        int activeVillains = ctx.activeVillains;
        DpNpcEngine.CounterStrategyProfile counter = ctx.counterStrategy;

        int sharkTotalStack = bot.getBet() + bot.getChips();
        double sharkCommitFactor;
        if (st == SimpleStrength.MONSTER) {
            sharkCommitFactor = 0.95;
        } else if (st == SimpleStrength.STRONG) {
            sharkCommitFactor = 0.75;
        } else if (st == SimpleStrength.MEDIUM) {
            sharkCommitFactor = 0.5;
        } else {
            sharkCommitFactor = 0.3;
        }
        if (!"preflop".equals(stage) && bd == DpNpcEngine.BoardDanger.WET) {
            // 潮湿牌面：即便是 Shark，也更愿意控池
            sharkCommitFactor *= 0.8;
        }
        if (!"preflop".equals(stage)
                && stackCtx != null
                && stackCtx.avgStackBB >= DpNpcEngine.SharkConfig.DEEP_TABLE_AVG_BB
                && (st == SimpleStrength.WEAK || st == SimpleStrength.MEDIUM)) {
            // 深码 + 弱/中牌：整体减少愿意投入的总筹码比例
            sharkCommitFactor *= 0.85;
        }
        if (sharkCommitFactor < 0.15) sharkCommitFactor = 0.15;
        if (sharkCommitFactor > 0.98) sharkCommitFactor = 0.98;
        final double sharkCommitThreshold = sharkTotalStack * sharkCommitFactor;

        double baseFold = 0.0;
        if (st == SimpleStrength.WEAK && callAmount > 0 && callRatio > 0.4) {
            baseFold = (bd == DpNpcEngine.BoardDanger.WET) ? 0.7 : 0.5;
        }

        DpPlayer aggressor = ctx.aggressor;
        PlayerStats aggressorStats = ctx.aggressorStats;
        PlayerStats.SingleHandStats lastAggressorStats = null;
        if (aggressorStats != null && !aggressorStats.getRecentHands().isEmpty()) {
            lastAggressorStats = aggressorStats.getRecentHands().peekLast();
        }
        DpNpcEngine.VillainRangeTier villainTier = ctx.villainTier;
        DpNpcEngine.ActionCredibility credibility = ctx.credibility;
        double showdownBluffiness = ctx.showdownBluffiness;
        String villainName = aggressor != null ? aggressor.getNickname() : "-";

        // 仅在有跟注额时根据对手风格调整弃牌率（免费看牌时不因“石头型”而弃牌）
        if (callAmount > 0 && aggressorStats != null) {
            double overallPart = aggressorStats.getOverallParticipationRate();
            double overallRaise = aggressorStats.getOverallRaiseRate();

            boolean looseAggressiveLong = overallPart > 0.5 && overallRaise > 0.3;
            boolean tightPassiveLong = overallPart < 0.25 && overallRaise < 0.15;

            boolean looseAggressiveRecent = lastAggressorStats != null
                    && lastAggressorStats.isParticipated() && lastAggressorStats.isRaised();
            boolean tightPassiveRecent = lastAggressorStats != null
                    && !lastAggressorStats.isParticipated() && !lastAggressorStats.isRaised();

            if (looseAggressiveLong || looseAggressiveRecent) {
                baseFold *= 0.4;
            } else if (tightPassiveLong || tightPassiveRecent) {
                baseFold = Math.min(1.0, baseFold + 0.35);
            }
        }

        // 整合：根据对手动作可信度 + 摊牌 bluff 倾向调整
        if (callAmount > 0) {
            if (credibility == DpNpcEngine.ActionCredibility.LOW) {
                baseFold *= 0.6;
            } else if (credibility == DpNpcEngine.ActionCredibility.HIGH) {
                baseFold = Math.min(1.0, baseFold * 1.2);
            }
            if (showdownBluffiness > 0.4) {
                baseFold *= 0.7;
            } else if (showdownBluffiness < 0.1) {
                baseFold = Math.min(1.0, baseFold * 1.2);
            }
        }

        double potOdds = ctx.potOdds;
        if (callAmount > 0) {
            if (potOdds > 0.5) {
                baseFold = Math.min(1.0, baseFold * 1.15);
            } else if (potOdds < 0.25) {
                baseFold = Math.max(0.0, baseFold * 0.85);
            }
        }

        double equityEst = ctx.equityEst;
        if (callAmount > 0) {
            if (equityEst + DpNpcEngine.SharkConfig.EQUITY_POTODDS_EPS < potOdds) {
                baseFold = Math.min(1.0, baseFold + DpNpcEngine.SharkConfig.EQUITY_FOLD_BOOST);
            }
            if (equityEst > potOdds + DpNpcEngine.SharkConfig.EQUITY_POTODDS_MARGIN) {
                baseFold = Math.max(0.0, baseFold * DpNpcEngine.SharkConfig.EQUITY_FOLD_SHRINK);
            }
            if (st == SimpleStrength.WEAK && callRatio > 0.5 && potOdds > 0.45) {
                baseFold = Math.min(1.0, Math.max(baseFold, 0.85));
            }
        }

        if (counter != null && callAmount > 0) {
            if (counter.foldMoreToBigBets && callRatio > 0.5) {
                baseFold = Math.min(1.0, baseFold + 0.1);
            }
            if (counter.callDownMore) {
                baseFold = Math.max(0.0, baseFold * 0.8);
            }
            if (counter.bluffCatchMore && st != SimpleStrength.WEAK && potOdds < 0.5) {
                baseFold = Math.max(0.0, baseFold * 0.85);
            }
        }

        if (callAmount > 0 && activeVillains >= 3) {
            double boost = DpNpcEngine.SharkConfig.MULTIWAY_FOLD_BOOST * (0.5 + 0.5 * checkRaiseFear);
            boost *= (1.0 - 0.4 * callStation);
            baseFold = Math.min(1.0, baseFold + boost);
        }

        DpNpcEngine.HandPlanType planShark = DpNpcEngine.getHandPlanType(bot);
        if (planShark == DpNpcEngine.HandPlanType.GIVE_UP && callAmount > 0) {
            baseFold = Math.min(1.0, baseFold + 0.15);
        }

        double foldProbShark = Math.min(1.0, Math.max(0.0, baseFold + (-mood) * 0.1));
        foldProbShark = DpNpcEngine.applySoftNoise(foldProbShark, DpNpcEngine.SharkConfig.PROB_NOISE_DELTA, random);
        if (callAmount > 0 && foldProbShark > 0 && random.nextDouble() < foldProbShark) {
            return new DpNpcEngine.BotAction(DpNpcEngine.BotActionType.FOLD, 0);
        }

        double r = random.nextDouble();

        // 位置意识：翻后无人下注时，晚位在干燥牌面上有一定概率用弱/中牌做小额 c-bet
        if (callAmount == 0
                && ("flop".equals(stage) || "turn".equals(stage))
                && position == DpNpcEngine.TablePosition.LATE
                && bd == DpNpcEngine.BoardDanger.DRY
                && (st == SimpleStrength.WEAK || st == SimpleStrength.MEDIUM)) {
            if (DpNpcEngine.shouldSkipAggressiveActionByPlan(bot, stage)) {
                return new DpNpcEngine.BotAction(DpNpcEngine.BotActionType.CALL_OR_CHECK, 0);
            }
            double baseCbetProb = (st == SimpleStrength.MEDIUM) ? 0.55 : 0.45;
            if (villainTier == DpNpcEngine.VillainRangeTier.MANIAC || villainTier == DpNpcEngine.VillainRangeTier.LOOSE) {
                baseCbetProb -= 0.05;
            } else if (villainTier == DpNpcEngine.VillainRangeTier.NIT || villainTier == DpNpcEngine.VillainRangeTier.TIGHT) {
                baseCbetProb += 0.05;
            }
            if (stackCtx.avgStackBB >= DpNpcEngine.SharkConfig.DEEP_TABLE_AVG_BB) {
                baseCbetProb += 0.05;
            }
            baseCbetProb += mood * 0.05;
            if (baseCbetProb < 0.2) baseCbetProb = 0.2;
            if (baseCbetProb > 0.8) baseCbetProb = 0.8;
            baseCbetProb = DpNpcEngine.applySoftNoise(baseCbetProb, DpNpcEngine.SharkConfig.PROB_NOISE_DELTA, random);

            if (random.nextDouble() < baseCbetProb) {
                int sb = DpRoom.getSBChips();
                int pot = room.getPot();
                double sizeFactor = 0.33 + random.nextDouble() * 0.17; // 约 1/3 ~ 1/2 pot
                int target = (int) Math.round(pot * sizeFactor);
                int minBet = DpRoom.getBBChips() * 2;
                if (target < minBet) target = minBet;
                int raiseAmount = Math.min(chips, target);
                if (sb > 0 && raiseAmount > 0) {
                    int units = Math.max(1, Math.round(raiseAmount * 1.0f / sb));
                    raiseAmount = units * sb;
                    if (raiseAmount > chips) {
                        units = Math.max(1, chips / sb);
                        raiseAmount = units * sb;
                    }
                }
                DpNpcEngine.consumeOneBarrelIfAny(bot, stage);
                return new DpNpcEngine.BotAction(DpNpcEngine.BotActionType.RAISE, raiseAmount);
            }
        }

        boolean isLateStreet = "river".equals(stage);
        double callProb = 0.6 + mood * 0.05;
        if (st == SimpleStrength.STRONG) {
            callProb -= 0.25;
            if (!isLateStreet) {
                callProb += 0.15;
            }
        } else if (st == SimpleStrength.MEDIUM) {
            callProb += 0.05;
        }

        if (st == SimpleStrength.WEAK && callAmount > 0) {
            int bbForBluff = DpRoom.getBBChips();
            int villainStack = aggressor != null ? aggressor.getChips() : 0;
            double villainBB = bbForBluff > 0 ? villainStack * 1.0 / bbForBluff : 0.0;
            if (villainBB > DpNpcEngine.SharkConfig.DEEP_STACK_MIN_BB && stackCtx.avgStackBB > DpNpcEngine.SharkConfig.DEEP_TABLE_AVG_BB) {
                callProb += 0.10;
            }
            if (villainBB > 0 && villainBB <= DpNpcEngine.SharkConfig.SHORT_STACK_BB) {
                callProb += 0.05;
            }
        }

        if (position == DpNpcEngine.TablePosition.EARLY && bd == DpNpcEngine.BoardDanger.WET && st == SimpleStrength.WEAK) {
            if (callAmount > 0) {
                callProb -= 0.08;
            }
        }

        callProb = DpNpcEngine.applySoftNoise(callProb, DpNpcEngine.SharkConfig.PROB_NOISE_DELTA, random);
        if (r < Math.max(0.1, Math.min(0.9, callProb))) {
            return new DpNpcEngine.BotAction(DpNpcEngine.BotActionType.CALL_OR_CHECK, 0);
        }

        if (chips > callAmount) {
            if (DpNpcEngine.shouldSkipAggressiveActionByPlan(bot, stage)) {
                // HandPlan 已用尽进攻额度或为 GIVE_UP：不再主动加注，只考虑跟注/过牌
                return new DpNpcEngine.BotAction(DpNpcEngine.BotActionType.CALL_OR_CHECK, 0);
            }
            int sb = DpRoom.getSBChips();
            int bb = DpRoom.getBBChips();
            int raiseAmount;
            if (callAmount == 0) {
                int pot = room.getPot();
                double baseFactor;
                if (st == SimpleStrength.STRONG || st == SimpleStrength.MONSTER) {
                    baseFactor = 0.8;
                } else if (st == SimpleStrength.MEDIUM) {
                    baseFactor = 0.6;
                } else {
                    baseFactor = 0.45;
                }
                double randomFactor = 0.9 + random.nextDouble() * 0.3; // 0.9~1.2
                int target = (int) Math.round(pot * baseFactor * randomFactor);

                // 短码桌：若存在典型短码且自己为强牌/怪兽牌，适当提高下注倍数
                if (stackCtx.minStackBB > 0 && stackCtx.minStackBB <= DpNpcEngine.SharkConfig.SHORT_STACK_BB
                        && (st == SimpleStrength.STRONG || st == SimpleStrength.MONSTER)) {
                    int effectiveBase = pot > 0 ? Math.min(pot, stackCtx.minStack) : stackCtx.minStack;
                    double shortFactor = 0.4 + random.nextDouble() * 0.4;
                    int shortTarget = (int) Math.round(effectiveBase * shortFactor);
                    if (shortTarget > target) {
                        target = shortTarget;
                    }
                }

                // 深码桌 + 弱牌：缩小 c-bet
                if (stackCtx.avgStackBB >= DpNpcEngine.SharkConfig.DEEP_TABLE_AVG_BB && st == SimpleStrength.WEAK
                        && ("flop".equals(stage) || "turn".equals(stage))) {
                    target = (int) Math.round(target * 0.8);
                }

                int minBet = bb * 2;
                if (target < minBet) {
                    target = minBet;
                }
                raiseAmount = Math.min(chips, target);
            } else {
                int strengthFactor;
                if (st == SimpleStrength.STRONG || st == SimpleStrength.MONSTER) {
                    strengthFactor = 5;
                } else if (st == SimpleStrength.MEDIUM) {
                    strengthFactor = 4;
                } else {
                    strengthFactor = 3;
                }
                int streetFactor = isLateStreet ? 2 : 1;
                int extraMin = bb * strengthFactor;
                int extraMax = bb * (strengthFactor + streetFactor);
                if (extraMax < extraMin) {
                    extraMax = extraMin;
                }
                int extraRange = extraMax - extraMin + 1;
                int extra = extraMin + random.nextInt(Math.max(1, extraRange));

                int target = callAmount + extra;

                // 根据主要对手 stack 深度微调加注额
                int aggressorStack = (aggressor != null) ? aggressor.getChips() : 0;
                double aggressorStackBB = (bb > 0) ? (aggressorStack * 1.0 / bb) : 0.0;

                if (aggressorStackBB > 0 && aggressorStackBB <= DpNpcEngine.SharkConfig.SHORT_STACK_BB) {
                    if (st == SimpleStrength.STRONG || st == SimpleStrength.MONSTER) {
                        double pressureFactor = 0.6 + random.nextDouble() * 0.4;
                        int desired = callAmount + (int) Math.round(aggressorStack * pressureFactor);
                        if (desired > target) {
                            target = desired;
                        }
                    } else if (st == SimpleStrength.WEAK) {
                        int maxBluffExtra = bb * 3;
                        int bluffCap = callAmount + maxBluffExtra;
                        if (target > bluffCap) {
                            target = bluffCap;
                        }
                    }
                } else if (stackCtx.minStackBB > DpNpcEngine.SharkConfig.DEEP_STACK_MIN_BB
                        && stackCtx.avgStackBB > DpNpcEngine.SharkConfig.DEEP_TABLE_AVG_BB) {
                    if (st == SimpleStrength.STRONG || st == SimpleStrength.MONSTER) {
                        if ("turn".equals(stage) || "river".equals(stage)) {
                            double hvRatio = DpNpcEngine.computeHeroVsVillainStackRatio(bot, aggressor);
                            if (hvRatio >= 1.5 && aggressorStackBB >= 25 && aggressorStackBB <= 60) {
                                target = (int) Math.round(target * 1.15);
                            }
                        }
                    } else if (st == SimpleStrength.WEAK) {
                        target = (int) Math.round(target * 0.9);
                    }
                }

                if (activeVillains >= 3 && st == SimpleStrength.WEAK && callAmount > 0) {
                    target = (int) Math.round(target * 0.85);
                }

                raiseAmount = Math.min(chips, target);
                int heroInvestAfterRaise = bot.getBet() + raiseAmount;
                if (heroInvestAfterRaise > sharkCommitThreshold) {
                    double y = random.nextDouble();
                    if (st == SimpleStrength.MONSTER || st == SimpleStrength.STRONG) {
                        if (y < 0.5) {
                            return new DpNpcEngine.BotAction(DpNpcEngine.BotActionType.ALL_IN, chips);
                        } else {
                            return new DpNpcEngine.BotAction(DpNpcEngine.BotActionType.CALL_OR_CHECK, 0);
                        }
                    } else if (st == SimpleStrength.MEDIUM) {
                        if (y < 0.8) {
                            return new DpNpcEngine.BotAction(DpNpcEngine.BotActionType.CALL_OR_CHECK, 0);
                        } else {
                            return new DpNpcEngine.BotAction(DpNpcEngine.BotActionType.FOLD, 0);
                        }
                    } else {
                        if (y < 0.85) {
                            return new DpNpcEngine.BotAction(DpNpcEngine.BotActionType.FOLD, 0);
                        } else {
                            return new DpNpcEngine.BotAction(DpNpcEngine.BotActionType.ALL_IN, chips);
                        }
                    }
                }

                if (callAmount > 0 && bb > 0) {
                    int minExtraBB = 3;
                    if (room.getPot() > bb * 20) {
                        minExtraBB = 4;
                    }
                    int minRaise = callAmount + minExtraBB * bb;
                    if (raiseAmount < minRaise) {
                        raiseAmount = Math.min(chips, minRaise);
                    }
                }
            }

            if (sb > 0 && raiseAmount > 0) {
                int units = Math.max(1, Math.round(raiseAmount * 1.0f / sb));
                raiseAmount = units * sb;
                if (raiseAmount > chips) {
                    units = Math.max(1, chips / sb);
                    raiseAmount = units * sb;
                }
            }

            if ("river".equals(stage)
                    && st == SimpleStrength.MONSTER
                    && bd == DpNpcEngine.BoardDanger.DRY
                    && room.getPot() > 0
                    && random.nextDouble() < DpNpcEngine.SharkConfig.RIVER_OVERBET_PROB) {
                int pot = room.getPot();
                double factor = 1.2 + random.nextDouble() * 0.3;
                int overTarget = (int) Math.round(pot * factor);
                if (overTarget > raiseAmount) {
                    raiseAmount = Math.min(chips, overTarget);
                }
            }

            DpNpcEngine.consumeOneBarrelIfAny(bot, stage);
            return new DpNpcEngine.BotAction(DpNpcEngine.BotActionType.RAISE, raiseAmount);
        }

        if ("river".equals(stage)
                && callAmount == 0
                && st == SimpleStrength.MEDIUM
                && chips > 0
                && random.nextDouble() < DpNpcEngine.SharkConfig.RIVER_BLOCK_PROB) {
            int sb = DpRoom.getSBChips();
            int pot = room.getPot();
            double factor = 0.33;
            int target = (int) Math.round(pot * factor);
            int bb = DpRoom.getBBChips();
            int minBet = bb * 2;
            if (target < minBet) {
                target = minBet;
            }
            int raiseAmount = Math.min(chips, target);
            if (sb > 0 && raiseAmount > 0) {
                int units = Math.max(1, Math.round(raiseAmount * 1.0f / sb));
                raiseAmount = units * sb;
                if (raiseAmount > chips) {
                    units = Math.max(1, chips / sb);
                    raiseAmount = units * sb;
                }
            }
            return new DpNpcEngine.BotAction(DpNpcEngine.BotActionType.RAISE, raiseAmount);
        }

        return new DpNpcEngine.BotAction(DpNpcEngine.BotActionType.CALL_OR_CHECK, 0);
    }
}

