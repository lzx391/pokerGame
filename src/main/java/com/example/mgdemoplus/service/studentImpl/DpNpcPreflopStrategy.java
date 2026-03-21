package com.example.mgdemoplus.service.studentImpl;

import com.example.mgdemoplus.entity.DpPlayer;
import com.example.mgdemoplus.entity.DpRoom;
import com.example.mgdemoplus.entity.PlayerStats;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * 可复用的翻前策略模块（先给 SHARK 接入，后续可给其它 NPC 配置复用）。
 *
 * <p>设计思路（两层）：</p>
 * <ul>
 *   <li>底层：13×13 手牌“分组”（HandGroup，1 最强，数字越大越弱）</li>
 *   <li>上层：把人数/位置/码量/松紧/情绪等因素算成 rangeLevel（1~8），映射成可玩的 HandGroup 阈值</li>
 * </ul>
 */
final class DpNpcPreflopStrategy {
    private DpNpcPreflopStrategy() {
    }

    private static final Map<String, Integer> RANK = new HashMap<>();

    static {
        RANK.put("2", 2);
        RANK.put("3", 3);
        RANK.put("4", 4);
        RANK.put("5", 5);
        RANK.put("6", 6);
        RANK.put("7", 7);
        RANK.put("8", 8);
        RANK.put("9", 9);
        RANK.put("10", 10);
        RANK.put("J", 11);
        RANK.put("Q", 12);
        RANK.put("K", 13);
        RANK.put("A", 14);
    }

    enum HandGroup {
        G1,  // AA/KK/QQ/JJ/AK
        G2,  // TT/AQ/AJ/KQ（偏强）
        G3,  // 99-88/AT/KJ/QJ（偏可打）
        G4,  // 77-66/A9s-A5s/KT/QT/JT/部分同花大牌
        G5,  // 55-22/同花连张/同花一隔
        G6,  // 宽松的同花 Axs/同花 Kxs/部分 broadway offsuit
        G7,  // 部分边缘可偷牌（主要用于后位/短人局）
        G8,  // 其它（基本不建议入池）
    }

    enum PreflopSpot {
        UNOPENED,        // 还没人加注（callAmount==0 且 raiseLevel==0/1）
        FACING_OPEN,     // 面对 open（raiseLevel==1）
        FACING_3BET,     // 你 open 后被 3bet（raiseLevel==2）
        FACING_4BET,     // 面对 4bet+（raiseLevel>=3）
        UNKNOWN
    }

    static DpNpcEngine.BotAction decideForShark(
            DpRoom room,
            DpPlayer hero,
            DpNpcEngine.TablePosition position,
            int callAmount,
            double callRatio,
            double preflopTightness,
            double callStation,
            double mood,
            Random random
    ) {
        if (room == null || hero == null || random == null) {
            return null;
        }
        if (!"preflop".equals(room.getCurrentStage())) {
            return null;
        }
        int bb = DpRoom.getBBChips();
        int sb = DpRoom.getSBChips();
        if (bb <= 0) {
            return null;
        }

        HoleInfo hole = parseHole(hero.getHoleCards());
        if (!hole.valid) {
            return new DpNpcEngine.BotAction(DpNpcEngine.BotActionType.CALL_OR_CHECK, 0);
        }

        int activePlayers = countActivePlayers(room);
        DpPlayer aggressor = findAggressor(room, hero);
        PlayerStats aggressorStats = aggressor != null && room.getPlayerStatsMap() != null
                ? room.getPlayerStatsMap().get(aggressor.getNickname())
                : null;
        VillainTier tier = estimateVillainTier(aggressorStats);

        double heroStackBB = hero.getChips() * 1.0 / bb;
        double effStackBB = heroStackBB;
        if (aggressor != null) {
            effStackBB = Math.min(hero.getChips(), aggressor.getChips()) * 1.0 / bb;
        }

        int raiseLevel = room.getRaiseLevel();
        PreflopSpot spot = spotBy(raiseLevel, callAmount);

        int rangeLevel = computeRangeLevel(activePlayers, position, effStackBB, preflopTightness, callStation, mood);
        RangeThreshold th = thresholdsFor(position, rangeLevel);

        HandGroup g = groupOf(hole);

        // === 终局规则：到 4bet 往后收敛到 all-in / call / fold ===
        if (spot == PreflopSpot.FACING_4BET) {
            return decideFacing4Bet(hero, bb, sb, callAmount, callRatio, effStackBB, g, tier, mood, random);
        }

        // === 无人加注：open / limp-check / fold ===
        if (spot == PreflopSpot.UNOPENED) {
            return decideUnopened(hero, bb, sb, position, activePlayers, effStackBB, g, th, mood, random);
        }

        // === 面对 open：call / 3bet / fold ===
        if (spot == PreflopSpot.FACING_OPEN) {
            return decideFacingOpen(room, hero, bb, sb, position, callAmount, effStackBB, g, th, tier, mood, random);
        }

        // === 你 open 后被 3bet：call / 4bet / fold ===
        if (spot == PreflopSpot.FACING_3BET) {
            return decideFacing3Bet(room, hero, bb, sb, position, callAmount, callRatio, effStackBB, g, th, tier, mood, random);
        }

        // fallback：默认跟/过牌（尽量不断线）
        return new DpNpcEngine.BotAction(DpNpcEngine.BotActionType.CALL_OR_CHECK, 0);
    }

