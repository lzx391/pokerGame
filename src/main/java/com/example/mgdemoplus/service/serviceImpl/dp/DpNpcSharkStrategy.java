package com.example.mgdemoplus.service.serviceImpl.dp;

import com.example.mgdemoplus.bo.DpRoomBO;
import com.example.mgdemoplus.entity.dp.DpPlayer;
import com.example.mgdemoplus.utils.dp.DpUtilSmartContext;
import com.example.mgdemoplus.entity.dp.DpPlayerStats;

import static com.example.mgdemoplus.utils.dp.DpUtilHandEvaluator.SimpleStrength;

import java.util.Random;

/**
 * 单独拆出来的 Shark 决策逻辑（只负责 SHARK 分支）。
 *
 * <p>
 * 目标：减少 {@link DpNpcEngine} 的体积，让你把“Shark 大脑”单文件发给 LLM 更省 token。
 * </p>
 *
 * <p>
 * 说明：这里不改变任何策略逻辑，只是从 DpNpcEngine 的 case SHARK 中搬运出来。
 * </p>
 */
final class DpNpcSharkStrategy {
    private DpNpcSharkStrategy() {
    }

    private static void dbg(DpRoomBO room, DpNpcEngine.BotType type, DpPlayer bot, String msg) {
        if (type != DpNpcEngine.BotType.SHARK)
            return;
        if (bot == null)
            return;
        String rid = room != null ? room.getRoomId() : "-";
        // System.out.println("[SHARK][room=" + rid + "] " + msg);
    }

