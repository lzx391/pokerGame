package com.example.mgdemoplus.npc.strategypro.preflop;

import com.example.mgdemoplus.common.bo.DpRoomBO;
import com.example.mgdemoplus.common.entity.DpPlayer;
import com.example.mgdemoplus.common.entity.DpPlayerStats;
import com.example.mgdemoplus.npc.engine.DpNpcEngine;
import com.example.mgdemoplus.npc.eval.DpNpcPreflopCategory;
import com.example.mgdemoplus.npc.eval.DpNpcPreflopHandGrouper;
import com.example.mgdemoplus.npc.eval.DpNpcPreflopHandGrouper.HandGroup;
import com.example.mgdemoplus.npc.eval.DpNpcPreflopHandGrouper.HoleInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * ?? NPC ????????? G1?G8 ? {@link #groupOf} ??????????? open/3bet ?????????
 * ? <b>13?13 ????</b> ???{@code rangeLevel(1~8)} ? {@link DpNpcEngine.TablePosition}?4 ??? ???????
 * ??? 1=???????????0=??<b>?????</b>?<b>???</b>??&gt;???<strong>??</strong>?
 * <b>???</b>??&lt;???<strong>??</strong>??????????????
 * {@code rangeLevel} ? vpip???????????callStation?foldToPressure ???3bet/4bet ???? pfr ???
 * {@link DpNpcEngine.BotType} ????????MANIAC +2?LAG/FISH/CALL +1?NIT ?2??
 *
 * <p>
 * <b>??????</b>??? {@link DpNpcEngine.TablePosition} ???????????? {@code rangeLevel} ?????????
 * ???????3bet/4bet ????????<strong>????????</strong>????????????
 *
 * <p>
 * <b>????</b>?? {@link com.example.mgdemoplus.room.impl.DpRoomServiceImpl} ??????????? {@code (dealerIndex + 3) % N}?UTG??
 * ????????????????????????????????????????? {@code rank}?1=??????????????
 * {@code lateFactor = (rank - 1) / (active - 1)} ? [0,1]???? 1 ???<strong>???????</strong>?????????????????
 * ???? {@link DpNpcEngine.TablePosition} ??????????????????/???? BLINDS??
 * </p>
 */
public final class DpNpcUnifiedPreflopStrategy {
    private DpNpcUnifiedPreflopStrategy() {
    }


    enum PreflopSpot {
        UNOPENED, // ??????callAmount==0 ? raiseLevel==0/1?
        FACING_OPEN, // ?? open?raiseLevel==1?
        FACING_3BET, // ? open ?? 3bet?raiseLevel==2?
        FACING_4BET, // ?? 4bet+?raiseLevel>=3?
        UNKNOWN
    }

    /** ?? 2?A ??????? 0?12? */
    private static final int RANK_DIM = 13;

    /** ? {@link DpNpcEngine.TablePosition#ordinal()} ???EARLY, MIDDLE, LATE, BLINDS? */
    private static final int POS_DIM = 4;

    /** {@code rangeLevel 1~8} ? ???? {@code 0~7}? */
    private static final int LEVEL_DIM = 8;

    /**
     * ????????? open?? {@code RangeThreshold.openMaxGroup}??
     * {@code openAllow[posOrdinal][rangeLevel-1][row][col]}?
     */
    private static final byte[][][][] openAllow = new byte[POS_DIM][LEVEL_DIM][RANK_DIM][RANK_DIM];

    /** ?? open????????? {@code vsOpenContinueMaxGroup}?? */
    private static final byte[][][][] vsOpenContinueAllow = new byte[POS_DIM][LEVEL_DIM][RANK_DIM][RANK_DIM];

    /** ?? open??? 3bet ???? {@code vsOpen3BetValueMaxGroup}?? */
    private static final byte[][][][] vsOpen3BetValueAllow = new byte[POS_DIM][LEVEL_DIM][RANK_DIM][RANK_DIM];

    /** ?? 3bet??????? {@code vs3BetContinueMaxGroup}?? */
    private static final byte[][][][] vs3BetContinueAllow = new byte[POS_DIM][LEVEL_DIM][RANK_DIM][RANK_DIM];

    /** ?? 3bet??? 4bet ???? {@code vs3Bet4BetValueMaxGroup}?? */
    private static final byte[][][][] vs3Bet4BetValueAllow = new byte[POS_DIM][LEVEL_DIM][RANK_DIM][RANK_DIM];

    /** ?? 4bet+?pot-odds ?????? G4 ????????? {@code HandGroup.G4} ?????? */
    private static final byte[][] facing4BetPotOddsCallAllow = new byte[RANK_DIM][RANK_DIM];

    /** ?? 4bet+?Jam ?? G2 ?????????????? */
    private static final byte[][] facing4BetJamAllow = new byte[RANK_DIM][RANK_DIM];

    /** ?? 4bet+?????????? G3?? */
    private static final byte[][] facing4BetMidG3Allow = new byte[RANK_DIM][RANK_DIM];

    /**
     * ???????????????????{@code rangeLevel}???????????<strong>????</strong>???????
     * ?????????? G4/G5?G3/G4?
     */
    public static DpNpcEngine.BotAction decide(
            DpRoomBO room,
            DpPlayer hero,
            int callAmount,
            double callRatio,
            double vpip,
            double pfr,
            double callStation,
            double foldToPressure,
            Random random,
            DpNpcEngine.BotType botType) {
        if (room == null || hero == null || random == null) {
            return null;
        }
        if (!"preflop".equals(room.getCurrentStage())) {
            return null;
        }
        int bb = room.getBigBlindChips();
        int sb = room.getSmallBlindChips();
        if (bb <= 0) {
            return null;
        }

        HoleInfo hole = DpNpcPreflopHandGrouper.parseHole(hero.getHoleCards());//?????????????
        if (!hole.valid) {
            return new DpNpcEngine.BotAction(DpNpcEngine.BotActionType.CALL_OR_CHECK, 0);
        }

        int activePlayers = countActivePlayers(room);
        DpPlayer aggressor = findAggressor(room, hero);
        DpPlayerStats aggressorStats = aggressor != null && room.getPlayerStatsMap() != null
                ? room.getPlayerStatsMap().get(aggressor.getNickname())
                : null;
        VillainTier tier = estimateVillainTier(aggressorStats);

        double heroStackBB = hero.getChips() * 1.0 / bb;
        double effStackBB = heroStackBB;//???????bb?
        if (aggressor != null) {
            effStackBB = Math.min(hero.getChips(), aggressor.getChips()) * 1.0 / bb;
        }

        int raiseLevel = room.getRaiseLevel();//????
        PreflopSpot spot = spotBy(raiseLevel, callAmount);//?????????

        double lateFactor = computePreflopLateFactor(room, hero);
        DpNpcEngine.TablePosition position = pseudoTablePosition(lateFactor, hero.getBlind());

        int rangeLevel = computeRangeLevel(activePlayers, effStackBB, vpip, callStation, foldToPressure);
        rangeLevel += rangeLevelBonus(botType);
        rangeLevel = clampInt(rangeLevel, 1, 8);
        // ?? + rangeLevel ? ???????????3bet/4bet ????????????????
        int posIdx = position.ordinal();
        int levelIdx = rangeLevel - 1;
        int defendLevelIdx = defendMatrixLevelIdx(rangeLevel, botType, callAmount, bb);

        HandGroup g = DpNpcPreflopHandGrouper.groupOf(hole);

        // === ?????? 4bet ????? all-in / call / fold ===
        if (spot == PreflopSpot.FACING_4BET) {
            return decideFacing4Bet(hero, bb, sb, callAmount, callRatio, effStackBB, hole, tier, random);
        }

        // === ?????open / limp-check / fold ===
        if (spot == PreflopSpot.UNOPENED) {
            return decideUnopened(hero, bb, sb, activePlayers, effStackBB, hole, openAllow[posIdx][levelIdx], random);
        }

        // === ?? open?call / 3bet / fold ===
        if (spot == PreflopSpot.FACING_OPEN) {
            return decideFacingOpen(room, hero, bb, sb, callAmount, effStackBB, hole, g,
                    vsOpenContinueAllow[posIdx][defendLevelIdx], vsOpen3BetValueAllow[posIdx][levelIdx], tier, pfr, random);
        }

        // === ? open ?? 3bet?call / 4bet / fold ===
        if (spot == PreflopSpot.FACING_3BET) {
            return decideFacing3Bet(room, hero, bb, sb, callAmount, callRatio, effStackBB, hole, g,
                    vs3BetContinueAllow[posIdx][levelIdx], vs3Bet4BetValueAllow[posIdx][levelIdx], tier, pfr, random);
        }

        // fallback????/?????????
        return new DpNpcEngine.BotAction(DpNpcEngine.BotActionType.CALL_OR_CHECK, 0);
    }

    private static int rangeLevelBonus(DpNpcEngine.BotType t) {
        if (t == null) {
            return 0;
        }
        if (t == DpNpcEngine.BotType.MANIAC) {
            return 2;
        }
        if (t == DpNpcEngine.BotType.LAG) {
            return 1;
        }
        if (t == DpNpcEngine.BotType.FISH) {
            return 1;
        }
        if (t == DpNpcEngine.BotType.CALL) {
            return 2;
        }
        if (t == DpNpcEngine.BotType.NIT) {
            return -2;
        }
        return 0;
    }

    /**
     * ?? open ????? defend ???? open ?? {@link #rangeLevelBonus} ????
     * FISH/CALL ???LAG/MANIAC ?????TAG ???NIT ???
     * ?? min-raise???? ? 1BB?? +1 ???? pot odds ???? defend ???
     */
    private static int defendLevelBonus(DpNpcEngine.BotType t) {
        if (t == null) {
            return 0;
        }
        if (t == DpNpcEngine.BotType.FISH || t == DpNpcEngine.BotType.CALL) {
            return 2;
        }
        if (t == DpNpcEngine.BotType.LAG || t == DpNpcEngine.BotType.MANIAC) {
            return 1;
        }
        if (t == DpNpcEngine.BotType.NIT) {
            return -1;
        }
        return 0;
    }

    private static int defendMatrixLevelIdx(int rangeLevel, DpNpcEngine.BotType botType, int callAmount, int bb) {
        int defendLevel = clampInt(rangeLevel + defendLevelBonus(botType), 1, 8);
        if (callAmount > 0 && bb > 0 && callAmount <= 2 * bb) {
            defendLevel = clampInt(defendLevel + 1, 1, 8);
        }
        return defendLevel - 1;
    }

    private static double pfrAggressionScale(double pfr) {
        double p = pfr;
        if (p < 0.0) {
            p = 0.0;
        } else if (p > 1.0) {
            p = 1.0;
        }
        return 0.35 + 0.65 * p;
    }

    private static DpNpcEngine.BotAction decideUnopened(
            DpPlayer hero,
            int bb,
            int sb,
            int activePlayers,
            double effStackBB,
            HoleInfo hole,
            byte[][] openSlice,
            Random random) {
        boolean canOpen = matrixAllows(openSlice, hole);
        if (!canOpen) {
            // ???????????????????
            return new DpNpcEngine.BotAction(DpNpcEngine.BotActionType.CALL_OR_CHECK, 0);
        }

        double openSizeBB = baseOpenSizeBB(activePlayers, effStackBB, random);
        int raiseAmount = openRaiseChips(openSizeBB, bb, sb, hero.getChips());
        if (raiseAmount <= 0) {
            return new DpNpcEngine.BotAction(DpNpcEngine.BotActionType.CALL_OR_CHECK, 0);
        }
        return new DpNpcEngine.BotAction(DpNpcEngine.BotActionType.RAISE, raiseAmount);
    }

    private static DpNpcEngine.BotAction decideFacingOpen(
            DpRoomBO room,
            DpPlayer hero,
            int bb,
            int sb,
            int callAmount,
            double effStackBB,
            HoleInfo hole,
            HandGroup g,
            byte[][] vsOpenContinueSlice,
            byte[][] vsOpen3BetValueSlice,
            VillainTier tier,
            double pfr,
            Random random) {
        boolean canContinue = matrixAllows(vsOpenContinueSlice, hole);
        if (!canContinue) {
            return new DpNpcEngine.BotAction(DpNpcEngine.BotActionType.FOLD, 0);
        }

        boolean can3BetValue = matrixAllows(vsOpen3BetValueSlice, hole);
        boolean can3BetBluff = is3BetBluffCandidate(g) && tier == VillainTier.LOOSE_OR_AGGRO;

        double base3betProb = can3BetValue ? 0.62 : (can3BetBluff ? 0.16 : 0.0);
        base3betProb *= pfrAggressionScale(pfr);
        base3betProb = clamp01(base3betProb);

        if (base3betProb > 0 && hero.getChips() > callAmount && random.nextDouble() < base3betProb) {
            int villainBetToCall = room.getCurrentBetToCall();
            int raiseAmount = compute3BetAmount(hero, villainBetToCall, callAmount, bb, sb, effStackBB, random);
            if (raiseAmount > callAmount) {
                return new DpNpcEngine.BotAction(DpNpcEngine.BotActionType.RAISE, raiseAmount);
            }
        }

        return new DpNpcEngine.BotAction(DpNpcEngine.BotActionType.CALL_OR_CHECK, 0);
    }

    private static DpNpcEngine.BotAction decideFacing3Bet(
            DpRoomBO room,
            DpPlayer hero,
            int bb,
            int sb,
            int callAmount,
            double callRatio,
            double effStackBB,
            HoleInfo hole,
            HandGroup g,
            byte[][] vs3BetContinueSlice,
            byte[][] vs3Bet4BetValueSlice,
            VillainTier tier,
            double pfr,
            Random random) {
        // continue range???? open ??
        boolean canContinue = matrixAllows(vs3BetContinueSlice, hole);
        if (!canContinue) {
            return new DpNpcEngine.BotAction(DpNpcEngine.BotActionType.FOLD, 0);
        }

        // ?? 4bet?????
        boolean value4bet = matrixAllows(vs3Bet4BetValueSlice, hole);
        boolean bluff4bet = is4BetBluffCandidate(g) && tier == VillainTier.LOOSE_OR_AGGRO && effStackBB >= 18;

        // ???? 3bet??????????
        if (callRatio >= 0.35 && !value4bet) {
            // ???????????????
            if (effStackBB >= 35 && random.nextDouble() < 0.22) {
                return new DpNpcEngine.BotAction(DpNpcEngine.BotActionType.CALL_OR_CHECK, 0);
            }
            double foldP = clamp01(0.55 + 0.25 * Math.min(1.0, callRatio));
            if (random.nextDouble() < foldP) {
                return new DpNpcEngine.BotAction(DpNpcEngine.BotActionType.FOLD, 0);
            }
            return new DpNpcEngine.BotAction(DpNpcEngine.BotActionType.CALL_OR_CHECK, 0);
        }

        double fourBetProb = value4bet ? 0.72 : (bluff4bet ? 0.14 : 0.0);
        fourBetProb *= pfrAggressionScale(pfr);
        fourBetProb = clamp01(fourBetProb);

        if (fourBetProb > 0 && hero.getChips() > callAmount && random.nextDouble() < fourBetProb) {
            int villainBetToCall = room.getCurrentBetToCall();
            int raiseAmount = compute4BetAmount(hero, villainBetToCall, callAmount, bb, sb, effStackBB, random);
            if (raiseAmount > callAmount) {
                // ??/?????????????????
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
            HoleInfo hole,
            VillainTier tier,
            Random random) {
        // ???????????????????????? 5bet ????
        double potOdds;
        int denom = hero.getBet() + hero.getChips() + callAmount; // ???? hero ??????????
        if (callAmount <= 0 || denom <= 0)
            potOdds = 1.0;
        else
            potOdds = callAmount * 1.0 / denom;
        if (callAmount > 0 && potOdds <= 0.18) {
            if (matrixAllows(facing4BetPotOddsCallAllow, hole)) {
                return new DpNpcEngine.BotAction(DpNpcEngine.BotActionType.CALL_OR_CHECK, 0);
            }
            if (random.nextDouble() < 0.40) {
                return new DpNpcEngine.BotAction(DpNpcEngine.BotActionType.CALL_OR_CHECK, 0);
            }
            return new DpNpcEngine.BotAction(DpNpcEngine.BotActionType.FOLD, 0);
        }

        boolean jamValue = matrixAllows(facing4BetJamAllow, hole);
        if (jamValue) {
            // ??????????????????????????????
            double jamProb = (tier == VillainTier.TIGHT_OR_NIT) ? 0.70 : 0.82;
            if (effStackBB >= 45)
                jamProb -= 0.12;
            jamProb = clamp01(jamProb);
            if (random.nextDouble() < jamProb) {
                return new DpNpcEngine.BotAction(DpNpcEngine.BotActionType.ALL_IN, hero.getChips());
            }
            return new DpNpcEngine.BotAction(DpNpcEngine.BotActionType.CALL_OR_CHECK, 0);
        }

        // ????????/??
        if (matrixAllows(facing4BetMidG3Allow, hole)) {
            double foldP = 0.35 + 0.25 * Math.min(1.0, callRatio);
            if (tier == VillainTier.TIGHT_OR_NIT)
                foldP += 0.10;
            foldP = clamp01(foldP);
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
            double effStackBB,
            Random random) {
        if (villainBetToCall < bb)
            villainBetToCall = bb;
        // ? IP?3??OOP?4?????????????????????????
        double mult = 3.5;
        // ??????????????????????????????
        if (effStackBB >= 50)
            mult -= 0.2;
        if (effStackBB <= 18)
            mult += 0.3;
        mult += (random.nextDouble() * 0.4 - 0.2);
        int desiredTotalBet = (int) Math.round(villainBetToCall * mult);
        int minTotalBet = villainBetToCall + 3 * bb;
        if (desiredTotalBet < minTotalBet)
            desiredTotalBet = minTotalBet;
        int raiseAmount = desiredTotalBet - hero.getBet();
        if (raiseAmount < 0)
            raiseAmount = 0;
        if (raiseAmount > hero.getChips())
            raiseAmount = hero.getChips();
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
            double effStackBB,
            Random random) {
        if (villainBetToCall < bb)
            villainBetToCall = bb;
        double mult = 2.2 + random.nextDouble() * 0.4;
        if (effStackBB <= 20)
            mult += 0.1;
        int desiredTotalBet = (int) Math.round(villainBetToCall * mult);
        int minTotalBet = villainBetToCall + 4 * bb;
        if (desiredTotalBet < minTotalBet)
            desiredTotalBet = minTotalBet;
        int raiseAmount = desiredTotalBet - hero.getBet();
        if (raiseAmount < 0)
            raiseAmount = 0;
        if (raiseAmount > hero.getChips())
            raiseAmount = hero.getChips();
        if (raiseAmount <= callAmount && hero.getChips() > callAmount) {
            raiseAmount = Math.min(hero.getChips(), callAmount + 2 * bb);
        }
        return roundToSB(raiseAmount, sb, hero.getChips());
    }

    /** ??????????????????????? {@link #openAllow} ????????? / ??????? */
    private static double baseOpenSizeBB(
            int activePlayers,
            double effStackBB,
            Random random) {
        double base = 2.8;
        if (activePlayers <= 3) {
            base -= 0.35;
        }
        if (effStackBB <= 18) {
            base -= 0.35;
        }
        if (effStackBB >= 60) {
            base += 0.45;
        }
        base += random.nextDouble() * 1.4 - 0.7;
        return clampDouble(base, 2.0, 4.0);
    }

    private static int openRaiseChips(double openSizeBB, int bb, int sb, int max) {
        if (openSizeBB <= 0 || bb <= 0 || max <= 0) {
            return 0;
        }
        int target = (int) Math.round(openSizeBB * bb);
        return roundRaiseToBlind(target, bb, sb, max);
    }

    private static int roundRaiseToBlind(int amount, int bb, int sb, int max) {
        if (amount <= 0) {
            return 0;
        }
        int increment = raiseChipIncrement(bb, sb);
        if (increment <= 0) {
            return Math.min(amount, max);
        }
        int units = Math.max(1, (int) Math.round(amount * 1.0 / increment));
        int v = units * increment;
        if (v > max) {
            units = Math.max(1, max / increment);
            v = units * increment;
        }
        return v;
    }

    private static int raiseChipIncrement(int bb, int sb) {
        if (bb <= 0) {
            return Math.max(1, sb);
        }
        if (sb > 0 && bb % sb == 0 && bb / sb == 2) {
            return bb / 2;
        }
        if (sb > 0) {
            return sb;
        }
        return bb;
    }

    private static int roundToSB(int amount, int sb, int max) {
        int bb = sb > 0 ? sb * 2 : 0;
        return roundRaiseToBlind(amount, bb, sb, max);
    }

    private static double clampDouble(double v, double min, double max) {
        if (v < min) {
            return min;
        }
        if (v > max) {
            return max;
        }
        return v;
    }

    private static PreflopSpot spotBy(int raiseLevel, int callAmount) {
        if (raiseLevel >= 3)
            return PreflopSpot.FACING_4BET;
        if (raiseLevel == 2)
            return PreflopSpot.FACING_3BET;
        if (raiseLevel == 1 && callAmount > 0)
            return PreflopSpot.FACING_OPEN;
        if (callAmount == 0)
            return PreflopSpot.UNOPENED;
        return PreflopSpot.UNKNOWN;
    }

    /**
     * ?????????????????? UTG?BB **??????**??????? open / 3bet / 4bet ????
     * ?????????? {@code rank} ???{@code lateFactor = (rank-1)/(active-1)} ??? 1?
     *
     * <p>
     * <b>????3B?4B?????</b>????????????<strong>?????????</strong>?
     * ????? {@code active} ????????? {@code rank / lateFactor} ???????????????????????
     * ?<strong>??</strong> {@code raiseLevel}????????????????????<strong>????? proxy</strong>?
     * </p>
     * <p>
     * ??? squeeze / ??????????????????????
     * </p>
     */
    static double computePreflopLateFactor(DpRoomBO room, DpPlayer hero) {
        List<DpPlayer> ps = room != null ? room.getPlayers() : null;
        if (ps == null || ps.isEmpty() || hero == null) {
            return 0.5;
        }
        int n = ps.size();
        int dealer = -1;
        for (int i = 0; i < n; i++) {
            DpPlayer p = ps.get(i);
            if (p != null && p.isDealer()) {
                dealer = i;
                break;
            }
        }
        if (dealer < 0) {
            return 0.5;
        }
        // ????????????????UTG???
        int utg = (dealer + 3) % n;
        List<Integer> activeSeatOrder = new ArrayList<>();
        for (int step = 0; step < n; step++) {
            int idx = (utg + step) % n;
            DpPlayer p = ps.get(idx);
            if (p == null) {
                continue;
            }
            if (p.isLeftThisHand() || p.isFold()) {
                continue;
            }
            activeSeatOrder.add(idx);
        }
        int active = activeSeatOrder.size();
        if (active <= 1) {
            return 0.5;
        }
        int heroIdx = ps.indexOf(hero);
        int rank = -1;
        for (int i = 0; i < activeSeatOrder.size(); i++) {
            if (activeSeatOrder.get(i) == heroIdx) {
                rank = i + 1;
                break;
            }
        }
        if (rank < 0) {
            return 0.5;
        }
        return (rank - 1) * 1.0 / (active - 1);
    }

    /**
     * ?? {@code lateFactor} ? ?????????????????? HU ? BTN/SB??
     */
    private static DpNpcEngine.TablePosition pseudoTablePosition(double lateFactor, int blind) {
        if (blind == 1 || blind == 2) {
            return DpNpcEngine.TablePosition.BLINDS;
        }
        if (lateFactor < 0.34) {
            return DpNpcEngine.TablePosition.EARLY;
        }
        if (lateFactor < 0.67) {
            return DpNpcEngine.TablePosition.MIDDLE;
        }
        return DpNpcEngine.TablePosition.LATE;
    }

    private static int computeRangeLevel(
            int activePlayers,
            double effStackBB,
            double vpip,
            double callStation,
            double foldToPressure) {
        // 1~8??????????????? thresholdsFor(pseudoPosition(lateFactor)) ?????????????
        int base = 4;

        // ?????????
        if (activePlayers <= 3)
            base += 2;
        else if (activePlayers <= 5)
            base += 1;

        // ???????????????????
        if (effStackBB >= 50)
            base += 1;
        if (effStackBB <= 16)
            base -= 1;

        // vpip ???????? 0.15~0.60?
        base += (int) Math.round((vpip - 0.30) * 5.0);
        base += (int) Math.round(callStation * 1.0);
        base -= (int) Math.round(foldToPressure * 0.8);

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
        // ???????????rangeLevel ????? HandGroup ??????
        // ????????????????????????????????
        HandGroup open;
        if (pos == DpNpcEngine.TablePosition.EARLY)
            open = mapLevelToGroup(rangeLevel - 2);
        else if (pos == DpNpcEngine.TablePosition.MIDDLE)
            open = mapLevelToGroup(rangeLevel - 1);
        else if (pos == DpNpcEngine.TablePosition.LATE)
            open = mapLevelToGroup(rangeLevel);
        else
            open = mapLevelToGroup(rangeLevel - 1);

        HandGroup vsOpenContinue;
        if (pos == DpNpcEngine.TablePosition.EARLY) {
            vsOpenContinue = mapLevelToGroup(rangeLevel - 1);
        } else if (pos == DpNpcEngine.TablePosition.LATE) {
            vsOpenContinue = mapLevelToGroup(rangeLevel + 1);
        } else {
            vsOpenContinue = mapLevelToGroup(rangeLevel);
        }
        HandGroup vsOpen3BetValue = mapLevelToGroup(rangeLevel - 4);
        HandGroup vs3BetContinue = mapLevelToGroup(rangeLevel - 4);
        HandGroup vs3Bet4BetValue = mapLevelToGroup(rangeLevel - 6);
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
        if (g == null || maxAllowed == null)
            return false;
        return g.ordinal() <= maxAllowed.ordinal();
    }

    private static double clamp01(double v) {
        if (v < 0)
            return 0.0;
        if (v > 1)
            return 1.0;
        return v;
    }

    private static int clampInt(int v, int min, int max) {
        if (v < min)
            return min;
        if (v > max)
            return max;
        return v;
    }


    private static int rankToIndex(int rank) {
        return rank - 2;
    }

    /**
     * 13?13 ???????? = ?? ? 2?2?0 ? A?12??
     * <ul>
     * <li>????? {@code [i][i]}</li>
     * <li>????????{@code i > j}???? {@code i+2}???? {@code j+2}</li>
     * <li>???{@code i < j}???? {@code j+2}???? {@code i+2}</li>
     * </ul>
     */
    private static boolean matrixAllows(byte[][] m, HoleInfo h) {
        if (m == null || h == null || !h.valid) {
            return false;
        }
        int ia = rankToIndex(h.r1);
        int ib = rankToIndex(h.r2);
        int hi = Math.max(ia, ib);
        int lo = Math.min(ia, ib);
        if (hi < 0 || hi >= RANK_DIM || lo < 0 || lo >= RANK_DIM) {
            return false;
        }
        if (hi == lo) {
            return m[hi][hi] != 0;
        }
        if (h.suited) {
            return m[lo][hi] != 0;
        }
        return m[hi][lo] != 0;
    }

    private static HandGroup handGroupAtMatrixCell(int row, int col) {
        if (row == col) {
            int r = row + 2;
            return DpNpcPreflopHandGrouper.groupOf(new HoleInfo(true, r, r, false));
        }
        if (row > col) {
            return DpNpcPreflopHandGrouper.groupOf(new HoleInfo(true, row + 2, col + 2, false));
        }
        return DpNpcPreflopHandGrouper.groupOf(new HoleInfo(true, col + 2, row + 2, true));
    }

    /** @param exact ? {@code true} ??? {@code group == maxAllowed} ? 1??? 4bet ??? G3????? */
    private static void fillRangeSlice(byte[][] m, HandGroup maxAllowed, boolean exact) {
        for (int i = 0; i < RANK_DIM; i++) {
            for (int j = 0; j < RANK_DIM; j++) {
                HandGroup gCell = handGroupAtMatrixCell(i, j);
                boolean ok = exact ? (gCell == maxAllowed) : isGroupAtMost(gCell, maxAllowed);
                m[i][j] = (byte) (ok ? 1 : 0);
            }
        }
    }

    static {
        for (int pos = 0; pos < POS_DIM; pos++) {
            DpNpcEngine.TablePosition tp = DpNpcEngine.TablePosition.values()[pos];
            for (int lv = 0; lv < LEVEL_DIM; lv++) {
                RangeThreshold th = thresholdsFor(tp, lv + 1);
                fillRangeSlice(openAllow[pos][lv], th.openMaxGroup, false);
                fillRangeSlice(vsOpenContinueAllow[pos][lv], th.vsOpenContinueMaxGroup, false);
                fillRangeSlice(vsOpen3BetValueAllow[pos][lv], th.vsOpen3BetValueMaxGroup, false);
                fillRangeSlice(vs3BetContinueAllow[pos][lv], th.vs3BetContinueMaxGroup, false);
                fillRangeSlice(vs3Bet4BetValueAllow[pos][lv], th.vs3Bet4BetValueMaxGroup, false);
            }
        }
        fillRangeSlice(facing4BetPotOddsCallAllow, HandGroup.G4, false);
        fillRangeSlice(facing4BetJamAllow, HandGroup.G2, false);
        fillRangeSlice(facing4BetMidG3Allow, HandGroup.G3, true);
    }

    private static int countActivePlayers(DpRoomBO room) {
        if (room == null || room.getPlayers() == null)
            return 0;
        int n = 0;
        for (DpPlayer p : room.getPlayers()) {
            if (p == null)
                continue;
            if (p.isLeftThisHand() || p.isFold())
                continue;
            n++;
        }
        return n;
    }

    private static DpPlayer findAggressor(DpRoomBO room, DpPlayer hero) {//???????bet??hero???
        if (room == null || room.getPlayers() == null)
            return null;
        DpPlayer ag = null;
        int maxBet = 0;
        for (DpPlayer p : room.getPlayers()) {
            if (p == null)
                continue;
            if (p == hero)
                continue;
            if (p.isFold() || p.isLeftThisHand())
                continue;
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

    private static VillainTier estimateVillainTier(DpPlayerStats stats) {
        if (stats == null)
            return VillainTier.BALANCED;
        double vpip = stats.getOverallParticipationRate();
        double pfr = stats.getOverallRaiseRate();
        if (vpip < 0.20 && pfr < 0.12)
            return VillainTier.TIGHT_OR_NIT;
        if (vpip > 0.45 || pfr > 0.28)
            return VillainTier.LOOSE_OR_AGGRO;
        return VillainTier.BALANCED;
    }

    /** G1?G8 ? {@link DpNpcPreflopCategory}?? {@link com.example.mgdemoplus.npc.eval.DpNpcEquityEstimator} ?????? */
    public static DpNpcPreflopCategory preflopCategoryOf(List<String> holeCards) {
        return DpNpcPreflopHandGrouper.preflopCategoryOf(holeCards);
    }
}