    private static DpNpcEngine.BotAction decideUnopened(
            DpPlayer hero,
            int bb,
            int sb,
            DpNpcEngine.TablePosition position,
            int activePlayers,
            double effStackBB,
            HandGroup g,
            RangeThreshold th,
            double mood,
            Random random
    ) {
        boolean canOpen = isGroupAtMost(g, th.openMaxGroup);
        if (!canOpen) {
            // 盲注位：很多时候可以直接过牌免费看翻牌
            return new DpNpcEngine.BotAction(DpNpcEngine.BotActionType.CALL_OR_CHECK, 0);
        }

        int openSizeBB = baseOpenSizeBB(position, activePlayers, effStackBB, mood, random);
        int raiseAmount = Math.min(hero.getChips(), openSizeBB * bb);
        raiseAmount = roundToSB(raiseAmount, sb, hero.getChips());
        if (raiseAmount <= 0) {
            return new DpNpcEngine.BotAction(DpNpcEngine.BotActionType.CALL_OR_CHECK, 0);
        }
        return new DpNpcEngine.BotAction(DpNpcEngine.BotActionType.RAISE, raiseAmount);
    }

    private static DpNpcEngine.BotAction decideFacingOpen(
            DpRoom room,
            DpPlayer hero,
            int bb,
            int sb,
            DpNpcEngine.TablePosition position,
            int callAmount,
            double effStackBB,
            HandGroup g,
            RangeThreshold th,
            VillainTier tier,
            double mood,
            Random random
    ) {
        boolean canContinue = isGroupAtMost(g, th.vsOpenContinueMaxGroup);
        if (!canContinue) {
            return new DpNpcEngine.BotAction(DpNpcEngine.BotActionType.FOLD, 0);
        }

        boolean can3BetValue = isGroupAtMost(g, th.vsOpen3BetValueMaxGroup);
        boolean can3BetBluff = is3BetBluffCandidate(g) && tier == VillainTier.LOOSE_OR_AGGRO;

        double base3betProb = can3BetValue ? 0.62 : (can3BetBluff ? 0.16 : 0.0);
        if (position == DpNpcEngine.TablePosition.BLINDS) base3betProb += 0.06;
        base3betProb += mood * 0.04;
        base3betProb = clamp01(base3betProb);

        if (base3betProb > 0 && hero.getChips() > callAmount && random.nextDouble() < base3betProb) {
            int villainBetToCall = room.getCurrentBetToCall();
            int raiseAmount = compute3BetAmount(hero, villainBetToCall, callAmount, bb, sb, position, effStackBB, random);
            if (raiseAmount > callAmount) {
                return new DpNpcEngine.BotAction(DpNpcEngine.BotActionType.RAISE, raiseAmount);
            }
        }

        return new DpNpcEngine.BotAction(DpNpcEngine.BotActionType.CALL_OR_CHECK, 0);
    }