    static DpNpcEngine.BotAction decide(
            DpRoomBO room,
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
            double checkRaiseFear) {
        DpNpcEngine.BoardDanger bd = boardDanger;
        SimpleStrength st = strength;
        String stage = room.getCurrentStage();
        // 约定：本策略类只负责翻后（preflop 仍由 DpNpcEngine 处理）
        if ("preflop".equals(stage)) {
            return null;
        }

        // Shark 翻后细化：公共牌主导时，降低“自信牌力”，避免把 board 强误当自己强
        boolean boardDominant = DpNpcEngine.isBoardDominantBestHand(room, bot);
        if (boardDominant) {
            if (st == SimpleStrength.MONSTER)
                st = SimpleStrength.STRONG;
            else if (st == SimpleStrength.STRONG)
                st = SimpleStrength.MEDIUM;
            else if (st == SimpleStrength.MEDIUM)
                st = SimpleStrength.MEDIUM; // 保持
        }

        dbg(room, type, bot, "ENTER stage=" + stage
                + " st=" + st
                + " bd=" + bd
                + " pos=" + position
                + " chips=" + chips
                + " botBet=" + bot.getBet()
                + " curBTC=" + room.getCurrentBetToCall()
                + " callAmount(arg)=" + callAmount
                + " callRatio(arg)=" + String.format("%.3f", callRatio)
                + " pot=" + room.getPot()
                + " activeVillains=?");

        DpUtilSmartContext ctx = DpNpcEngine.buildSmartContext(
                room,
                bot,
                st,
                stage,
                callAmount,
                difficulty,
                random);

        // 在 flop 首次行动时为 Shark 初始化整手 HandPlan
        DpNpcEngine.initHandPlanIfNeededForPostflop(
                room,
                bot,
                type,
                st,
                bd,
                position,
                ctx,
                random);

        // turn/river：如果牌力明显升级，修复“前一街打了但后两街全 check”的过度保守
        DpNpcEngine.updateHandPlanForLaterStreetIfNeeded(
                room,
                bot,
                type,
                st,
                bd,
                ctx);

        DpNpcEngine.StackContext stackCtx = ctx.stackCtx;
        int activeVillains = ctx.activeVillains;
        DpNpcEngine.CounterStrategyProfile counter = ctx.counterStrategy;
        // 情报：recentHands 推出的 counter（与跟注站剧本互补——跟注站走 CALLING_STATION + bfs；bluffLess
        // 针对「进攻线很实」）
        final double countBluffScale = (counter != null && counter.bluffLess) ? 0.70 : 1.0;
        final boolean thinVsVillain = counter != null && counter.moreThinValue;

        dbg(room, type, bot, "CTX activeVillains=" + activeVillains
                + " aggressor=" + (ctx.aggressor != null ? ctx.aggressor.getNickname() : "-")
                + " aggBet=" + (ctx.aggressor != null ? ctx.aggressor.getBet() : 0)
                + " villainTier=" + ctx.villainTier
                + " credibility=" + ctx.credibility
                + " showdownBluffiness=" + String.format("%.3f", ctx.showdownBluffiness)
                + " potOdds=" + String.format("%.3f", ctx.potOdds)
                + " eq=" + String.format("%.3f", ctx.equityEst)
                + " boardDominant=" + boardDominant
                + " plan=" + DpNpcEngine.getHandPlanType(bot)
                + " barrels=" + bot.getNpcHandPlanMaxBarrels()
                + " planAgg=" + String.format("%.3f", bot.getNpcHandPlanAggression())
                + " countBluffScale=" + String.format("%.2f", countBluffScale)
                + " thinVsVillain=" + thinVsVillain);

        // 无人下注时 aggressor=null：主对手由 resolvePrimaryVillainForShark 按座位顺时针 +「下一位须表态者」选取，
        // 与 DpNpcEngine.initHandPlanIfNeededForPostflop 一致，避免只盯住列表里第一个座位。
        DpPlayer targetVillain = DpNpcEngine.resolvePrimaryVillainForShark(room, bot, ctx);

        int sharkTotalStack = bot.getBet() + bot.getChips();
        double sharkCommitFactor;
        if (st == SimpleStrength.MONSTER) {
            sharkCommitFactor = 0.96;
        } else if (st == SimpleStrength.STRONG) {
            sharkCommitFactor = 0.80;
        } else if (st == SimpleStrength.MEDIUM) {
            sharkCommitFactor = 0.58;
        } else {
            sharkCommitFactor = 0.34;
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
        if (sharkCommitFactor < 0.15)
            sharkCommitFactor = 0.15;
        if (sharkCommitFactor > 0.98)
            sharkCommitFactor = 0.98;
        final double sharkCommitThreshold = sharkTotalStack * sharkCommitFactor;

        double baseFold = 0.0;
        if (st == SimpleStrength.WEAK && callAmount > 0 && callRatio > 0.4) {
            baseFold = (bd == DpNpcEngine.BoardDanger.WET) ? 0.7 : 0.5;
        }
        if (boardDominant && callAmount > 0) {
            // 公共牌主导时，对大额压力更保守一些（更像真人：不要为“打公共牌”投入太多）
            if (callRatio >= 0.35) {
                baseFold = Math.min(1.0, baseFold + 0.10);
            }
        }

        DpPlayer aggressor = ctx.aggressor;
        DpPlayerStats aggressorStats = ctx.aggressorStats;
        DpPlayerStats.SingleHandStats lastAggressorStats = null;
        if (aggressorStats != null && !aggressorStats.getRecentHands().isEmpty()) {
            lastAggressorStats = aggressorStats.getRecentHands().peekLast();
        }
        DpNpcEngine.VillainRangeTier villainTier = ctx.villainTier;
        DpNpcEngine.ActionCredibility credibility = ctx.credibility;
        double showdownBluffiness = ctx.showdownBluffiness;
        String villainName = aggressor != null ? aggressor.getNickname() : "-";

        // Shark 学习实验：对该对手的“开枪/榨取”偏好（用于提升针对性与加注频率）
        DpNpcSharkLearningLab.LearnedAdjust learnedVsAggressor = null;
        if (targetVillain != null) {
            learnedVsAggressor = DpNpcSharkLearningLab.getAdjust(room, bot, targetVillain);
        }
        /** 学习得到的「诈唬总乘子」：对跟注站趋近下限，决策里统一用 */
        double bfs = learnedVsAggressor != null ? learnedVsAggressor.bluffFrequencyScale : 1.0;
        final double vrMult = DpNpcEngine.SharkConfig.LEARN_VALUE_RAISE_APPLY_MULT;
        if (learnedVsAggressor != null) {
            dbg(room, type, bot, "LEARNED vs=" + targetVillain.getNickname()
                    + " foldAdj=" + String.format("%.3f", learnedVsAggressor.foldAdjustment)
                    + " bcBoost=" + String.format("%.3f", learnedVsAggressor.bluffCatchBoost)
                    + " sz=" + String.format("%.3f", learnedVsAggressor.sizingFactor)
                    + " bfs=" + String.format("%.3f", bfs)
                    + " bf(f/t/r)=" + String.format("%.3f", learnedVsAggressor.bluffFireBoostFlop) + "/"
                    + String.format("%.3f", learnedVsAggressor.bluffFireBoostTurn) + "/"
                    + String.format("%.3f", learnedVsAggressor.bluffFireBoostRiver)
                    + " vr(f/t/r)=" + String.format("%.3f", learnedVsAggressor.valueRaiseBoostFlop) + "/"
                    + String.format("%.3f", learnedVsAggressor.valueRaiseBoostTurn) + "/"
                    + String.format("%.3f", learnedVsAggressor.valueRaiseBoostRiver)
                    + " bluffSize(f/t/r)=" + String.format("%.2f", learnedVsAggressor.bluffBetPotFactorFlop) + "/"
                    + String.format("%.2f", learnedVsAggressor.bluffBetPotFactorTurn) + "/"
                    + String.format("%.2f", learnedVsAggressor.bluffBetPotFactorRiver));
        } else {
            dbg(room, type, bot, "LEARNED aggressor=null (no per-villain adjust this decision)");
        }

        // 额外快照一条“综合心电图”，方便肉眼看每一街 Shark 的读牌与学习状态。
        dbg(room, type, bot, "SNAPSHOT stage=" + stage
                + " st=" + st
                + " eq=" + String.format("%.3f", ctx.equityEst)
                + " potOdds=" + String.format("%.3f", ctx.potOdds)
                + " villain=" + villainName
                + " tier=" + villainTier
                + " cred=" + credibility
                + " showdownBluff=" + String.format("%.3f", showdownBluffiness)
                + " foldAdjLearn="
                + (learnedVsAggressor != null ? String.format("%.3f", learnedVsAggressor.foldAdjustment) : "0.000")
                + " bfTurn="
                + (learnedVsAggressor != null ? String.format("%.3f", learnedVsAggressor.bluffFireBoostTurn) : "0.000")
                + " bfRiver="
                + (learnedVsAggressor != null ? String.format("%.3f", learnedVsAggressor.bluffFireBoostRiver) : "0.000")
                + " vrTurn="
                + (learnedVsAggressor != null ? String.format("%.3f", learnedVsAggressor.valueRaiseBoostTurn) : "0.000")
                + " vrRiver="
                + (learnedVsAggressor != null ? String.format("%.3f", learnedVsAggressor.valueRaiseBoostRiver)
                        : "0.000")
                + " bfs=" + String.format("%.3f", bfs));

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
                baseFold = Math.min(1.0, baseFold * 1.07);
            } else if (potOdds < 0.25) {
                baseFold = Math.max(0.0, baseFold * 0.76);
            }
        }