    private static DpNpcEngine.BotAction decideFacing3Bet(
            DpRoom room,
            DpPlayer hero,
            int bb,
            int sb,
            DpNpcEngine.TablePosition position,
            int callAmount,
            double callRatio,
            double effStackBB,
            HandGroup g,
            RangeThreshold th,
            VillainTier tier,
            double mood,
            Random random
    ) {
        // continue range：比面对 open 更紧
        boolean canContinue = isGroupAtMost(g, th.vs3BetContinueMaxGroup);
        if (!canContinue) {
            return new DpNpcEngine.BotAction(DpNpcEngine.BotActionType.FOLD, 0);
        }

        // 价值 4bet：顶级强牌
        boolean value4bet = isGroupAtMost(g, th.vs3Bet4BetValueMaxGroup);
        boolean bluff4bet = is4BetBluffCandidate(g) && tier == VillainTier.LOOSE_OR_AGGRO && effStackBB >= 18;

        // 面对较大 3bet（成本高）时减少花活
        if (callRatio >= 0.35 && !value4bet) {
            // 深码可偶尔平跟看翻牌，否则多弃
            if (effStackBB >= 35 && random.nextDouble() < 0.22) {
                return new DpNpcEngine.BotAction(DpNpcEngine.BotActionType.CALL_OR_CHECK, 0);
            }
            double foldP = 0.55 + 0.25 * Math.min(1.0, callRatio);
            foldP = clamp01(foldP - mood * 0.05);
            if (random.nextDouble() < foldP) {
                return new DpNpcEngine.BotAction(DpNpcEngine.BotActionType.FOLD, 0);
            }
            return new DpNpcEngine.BotAction(DpNpcEngine.BotActionType.CALL_OR_CHECK, 0);
        }

        double fourBetProb = value4bet ? 0.72 : (bluff4bet ? 0.14 : 0.0);
        fourBetProb += mood * 0.04;
        if (position == DpNpcEngine.TablePosition.BLINDS) fourBetProb += 0.04;
        fourBetProb = clamp01(fourBetProb);

        if (fourBetProb > 0 && hero.getChips() > callAmount && random.nextDouble() < fourBetProb) {
            int villainBetToCall = room.getCurrentBetToCall();
            int raiseAmount = compute4BetAmount(hero, villainBetToCall, callAmount, bb, sb, position, effStackBB, random);
            if (raiseAmount > callAmount) {
                // 短码/有效筹码很浅：价值牌更倾向直接打光
                if (value4bet && effStackBB <= 16 && random.nextDouble() < 0.65) {
                    return new DpNpcEngine.BotAction(DpNpcEngine.BotActionType.ALL_IN, hero.getChips());
                }
                return new DpNpcEngine.BotAction(DpNpcEngine.BotActionType.RAISE, raiseAmount);
            }
        }

        return new DpNpcEngine.BotAction(DpNpcEngine.BotActionType.CALL_OR_CHECK, 0);
    }