        // === Shark 学习实验（不改 SmartContext）：按“对手长期摊牌弱牌比例”微调弃牌倾向 ===
        // 直觉：如果某人经常用弱牌摊牌，说明其线里 bluff/薄价值更多 → Shark 更少弃牌（更敢 bluff-catch）。
        if (callAmount > 0 && aggressor != null) {
            DpNpcSharkLearningLab.LearnedAdjust learned = learnedVsAggressor != null
                    ? learnedVsAggressor
                    : DpNpcSharkLearningLab.getAdjust(room, bot, aggressor);
            double baseFoldBeforeLearn = baseFold;
            // 面对「几乎不弃牌」的对手：削弱「少弃牌/抓诈」类学习，避免拿弱牌跟到底
            double learnDamp = 0.22 + 0.78 * learned.bluffFrequencyScale;
            double adj = learned.foldAdjustment * learnDamp;
            double bluffCatchBoost = learned.bluffCatchBoost * learnDamp;
            if (callRatio >= 0.25) {
                adj -= bluffCatchBoost;
            }
            baseFold = Math.min(1.0, Math.max(0.0, baseFold + adj));
            if (learnedVsAggressor != null) {
                dbg(room, type, bot, "LEARNED_APPLY fold base=" + String.format("%.3f", baseFoldBeforeLearn)
                        + " +adj=" + String.format("%.3f", adj)
                        + " (foldAdj=" + String.format("%.3f", learned.foldAdjustment)
                        + " bcBoost=" + String.format("%.3f", bluffCatchBoost)
                        + " callRatio=" + String.format("%.3f", callRatio) + ")"
                        + " => " + String.format("%.3f", baseFold));
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
                baseFold = Math.min(1.0, Math.max(baseFold, 0.72));
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
            baseFold = Math.min(1.0, baseFold + 0.08);
        }

        double foldProbShark = Math.min(1.0, Math.max(0.0, baseFold + (-mood) * 0.1));
        foldProbShark = DpNpcEngine.applySoftNoise(foldProbShark, DpNpcEngine.SharkConfig.PROB_NOISE_DELTA, random);
        if (callAmount > 0 && foldProbShark > 0 && random.nextDouble() < foldProbShark) {
            dbg(room, type, bot, "DECIDE FOLD foldProb=" + String.format("%.3f", foldProbShark)
                    + " baseFold=" + String.format("%.3f", baseFold)
                    + " callAmount=" + callAmount
                    + " callRatio=" + String.format("%.3f", callRatio));
            return new DpNpcEngine.BotAction(DpNpcEngine.BotActionType.FOLD, 0);
        }

        double r = random.nextDouble();

        // ===== 面对下注压力时的“先决定要不要加注”入口 =====
        // 之前这里很容易被 callProb 提前吞掉，导致你观感上几乎看不到翻后加注。
        if (callAmount > 0 && chips > callAmount
                && ("flop".equals(stage) || "turn".equals(stage) || "river".equals(stage))) {
            DpNpcEngine.HandPlanType plan = DpNpcEngine.getHandPlanType(bot);
            boolean skipByPlan = DpNpcEngine.shouldSkipAggressiveActionByPlan(bot, stage);
            // value 加注不应被 GIVE_UP 轻易压死：强牌面对可榨取对象允许更积极
            boolean allowValueOverride = false;
            double valueRaiseBoost = 0.0;
            double bluffFire = 0.0;
            if (learnedVsAggressor != null) {
                if ("flop".equals(stage)) {
                    valueRaiseBoost = learnedVsAggressor.valueRaiseBoostFlop;
                    bluffFire = learnedVsAggressor.bluffFireBoostFlop;
                } else if ("turn".equals(stage)) {
                    valueRaiseBoost = learnedVsAggressor.valueRaiseBoostTurn;
                    bluffFire = learnedVsAggressor.bluffFireBoostTurn;
                } else {
                    valueRaiseBoost = learnedVsAggressor.valueRaiseBoostRiver;
                    bluffFire = learnedVsAggressor.bluffFireBoostRiver;
                }
            }
            if (DpNpcEngine.getHandPlanType(bot) == DpNpcEngine.HandPlanType.GIVE_UP
                    && (st == SimpleStrength.STRONG || st == SimpleStrength.MONSTER)
                    && valueRaiseBoost >= 0.10) {
                allowValueOverride = true;
            }

            // HandPlan 不应作为“硬门禁”掐死学习算法：
            // - 如果计划不允许进攻（barrels<=0 或 GIVE_UP），我们只是降低 raiseProb，而不是直接禁止（除非特别危险）。
            // - 真正需要硬禁的：多人 + 湿牌面 + 弱牌（否则容易乱送）。
            boolean hardBlock = (st == SimpleStrength.WEAK)
                    && activeVillains >= 3
                    && bd == DpNpcEngine.BoardDanger.WET;

            if (!hardBlock && (!skipByPlan || allowValueOverride)) {
                double raiseProb = 0.0;
                if (st == SimpleStrength.MONSTER) {
                    raiseProb = 0.78;
                } else if (st == SimpleStrength.STRONG) {
                    raiseProb = 0.50;
                } else if (st == SimpleStrength.MEDIUM) {
                    // 中牌更多是“对人/对尺度”的针对性反击
                    raiseProb = 0.16;
                } else {
                    raiseProb = 0.04;
                }

                // 读人：valueRaiseBoost 提升强牌加注；bluffFire 在 turn/river 也提升少量反击频率
                double norm = Math.max(0.10, DpNpcEngine.SharkConfig.LEARN_BOOST_NORM);
                if (st == SimpleStrength.MONSTER || st == SimpleStrength.STRONG) {
                    raiseProb += 0.22 * (valueRaiseBoost / norm) * vrMult;
                    raiseProb += 0.10 * (1.0 - bfs);
                } else if (st == SimpleStrength.MEDIUM) {
                    raiseProb += 0.10 * (bluffFire / norm) * bfs;
                } else {
                    // 弱牌只在可信度低 + 压力不大时才少量反加注（避免“瞎送”）
                    if (credibility == DpNpcEngine.ActionCredibility.LOW && callRatio <= 0.28) {
                        raiseProb += 0.08 * (bluffFire / norm) * bfs;
                    } else {
                        raiseProb *= 0.35;
                    }
                }
                if (st == SimpleStrength.WEAK || st == SimpleStrength.MEDIUM) {
                    raiseProb *= 0.30 + 0.70 * bfs;
                }
                // 情报：对「进攻摊牌很实」的对手压空气反击；limp 多略抬中强牌价值加注
                if (countBluffScale < 1.0) {
                    if (st == SimpleStrength.WEAK) {
                        raiseProb *= countBluffScale;
                    } else if (st == SimpleStrength.MEDIUM) {
                        raiseProb *= 0.50 + 0.50 * countBluffScale;
                    }
                }
                if (thinVsVillain) {
                    if (st == SimpleStrength.MEDIUM) {
                        raiseProb = Math.min(0.92, raiseProb + 0.04);
                    } else if (st == SimpleStrength.STRONG) {
                        raiseProb = Math.min(0.92, raiseProb + 0.05);
                    }
                }

                // 多人底池更少反击；湿牌面更少“随便抬”
                if (activeVillains >= 3)
                    raiseProb *= 0.75;
                if (bd == DpNpcEngine.BoardDanger.WET && st != SimpleStrength.MONSTER)
                    raiseProb *= 0.85;

                // 计划软约束：若计划“不该进攻”，把 raiseProb 压低（但不为 0，让算法还能试探/发挥）
                if (skipByPlan && !allowValueOverride) {
                    if (plan == DpNpcEngine.HandPlanType.GIVE_UP) {
                        raiseProb *= 0.35;
                    } else {
                        raiseProb *= 0.55;
                    }
                }

                // 心情与噪声
                raiseProb += mood * 0.03;
                if (raiseProb < 0.02)
                    raiseProb = 0.02;
                if (raiseProb > 0.92)
                    raiseProb = 0.92;
                raiseProb = DpNpcEngine.applySoftNoise(raiseProb, DpNpcEngine.SharkConfig.PROB_NOISE_DELTA, random);

                if (random.nextDouble() < raiseProb) {
                    dbg(room, type, bot, "DECIDE RAISE(pressure) raiseProb=" + String.format("%.3f", raiseProb)
                            + " callAmount=" + callAmount
                            + " st=" + st
                            + " cred=" + credibility
                            + " callRatio=" + String.format("%.3f", callRatio));
                    // 直接复用“callAmount>0 时的加注额计算”逻辑（避免被 callProb 吞掉）
                    int sb = room.getSmallBlindChips();
                    int bb = room.getBigBlindChips();

                    int strengthFactor;
                    if (st == SimpleStrength.STRONG || st == SimpleStrength.MONSTER) {
                        strengthFactor = 5;
                    } else if (st == SimpleStrength.MEDIUM) {
                        strengthFactor = 4;
                    } else {
                        strengthFactor = 3;
                    }
                    boolean isLateStreet = "river".equals(stage);
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
                    } else if (stackCtx != null
                            && stackCtx.minStackBB > DpNpcEngine.SharkConfig.DEEP_STACK_MIN_BB
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

                    int raiseAmount = Math.min(chips, target);
                    int heroInvestAfterRaise = bot.getBet() + raiseAmount;
                    if (heroInvestAfterRaise > sharkCommitThreshold) {
                        double y = random.nextDouble();
                        if (st == SimpleStrength.MONSTER || st == SimpleStrength.STRONG) {
                            if (y < 0.5) {
                                return new DpNpcEngine.BotAction(DpNpcEngine.BotActionType.ALL_IN, chips);
                            } else {
                                // 过深投入则降级为跟注
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

                    if (sb > 0 && raiseAmount > 0) {
                        int units = Math.max(1, Math.round(raiseAmount * 1.0f / sb));
                        raiseAmount = units * sb;
                        if (raiseAmount > chips) {
                            units = Math.max(1, chips / sb);
                            raiseAmount = units * sb;
                        }
                    }

                    // 学习到的 value/尺度微调仍然适用
                    if (aggressor != null) {
                        double sizing = DpNpcSharkLearningLab.getAdjust(room, bot, aggressor).sizingFactor;
                        int scaled = (int) Math.round(raiseAmount * sizing);
                        if (scaled < raiseAmount) {
                            int minExtra = room.getBigBlindChips() * 2;
                            int minRaise = callAmount + minExtra;
                            if (scaled < minRaise)
                                scaled = minRaise;
                        }
                        if (scaled > chips)
                            scaled = chips;
                        if (sb > 0 && scaled > 0) {
                            int units = Math.max(1, Math.round(scaled * 1.0f / sb));
                            scaled = units * sb;
                            if (scaled > chips) {
                                units = Math.max(1, chips / sb);
                                scaled = units * sb;
                            }
                        }
                        if (scaled > callAmount) {
                            raiseAmount = scaled;
                        }
                    }

                    DpNpcEngine.consumeOneBarrelIfAny(bot, stage);
                    dbg(room, type, bot, "ACTION RAISE(pressure) amount=" + raiseAmount
                            + " pot=" + room.getPot()
                            + " barrelsLeft=" + bot.getNpcHandPlanMaxBarrels());
                    return new DpNpcEngine.BotAction(DpNpcEngine.BotActionType.RAISE, raiseAmount);
                }
            }
        }

        // 翻后无人下注时：先单独决定“是否主动下注”（这是 Shark 的主要进攻入口）。
        // 之前用 callProb 早早返回 check/call，容易导致“翻后几乎不下注”的观感。
        if (callAmount == 0 && chips > 0 && ("flop".equals(stage) || "turn".equals(stage) || "river".equals(stage))) {
            DpNpcEngine.HandPlanType plan = DpNpcEngine.getHandPlanType(bot);
            boolean skipByPlan = DpNpcEngine.shouldSkipAggressiveActionByPlan(bot, stage);

            // 给 Shark 一个“对人开枪”的破例：即便 HandPlan 是 GIVE_UP，只要对手很适合被偷，也允许低频 stab。
            double bluffFire = 0.0;
            if (learnedVsAggressor != null) {
                bluffFire = "flop".equals(stage) ? learnedVsAggressor.bluffFireBoostFlop
                        : "turn".equals(stage) ? learnedVsAggressor.bluffFireBoostTurn
                                : learnedVsAggressor.bluffFireBoostRiver;
            }
            boolean allowOverride = (plan == DpNpcEngine.HandPlanType.GIVE_UP)
                    && (("flop".equals(stage) || "turn".equals(stage)))
                    && bluffFire >= 0.04
                    && bfs >= 0.42
                    && countBluffScale >= 0.99
                    && bd == DpNpcEngine.BoardDanger.DRY
                    && activeVillains <= 2;

            // HandPlan 改成软约束：即便 skipByPlan 也不直接禁止 open，而是显著降低 betProb。
            // 真正硬禁：多人湿牌面 + 弱牌（这种局面开枪代价高）
            boolean hardBlock = (st == SimpleStrength.WEAK)
                    && activeVillains >= 3
                    && bd == DpNpcEngine.BoardDanger.WET;

            if (!hardBlock) {
                // 基础下注频率：强牌更高；弱牌也给一定 stab 空间（尤其对 tight / 一枪流）
                double betProb;
                if (st == SimpleStrength.MONSTER)
                    betProb = 0.91;
                else if (st == SimpleStrength.STRONG)
                    betProb = 0.79;
                else if (st == SimpleStrength.MEDIUM)
                    betProb = 0.51;
                else
                    betProb = 0.17;

                // 牌面与人数：湿牌/多人减少无谓开枪
                if (bd == DpNpcEngine.BoardDanger.WET)
                    betProb *= 0.85;
                if (activeVillains >= 3)
                    betProb *= 0.70;

                // 位置：晚位更敢
                if (position == DpNpcEngine.TablePosition.LATE)
                    betProb += 0.06;
                if (position == DpNpcEngine.TablePosition.EARLY)
                    betProb -= 0.04;

                // HandPlan aggression（之前一直没被真正用起来）
                double planAgg = bot.getNpcHandPlanAggression();
                betProb += (planAgg - 0.45) * 0.18; // 约 -0.08 ~ +0.10

                double norm = Math.max(0.10, DpNpcEngine.SharkConfig.LEARN_BOOST_NORM);
                betProb += 0.18 * (bluffFire / norm) * bfs;

                if (st == SimpleStrength.WEAK) {
                    betProb *= 0.25 + 0.75 * bfs;
                } else if (st == SimpleStrength.MEDIUM) {
                    betProb *= 0.40 + 0.60 * bfs;
                } else if (st == SimpleStrength.STRONG || st == SimpleStrength.MONSTER) {
                    betProb += 0.07 * (1.0 - bfs);
                }

                // 情报 counter.bluffLess：压弱/中空气开枪；moreThinValue：对爱 limp 的对手略增薄价值
                if (countBluffScale < 1.0) {
                    if (st == SimpleStrength.WEAK) {
                        betProb *= countBluffScale;
                    } else if (st == SimpleStrength.MEDIUM) {
                        betProb *= 0.55 + 0.45 * countBluffScale;
                    }
                }
                if (thinVsVillain) {
                    if (st == SimpleStrength.MEDIUM) {
                        betProb = Math.min(0.92, betProb + 0.07);
                    } else if (st == SimpleStrength.STRONG || st == SimpleStrength.MONSTER) {
                        betProb = Math.min(0.92, betProb + 0.05);
                    }
                }

                // 心情：轻微影响
                betProb += mood * 0.03;

                // 计划软约束：若本街计划“不该进攻”，压低 betProb（除非 allowOverride）。
                // 对 POT_CONTROL 稍微放松，让学习旋钮在 turn/river 还能发挥“第二/第三枪”的作用。
                if (skipByPlan && !allowOverride) {
                    if (plan == DpNpcEngine.HandPlanType.GIVE_UP) {
                        betProb *= 0.60;
                    } else {
                        betProb *= 0.80;
                    }
                }

                if (betProb < 0.05)
                    betProb = 0.05;
                if (betProb > 0.92)
                    betProb = 0.92;
                betProb = DpNpcEngine.applySoftNoise(betProb, DpNpcEngine.SharkConfig.PROB_NOISE_DELTA, random);

                if (random.nextDouble() < betProb) {
                    dbg(room, type, bot, "DECIDE OPEN betProb=" + String.format("%.3f", betProb)
                            + " st=" + st
                            + " plan=" + plan
                            + " allowOverride=" + allowOverride
                            + " bluffFire=" + String.format("%.3f", bluffFire)
                            + " pot=" + room.getPot());
                    int sb = room.getSmallBlindChips();
                    int pot = room.getPot();
                    double baseFactor;
                    if (st == SimpleStrength.STRONG || st == SimpleStrength.MONSTER) {
                        baseFactor = DpNpcEngine.SharkConfig.CBET_BASE_STRONG;
                    } else if (st == SimpleStrength.MEDIUM) {
                        baseFactor = DpNpcEngine.SharkConfig.CBET_BASE_MEDIUM;
                    } else {
                        baseFactor = DpNpcEngine.SharkConfig.CBET_BASE_WEAK;
                    }

                    double rMin = DpNpcEngine.SharkConfig.CBET_RANDOM_MIN;
                    double rMax = DpNpcEngine.SharkConfig.CBET_RANDOM_MAX;
                    double randomFactor = rMin + random.nextDouble() * (rMax - rMin);
                    double sizeFactor = baseFactor * randomFactor;

                    // 诈唬/偷的尺度：用分桶探索结果来“摸边界”
                    if (learnedVsAggressor != null && aggressor != null
                            && (st == SimpleStrength.WEAK || st == SimpleStrength.MEDIUM)) {
                        norm = Math.max(0.10, DpNpcEngine.SharkConfig.LEARN_BOOST_NORM);
                        double eps = 0.05 + 0.10 * (bluffFire / norm);
                        if (eps < 0.03)
                            eps = 0.03;
                        if (eps > 0.20)
                            eps = 0.20;
                        if (difficulty == DpNpcEngine.NpcDifficulty.PRO)
                            eps *= 1.00;
                        else if (difficulty == DpNpcEngine.NpcDifficulty.HARD)
                            eps *= 0.90;
                        else if (difficulty == DpNpcEngine.NpcDifficulty.MEDIUM)
                            eps *= 0.80;
                        else
                            eps *= 0.70;

                        double fallback = "flop".equals(stage) ? 0.45 : ("turn".equals(stage) ? 0.55 : 0.55);
                        double pref = DpNpcSharkLearningLab.pickBluffBetPotFactorWithExplore(room, aggressor, stage,
                                random, fallback, eps);
                        double norm2 = Math.max(0.10, DpNpcEngine.SharkConfig.LEARN_BOOST_NORM);
                        double alpha = 0.50 * (bluffFire / norm2) * countBluffScale; // 0~0.50，对大注实牌对手收敛探索尺度
                        if (alpha < 0)
                            alpha = 0;
                        if (alpha > 0.50)
                            alpha = 0.50;
                        sizeFactor = sizeFactor * (1.0 - alpha) + pref * alpha;
                    }

                    int target = (int) Math.round(Math.max(1, pot) * sizeFactor);
                    int bb = room.getBigBlindChips();
                    int minBet = bb * 2;
                    if (target < minBet)
                        target = minBet;
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
                    dbg(room, type, bot, "ACTION OPEN(RAISE) amount=" + raiseAmount
                            + " sizeFactor=" + String.format("%.3f", sizeFactor)
                            + " pot=" + pot
                            + " barrelsLeft=" + bot.getNpcHandPlanMaxBarrels());
                    return new DpNpcEngine.BotAction(DpNpcEngine.BotActionType.RAISE, raiseAmount);
                }
                dbg(room, type, bot, "SKIP OPEN betProb=" + String.format("%.3f", betProb)
                        + " skipByPlan=" + skipByPlan
                        + " allowOverride=" + allowOverride
                        + " plan=" + plan
                        + " st=" + st);
            }
        }

        boolean isLateStreet = "river".equals(stage);
        double callProb = 0.58 + mood * 0.04;
        if (st == SimpleStrength.STRONG) {
            callProb -= 0.25;
            if (!isLateStreet) {
                callProb += 0.15;
            }
        } else if (st == SimpleStrength.MEDIUM) {
            callProb += 0.05;
        }

        if (st == SimpleStrength.WEAK && callAmount > 0) {
            int bbForBluff = room.getBigBlindChips();
            int villainStack = aggressor != null ? aggressor.getChips() : 0;
            double villainBB = bbForBluff > 0 ? villainStack * 1.0 / bbForBluff : 0.0;
            if (villainBB > DpNpcEngine.SharkConfig.DEEP_STACK_MIN_BB
                    && stackCtx.avgStackBB > DpNpcEngine.SharkConfig.DEEP_TABLE_AVG_BB) {
                callProb += 0.10;
            }
            if (villainBB > 0 && villainBB <= DpNpcEngine.SharkConfig.SHORT_STACK_BB) {
                callProb += 0.05;
            }
        }

        if (position == DpNpcEngine.TablePosition.EARLY && bd == DpNpcEngine.BoardDanger.WET
                && st == SimpleStrength.WEAK) {
            if (callAmount > 0) {
                callProb -= 0.08;
            }
        }

        // ===== 针对性：对特定对手提高“开枪/榨取”的概率（按街拆分）=====
        // 目标：修复“Shark 太爱 check/call，看起来不敢加注”的观感；同时让行为更像在针对人。
        if (learnedVsAggressor != null) {
            double bluffFire;
            double valueRaise;
            if ("flop".equals(stage)) {
                bluffFire = learnedVsAggressor.bluffFireBoostFlop;
                valueRaise = learnedVsAggressor.valueRaiseBoostFlop;
            } else if ("turn".equals(stage)) {
                bluffFire = learnedVsAggressor.bluffFireBoostTurn;
                valueRaise = learnedVsAggressor.valueRaiseBoostTurn;
            } else {
                // river 或其它：默认按 river
                bluffFire = learnedVsAggressor.bluffFireBoostRiver;
                valueRaise = learnedVsAggressor.valueRaiseBoostRiver;
            }

            // 1) 无需付费（callAmount==0）时，弱/中牌更容易主动开枪（尤其 flop/turn）
            if (callAmount == 0 && ("flop".equals(stage) || "turn".equals(stage))
                    && (st == SimpleStrength.WEAK || st == SimpleStrength.MEDIUM)) {
                // bluffFire 越高，越减少“直接过牌/跟注”的概率；bfs 低时（跟注站）不再硬推去下注分支
                double norm = Math.max(0.10, DpNpcEngine.SharkConfig.LEARN_BOOST_NORM);
                double shrink = 0.20 * (bluffFire / norm) * bfs * countBluffScale;
                callProb -= shrink;
            }

            // 2) 需要付费（callAmount>0）时：强牌更倾向 value raise，而不是仅 call
            if (callAmount > 0 && (st == SimpleStrength.STRONG || st == SimpleStrength.MONSTER)) {
                double norm = Math.max(0.10, DpNpcEngine.SharkConfig.LEARN_BOOST_NORM);
                double shrink = 0.18 * (valueRaise / norm) * vrMult;
                // 潮湿牌面仍保守一些，避免无脑加注把自己抬到 commit 区间
                if (bd == DpNpcEngine.BoardDanger.WET) {
                    shrink *= 0.75;
                }
                callProb -= shrink;
            }

            // 3) 站桩对手（valueRaiseBoost 高往往来自松被动）：弱牌减少 bluff（多 check back）
            if (callAmount == 0 && ("flop".equals(stage) || "turn".equals(stage))
                    && st == SimpleStrength.WEAK
                    && valueRaise >= 0.15) {
                callProb += 0.06;
            }
        }

        if (callProb < 0.08)
            callProb = 0.08;
        if (callProb > 0.92)
            callProb = 0.92;
        callProb = DpNpcEngine.applySoftNoise(callProb, DpNpcEngine.SharkConfig.PROB_NOISE_DELTA, random);
        if (r < Math.max(0.1, Math.min(0.9, callProb))) {
            dbg(room, type, bot, "DECIDE CALL_OR_CHECK callProb=" + String.format("%.3f", callProb)
                    + " callAmount=" + callAmount
                    + " st=" + st);
            return new DpNpcEngine.BotAction(DpNpcEngine.BotActionType.CALL_OR_CHECK, 0);
        }

        if (chips > callAmount) {
            if (DpNpcEngine.shouldSkipAggressiveActionByPlan(bot, stage)) {
                // HandPlan 已用尽进攻额度或为 GIVE_UP：不再主动加注，只考虑跟注/过牌
                return new DpNpcEngine.BotAction(DpNpcEngine.BotActionType.CALL_OR_CHECK, 0);
            }
            int sb = room.getSmallBlindChips();
            int bb = room.getBigBlindChips();
            int raiseAmount;
            if (callAmount == 0) {
                int pot = room.getPot();
                double baseFactor;
                if (st == SimpleStrength.STRONG || st == SimpleStrength.MONSTER) {
                    baseFactor = DpNpcEngine.SharkConfig.CBET_BASE_STRONG;
                } else if (st == SimpleStrength.MEDIUM) {
                    baseFactor = DpNpcEngine.SharkConfig.CBET_BASE_MEDIUM;
                } else {
                    baseFactor = DpNpcEngine.SharkConfig.CBET_BASE_WEAK;
                }
                double rMin = DpNpcEngine.SharkConfig.CBET_RANDOM_MIN;
                double rMax = DpNpcEngine.SharkConfig.CBET_RANDOM_MAX;
                double randomFactor = rMin + random.nextDouble() * (rMax - rMin);
                double factor = baseFactor * randomFactor;

                // 学习到的“更怕哪个尺度”主要用于 bluff/steal（weak/medium），强牌仍以价值尺度为主
                if (learnedVsAggressor != null && aggressor != null
                        && (st == SimpleStrength.WEAK || st == SimpleStrength.MEDIUM)
                        && ("flop".equals(stage) || "turn".equals(stage) || "river".equals(stage))) {
                    double bluffFire = "flop".equals(stage) ? learnedVsAggressor.bluffFireBoostFlop
                            : "turn".equals(stage) ? learnedVsAggressor.bluffFireBoostTurn
                                    : learnedVsAggressor.bluffFireBoostRiver;
                    // alpha 越高，越像“有意识试探这个人的边界”
                    double norm = Math.max(0.10, DpNpcEngine.SharkConfig.LEARN_BOOST_NORM);
                    double alpha = 0.45 * (bluffFire / norm); // 0~0.45
                    if (alpha < 0)
                        alpha = 0;
                    if (alpha > 0.45)
                        alpha = 0.45;

                    double norm2 = Math.max(0.10, DpNpcEngine.SharkConfig.LEARN_BOOST_NORM);
                    double eps = 0.05 + 0.10 * (bluffFire / norm2); // 约 0.05~0.15
                    if (eps < 0.03)
                        eps = 0.03;
                    if (eps > 0.20)
                        eps = 0.20;
                    if (difficulty == DpNpcEngine.NpcDifficulty.PRO) {
                        eps *= 1.00;
                    } else if (difficulty == DpNpcEngine.NpcDifficulty.HARD) {
                        eps *= 0.90;
                    } else if (difficulty == DpNpcEngine.NpcDifficulty.MEDIUM) {
                        eps *= 0.80;
                    } else {
                        eps *= 0.70;
                    }
                    double fallback = "flop".equals(stage) ? 0.45 : ("turn".equals(stage) ? 0.55 : 0.55);
                    double pref = DpNpcSharkLearningLab.pickBluffBetPotFactorWithExplore(
                            room, aggressor, stage, random, fallback, eps);
                    factor = factor * (1.0 - alpha) + pref * alpha;
                }

                int target = (int) Math.round(pot * factor);

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

            // 旋钮：对特定对手的下注尺度微调（只在有主要进攻者时应用）
            if (aggressor != null) {
                double sizing = DpNpcSharkLearningLab.getAdjust(room, bot, aggressor).sizingFactor;
                int scaled = (int) Math.round(raiseAmount * sizing);
                if (scaled < raiseAmount && callAmount > 0) {
                    // 有成本时不把加注缩到不像加注：至少比跟注多 2BB
                    int minExtra = room.getBigBlindChips() * 2;
                    int minRaise = callAmount + minExtra;
                    if (scaled < minRaise)
                        scaled = minRaise;
                }
                if (scaled > chips)
                    scaled = chips;
                if (sb > 0 && scaled > 0) {
                    int units = Math.max(1, Math.round(scaled * 1.0f / sb));
                    scaled = units * sb;
                    if (scaled > chips) {
                        units = Math.max(1, chips / sb);
                        scaled = units * sb;
                    }
                }
                if (scaled > callAmount) {
                    raiseAmount = scaled;
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
            int sb = room.getSmallBlindChips();
            int pot = room.getPot();
            double factor = DpNpcEngine.SharkConfig.RIVER_BLOCK_FACTOR;
            int target = (int) Math.round(pot * factor);
            int bb = room.getBigBlindChips();
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