    private static DpNpcEngine.BotAction decideFacing4Bet(
            DpPlayer hero,
            int bb,
            int sb,
            int callAmount,
            double callRatio,
            double effStackBB,
            HandGroup g,
            VillainTier tier,
            double mood,
            Random random
    ) {
        // 便宜跟注保护：底池很大而跟注很便宜时，避免被小幅 5bet 反复剥削
        double potOdds;
        int denom = hero.getBet() + hero.getChips() + callAmount; // 粗略：用 hero 总量做分母的保守估计
        if (callAmount <= 0 || denom <= 0) potOdds = 1.0;
        else potOdds = callAmount * 1.0 / denom;
        if (callAmount > 0 && potOdds <= 0.18) {
            if (isGroupAtMost(g, HandGroup.G4)) {
                return new DpNpcEngine.BotAction(DpNpcEngine.BotActionType.CALL_OR_CHECK, 0);
            }
            if (random.nextDouble() < 0.40) {
                return new DpNpcEngine.BotAction(DpNpcEngine.BotActionType.CALL_OR_CHECK, 0);
            }
            return new DpNpcEngine.BotAction(DpNpcEngine.BotActionType.FOLD, 0);
        }

        boolean jamValue = isGroupAtMost(g, HandGroup.G2);
        if (jamValue) {
            // 对松凶玩家稍微更愿意打光；对紧玩家给一点平跟空间（更像真人）
            double jamProb = (tier == VillainTier.TIGHT_OR_NIT) ? 0.70 : 0.82;
            jamProb += mood * 0.05;
            if (effStackBB >= 45) jamProb -= 0.12;
            jamProb = clamp01(jamProb);
            if (random.nextDouble() < jamProb) {
                return new DpNpcEngine.BotAction(DpNpcEngine.BotActionType.ALL_IN, hero.getChips());
            }
            return new DpNpcEngine.BotAction(DpNpcEngine.BotActionType.CALL_OR_CHECK, 0);
        }

        // 中强牌：更多平跟/弃牌
        if (isGroupAtMost(g, HandGroup.G3)) {
            double foldP = 0.35 + 0.25 * Math.min(1.0, callRatio);
            if (tier == VillainTier.TIGHT_OR_NIT) foldP += 0.10;
            foldP = clamp01(foldP - mood * 0.04);
            if (random.nextDouble() < foldP) {
                return new DpNpcEngine.BotAction(DpNpcEngine.BotActionType.FOLD, 0);
            }
            return new DpNpcEngine.BotAction(DpNpcEngine.BotActionType.CALL_OR_CHECK, 0);
        }

        return new DpNpcEngine.BotAction(DpNpcEngine.BotActionType.FOLD, 0);
    }

    private static int compute3BetAmount(
            DpPlayer hero,
            int villainBetToCall,
            int callAmount,
            int bb,
            int sb,
            DpNpcEngine.TablePosition position,
            double effStackBB,
            Random random
    ) {
        if (villainBetToCall < bb) villainBetToCall = bb;
        boolean oop = (position == DpNpcEngine.TablePosition.BLINDS);
        double mult = oop ? 4.0 : 3.0;
        // 深码更偏向稍小，避免过早把锅抬太大；浅码更偏向更大制造弃牌率
        if (effStackBB >= 50) mult -= 0.2;
        if (effStackBB <= 18) mult += 0.3;
        mult += (random.nextDouble() * 0.4 - 0.2);
        int desiredTotalBet = (int) Math.round(villainBetToCall * mult);
        int minTotalBet = villainBetToCall + 3 * bb;
        if (desiredTotalBet < minTotalBet) desiredTotalBet = minTotalBet;
        int raiseAmount = desiredTotalBet - hero.getBet();
        if (raiseAmount < 0) raiseAmount = 0;
        if (raiseAmount > hero.getChips()) raiseAmount = hero.getChips();
        if (raiseAmount <= callAmount && hero.getChips() > callAmount) {
            raiseAmount = Math.min(hero.getChips(), callAmount + 2 * bb);
        }
        return roundToSB(raiseAmount, sb, hero.getChips());
    }

    private static int compute4BetAmount(
            DpPlayer hero,
            int villainBetToCall,
            int callAmount,
            int bb,
            int sb,
            DpNpcEngine.TablePosition position,
            double effStackBB,
            Random random
    ) {
        if (villainBetToCall < bb) villainBetToCall = bb;
        boolean oop = (position == DpNpcEngine.TablePosition.BLINDS);
        double mult = 2.2 + random.nextDouble() * 0.4;
        if (oop) mult += 0.1;
        if (effStackBB <= 20) mult += 0.1;
        int desiredTotalBet = (int) Math.round(villainBetToCall * mult);
        int minTotalBet = villainBetToCall + 4 * bb;
        if (desiredTotalBet < minTotalBet) desiredTotalBet = minTotalBet;
        int raiseAmount = desiredTotalBet - hero.getBet();
        if (raiseAmount < 0) raiseAmount = 0;
        if (raiseAmount > hero.getChips()) raiseAmount = hero.getChips();
        if (raiseAmount <= callAmount && hero.getChips() > callAmount) {
            raiseAmount = Math.min(hero.getChips(), callAmount + 2 * bb);
        }
        return roundToSB(raiseAmount, sb, hero.getChips());
    }

    private static int baseOpenSizeBB(
            DpNpcEngine.TablePosition position,
            int activePlayers,
            double effStackBB,
            double mood,
            Random random
    ) {
        // 简化：用位置 + 人数 + 码深做近似，先把“像人”拉起来
        int base;
        if (position == DpNpcEngine.TablePosition.EARLY) base = 3;
        else if (position == DpNpcEngine.TablePosition.MIDDLE) base = 3;
        else if (position == DpNpcEngine.TablePosition.LATE) base = 2;
        else base = 3; // blinds

        if (activePlayers <= 3) base = Math.max(2, base - 1);
        if (effStackBB <= 18) base = Math.max(2, base - 1);
        if (effStackBB >= 60 && position == DpNpcEngine.TablePosition.EARLY) base = Math.min(4, base + 1);

        double jitter = (random.nextDouble() * 0.4 - 0.2) + mood * 0.10;
        if (jitter > 0.20) base += 1;
        if (jitter < -0.20) base = Math.max(2, base - 1);
        return clampInt(base, 2, 4);
    }

    private static int roundToSB(int amount, int sb, int max) {
        if (amount <= 0) return 0;
        if (sb <= 0) return Math.min(amount, max);
        int units = Math.max(1, Math.round(amount * 1.0f / sb));
        int v = units * sb;
        if (v > max) {
            units = Math.max(1, max / sb);
            v = units * sb;
        }
        return v;
    }

    private static PreflopSpot spotBy(int raiseLevel, int callAmount) {
        if (raiseLevel >= 3) return PreflopSpot.FACING_4BET;
        if (raiseLevel == 2) return PreflopSpot.FACING_3BET;
        if (raiseLevel == 1 && callAmount > 0) return PreflopSpot.FACING_OPEN;
        if (callAmount == 0) return PreflopSpot.UNOPENED;
        return PreflopSpot.UNKNOWN;
    }

    private static int computeRangeLevel(
            int activePlayers,
            DpNpcEngine.TablePosition position,
            double effStackBB,
            double preflopTightness,
            double callStation,
            double mood
    ) {
        // 1~8：数字越大越松
        int base;
        if (position == DpNpcEngine.TablePosition.EARLY) base = 3;
        else if (position == DpNpcEngine.TablePosition.MIDDLE) base = 4;
        else if (position == DpNpcEngine.TablePosition.LATE) base = 5;
        else base = 4;

        // 人数：人少范围变大
        if (activePlayers <= 3) base += 2;
        else if (activePlayers <= 5) base += 1;

        // 有效码深：深码更愿意带入“可玩牌形状”
        if (effStackBB >= 50) base += 1;
        if (effStackBB <= 16) base -= 1;

        // 风格：preflopTightness 越大越紧。
        // 注意：原先用 *2.0 且 vsOpen 再用 rangeLevel-2，配合 Shark/TAG 的 preflopTightness≈0.85
        // 会把「面对 open」压成几乎只剩 G1（JJ+/AK），观感像「有人加就弃」。
        base -= (int) Math.round(preflopTightness * 1.0); // 0~1 -> 约 0~1，避免过度扣档
        base += (int) Math.round(callStation * 1.0);      // 0~1 -> 0~1

        // 情绪：更开心更松
        base += (int) Math.round(mood * 1.0);             // -1~+1 -> -1~+1

        return clampInt(base, 1, 8);
    }

    private static final class RangeThreshold {
        final HandGroup openMaxGroup;
        final HandGroup vsOpenContinueMaxGroup;
        final HandGroup vsOpen3BetValueMaxGroup;
        final HandGroup vs3BetContinueMaxGroup;
        final HandGroup vs3Bet4BetValueMaxGroup;

        RangeThreshold(HandGroup openMaxGroup,
                       HandGroup vsOpenContinueMaxGroup,
                       HandGroup vsOpen3BetValueMaxGroup,
                       HandGroup vs3BetContinueMaxGroup,
                       HandGroup vs3Bet4BetValueMaxGroup) {
            this.openMaxGroup = openMaxGroup;
            this.vsOpenContinueMaxGroup = vsOpenContinueMaxGroup;
            this.vsOpen3BetValueMaxGroup = vsOpen3BetValueMaxGroup;
            this.vs3BetContinueMaxGroup = vs3BetContinueMaxGroup;
            this.vs3Bet4BetValueMaxGroup = vs3Bet4BetValueMaxGroup;
        }
    }

    private static RangeThreshold thresholdsFor(DpNpcEngine.TablePosition pos, int rangeLevel) {
        // 这里用“阈值表”实现：rangeLevel 越大允许的 HandGroup 越差（更松）
        // 先做一个稳定可调的版本：早位更紧，后位更松；盲注介于中后位之间。
        HandGroup open;
        if (pos == DpNpcEngine.TablePosition.EARLY) open = mapLevelToGroup(rangeLevel - 2);
        else if (pos == DpNpcEngine.TablePosition.MIDDLE) open = mapLevelToGroup(rangeLevel - 1);
        else if (pos == DpNpcEngine.TablePosition.LATE) open = mapLevelToGroup(rangeLevel);
        else open = mapLevelToGroup(rangeLevel - 1);

        // 面对 open：比「自己 open」紧 1 档即可；非后位原先用 rangeLevel-2 过紧
        HandGroup vsOpenContinue = mapLevelToGroup(rangeLevel - 1);
        HandGroup vsOpen3BetValue = mapLevelToGroup(rangeLevel - 4);
        HandGroup vs3BetContinue = mapLevelToGroup(rangeLevel - 4);
        HandGroup vs3Bet4BetValue = mapLevelToGroup(rangeLevel - 6);

        // 后位可再宽半档：用满档 rangeLevel 映射到略宽的一组
        if (pos == DpNpcEngine.TablePosition.LATE) {
            vsOpenContinue = mapLevelToGroup(rangeLevel);
        }
        if (pos == DpNpcEngine.TablePosition.EARLY) {
            vsOpen3BetValue = mapLevelToGroup(rangeLevel - 5);
        }
        return new RangeThreshold(open, vsOpenContinue, vsOpen3BetValue, vs3BetContinue, vs3Bet4BetValue);
    }

    private static HandGroup mapLevelToGroup(int level) {
        int lv = clampInt(level, 1, 8);
        return switch (lv) {
            case 1 -> HandGroup.G1;
            case 2 -> HandGroup.G2;
            case 3 -> HandGroup.G3;
            case 4 -> HandGroup.G4;
            case 5 -> HandGroup.G5;
            case 6 -> HandGroup.G6;
            case 7 -> HandGroup.G7;
            default -> HandGroup.G8;
        };
    }

    private static boolean is3BetBluffCandidate(HandGroup g) {
        return g == HandGroup.G4 || g == HandGroup.G5;
    }

    private static boolean is4BetBluffCandidate(HandGroup g) {
        return g == HandGroup.G3 || g == HandGroup.G4;
    }

    private static boolean isGroupAtMost(HandGroup g, HandGroup maxAllowed) {
        if (g == null || maxAllowed == null) return false;
        return g.ordinal() <= maxAllowed.ordinal();
    }

    private static double clamp01(double v) {
        if (v < 0) return 0.0;
        if (v > 1) return 1.0;
        return v;
    }

    private static int clampInt(int v, int min, int max) {
        if (v < min) return min;
        if (v > max) return max;
        return v;
    }

    private static final class HoleInfo {
        final boolean valid;
        final int r1;
        final int r2;
        final boolean suited;

        HoleInfo(boolean valid, int r1, int r2, boolean suited) {
            this.valid = valid;
            this.r1 = r1;
            this.r2 = r2;
            this.suited = suited;
        }
    }

    private static HoleInfo parseHole(List<String> holeCards) {
        if (holeCards == null || holeCards.size() < 2) {
            return new HoleInfo(false, 0, 0, false);
        }
        Card a = parseCard(holeCards.get(0));
        Card b = parseCard(holeCards.get(1));
        if (!a.valid || !b.valid) {
            return new HoleInfo(false, 0, 0, false);
        }
        return new HoleInfo(true, a.rank, b.rank, a.suit.equals(b.suit));
    }

    private static final class Card {
        final boolean valid;
        final String suit;
        final int rank;

        Card(boolean valid, String suit, int rank) {
            this.valid = valid;
            this.suit = suit;
            this.rank = rank;
        }
    }

    private static Card parseCard(String c) {
        if (c == null || !c.contains("_")) return new Card(false, "", 0);
        String[] parts = c.split("_", 2);
        if (parts.length != 2) return new Card(false, "", 0);
        String suit = parts[0];
        String rankStr = parts[1];
        int r = RANK.getOrDefault(rankStr, 0);
        if (r <= 0) return new Card(false, "", 0);
        return new Card(true, suit, r);
    }

    private static HandGroup groupOf(HoleInfo h) {
        int a = Math.max(h.r1, h.r2);
        int b = Math.min(h.r1, h.r2);
        boolean pair = a == b;
        if (pair) {
            if (a >= 11) return HandGroup.G1;          // JJ+
            if (a == 10) return HandGroup.G2;          // TT
            if (a == 9 || a == 8) return HandGroup.G3; // 99/88
            if (a == 7 || a == 6) return HandGroup.G4; // 77/66
            return HandGroup.G5;                        // 55-
        }

        boolean suited = h.suited;
        boolean hasAce = (a == 14);
        boolean broadwayA = (a >= 10);
        boolean broadwayB = (b >= 10);
        int gap = a - b;

        // AK / AQ / AJ
        if (hasAce && b >= 11) {
            if (a == 14 && b == 13) return HandGroup.G1; // AK
            if (b == 12) return HandGroup.G2;            // AQ
            return suited ? HandGroup.G2 : HandGroup.G3;  // AJ
        }

        // AT/A9s-A5s：作为“形状牌”在深码/后位更常出现
        if (hasAce && suited && b >= 5 && b <= 10) {
            if (b >= 9) return HandGroup.G4;
            return HandGroup.G5;
        }

        // 同花大牌
        if (suited && broadwayA && broadwayB) {
            if (a >= 13 && b >= 10) return HandGroup.G2; // KTs+ / QTs / JTs 近似
            return HandGroup.G3;
        }

        // offsuit 大牌：KQ/QJ/JT 等
        if (!suited && broadwayA && broadwayB) {
            if (a >= 13 && b >= 11) return HandGroup.G3; // KQ/KJ
            if (a >= 12 && b >= 11) return HandGroup.G4; // QJ
            return HandGroup.G6;                           // JT/TT?（TT 已处理）
        }

        // 同花连张 / 一隔
        if (suited) {
            if (gap == 1 && a >= 6) return HandGroup.G5;
            if (gap == 2 && a >= 9) return HandGroup.G6;
        }

        // 其它杂牌：作为后位偷牌候选
        if (suited && a >= 11) return HandGroup.G6;
        if (suited && a >= 9) return HandGroup.G7;

        return HandGroup.G8;
    }

    private static int countActivePlayers(DpRoom room) {
        if (room == null || room.getPlayers() == null) return 0;
        int n = 0;
        for (DpPlayer p : room.getPlayers()) {
            if (p == null) continue;
            if (p.isLeftThisHand() || p.isFold()) continue;
            n++;
        }
        return n;
    }

    private static DpPlayer findAggressor(DpRoom room, DpPlayer hero) {
        if (room == null || room.getPlayers() == null) return null;
        DpPlayer ag = null;
        int maxBet = 0;
        for (DpPlayer p : room.getPlayers()) {
            if (p == null) continue;
            if (p == hero) continue;
            if (p.isFold() || p.isLeftThisHand()) continue;
            if (p.getBet() > maxBet) {
                maxBet = p.getBet();
                ag = p;
            }
        }
        return ag;
    }

    private enum VillainTier {
        TIGHT_OR_NIT,
        BALANCED,
        LOOSE_OR_AGGRO
    }

    private static VillainTier estimateVillainTier(PlayerStats stats) {
        if (stats == null) return VillainTier.BALANCED;
        double vpip = stats.getOverallParticipationRate();
        double pfr = stats.getOverallRaiseRate();
        if (vpip < 0.20 && pfr < 0.12) return VillainTier.TIGHT_OR_NIT;
        if (vpip > 0.45 || pfr > 0.28) return VillainTier.LOOSE_OR_AGGRO;
        return VillainTier.BALANCED;
    }
}

