package com.example.mgdemoplus.npc.strategypro.preflop;

import com.example.mgdemoplus.common.bo.DpRoomBO;
import com.example.mgdemoplus.common.entity.DpPlayer;
import com.example.mgdemoplus.npc.engine.DpNpcEngine;
import com.example.mgdemoplus.npc.rulethink.DpNpcRuleThinkProperties;
import com.example.mgdemoplus.npc.rulethink.DpNpcRuleThinkSampler;
import com.example.mgdemoplus.npc.trace.DpNpcTagDecisionTraceCollector;
import com.example.mgdemoplus.npc.trace.model.DpNpcActionTrace;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DpNpcUnifiedPreflopTraceMatrixTest {

    private static final double TAG_VPIP = 0.24;
    private static final double TAG_PFR = 0.76;
    private static final double TAG_CALL_STATION = 0.18;
    private static final double TAG_FOLD = 0.22;

    @BeforeEach
    void disableRuleThink() {
        DpNpcRuleThinkProperties props = new DpNpcRuleThinkProperties();
        props.setEnabled(false);
        DpNpcRuleThinkSampler.bind(props);
    }

    @AfterEach
    void clearCollector() {
        DpNpcTagDecisionTraceCollector.clear();
    }

    @Test
    void facing4Bet_effectiveJamIsSubsetOfVs3BetContinue_allPosLevels() {
        for (int pos = 0; pos < 4; pos++) {
            for (int lv = 0; lv < 8; lv++) {
                byte[][] jam = DpNpcUnifiedPreflopStrategy.effectiveFacing4BetJamAllow(pos, lv);
                byte[][] cont = DpNpcUnifiedPreflopStrategy.vs3BetContinueMatrix(pos, lv);
                assertMatrixSubset(jam, cont, pos, lv);
            }
        }
    }

    @Test
    void facing4Bet_lowRangeLevel_ttNotInEffectiveJam() {
        DpRoomBO room = buildFacing4BetLowRangeLevelRoom("hearts_10", "diamonds_10");
        DpPlayer hero = room.getPlayers().get(0);
        int callAmount = room.getCurrentBetToCall() - hero.getBet();

        DpNpcTagDecisionTraceCollector.begin(room, hero);
        DpNpcEngine.BotAction action = DpNpcUnifiedPreflopStrategy.decide(
                room,
                hero,
                callAmount,
                0.15,
                NIT_VPIP,
                NIT_PFR,
                NIT_CALL_STATION,
                NIT_FOLD,
                new Random(42L),
                DpNpcEngine.BotType.NIT);
        DpNpcActionTrace trace = DpNpcTagDecisionTraceCollector.build(action);

        assertNotNull(trace);
        assertNotNull(trace.preflopMatrix);
        assertEquals(false, trace.preflopMatrix.heroCell.inRange,
                "TT at RL1 should not be in effective jam when vs3BetContinue is G1-only");
        assertNotEquals(DpNpcEngine.BotActionType.ALL_IN, action.getType());
    }

    @Test
    void nitSixMaxDeepStack_traceRangeLevelAtLeastTwo() {
        DpRoomBO room = buildUnopenedBtnRoom("hearts_A", "diamonds_A");
        DpPlayer hero = room.getPlayers().get(0);

        DpNpcTagDecisionTraceCollector.begin(room, hero);
        DpNpcEngine.BotAction action = DpNpcUnifiedPreflopStrategy.decide(
                room,
                hero,
                0,
                0.0,
                NIT_VPIP,
                NIT_PFR,
                NIT_CALL_STATION,
                NIT_FOLD,
                new Random(42L),
                DpNpcEngine.BotType.NIT);
        DpNpcActionTrace trace = DpNpcTagDecisionTraceCollector.build(action);

        assertNotNull(trace);
        assertNotNull(trace.preflopMatrix);
        assertTrue(trace.preflopMatrix.rangeLevel >= 2,
                "6-max deep-stack NIT rangeLevel should be >= 2, got " + trace.preflopMatrix.rangeLevel);
    }

    private static void assertMatrixSubset(byte[][] subset, byte[][] superset, int pos, int lv) {
        for (int i = 0; i < 13; i++) {
            for (int j = 0; j < 13; j++) {
                if (subset[i][j] != 0) {
                    assertTrue(superset[i][j] != 0,
                            "jam cell [" + i + "][" + j + "] at pos=" + pos + " lv=" + lv
                                    + " must be in vs3BetContinue");
                }
            }
        }
    }

    private static final double NIT_VPIP = 0.11;
    private static final double NIT_PFR = 0.15;
    private static final double NIT_CALL_STATION = 0.37;
    private static final double NIT_FOLD = 0.92;

    @Test
    void facingOpen_attachesSecondaryMatrixAndRaiseMeta() {
        DpRoomBO room = buildFacingMinRaiseBtnRoom("hearts_A", "diamonds_K");
        DpPlayer hero = room.getPlayers().get(0);
        int callAmount = room.getCurrentBetToCall() - hero.getBet();

        DpNpcTagDecisionTraceCollector.begin(room, hero);
        DpNpcEngine.BotAction action = DpNpcUnifiedPreflopStrategy.decide(
                room,
                hero,
                callAmount,
                0.02,
                TAG_VPIP,
                TAG_PFR,
                TAG_CALL_STATION,
                TAG_FOLD,
                new Random(42L),
                DpNpcEngine.BotType.TAG);
        DpNpcActionTrace trace = DpNpcTagDecisionTraceCollector.build(action);

        assertNotNull(action);
        assertNotNull(trace);
        assertNotNull(trace.preflopMatrix);
        assertEquals("vsOpenContinueAllow", trace.preflopMatrix.matrixKind);
        assertEquals("FACING_OPEN", trace.preflopMatrix.spot);

        assertNotNull(trace.preflopMatrixSecondary);
        assertEquals("vsOpen3BetValueAllow", trace.preflopMatrixSecondary.matrixKind);
        assertEquals("FACING_OPEN", trace.preflopMatrixSecondary.spot);
        assertEquals(trace.preflopMatrix.labels.length, trace.preflopMatrixSecondary.labels.length);
        assertEquals(trace.preflopMatrix.cells.length, trace.preflopMatrixSecondary.cells.length);

        assertNotNull(trace.raiseMeta);
        assertEquals("vsOpen3BetValueAllow", trace.raiseMeta.matrixKind);
        assertTrue(trace.raiseMeta.valueEligible);
        assertTrue(trace.raiseMeta.baseRaiseProb > 0.0);
        assertTrue(trace.raiseMeta.pfrScale >= 0.35 && trace.raiseMeta.pfrScale <= 1.0);
    }

    @Test
    void facing3Bet_attachesSecondaryMatrixAndRaiseMeta() {
        DpRoomBO room = buildFacing3BetEarlyRoom("hearts_A", "diamonds_K");
        DpPlayer hero = room.getPlayers().get(0);
        int callAmount = room.getCurrentBetToCall() - hero.getBet();

        DpNpcTagDecisionTraceCollector.begin(room, hero);
        DpNpcEngine.BotAction action = DpNpcUnifiedPreflopStrategy.decide(
                room,
                hero,
                callAmount,
                0.15,
                TAG_VPIP,
                TAG_PFR,
                TAG_CALL_STATION,
                TAG_FOLD,
                new Random(42L),
                DpNpcEngine.BotType.TAG);
        DpNpcActionTrace trace = DpNpcTagDecisionTraceCollector.build(action);

        assertNotNull(action);
        assertNotNull(trace);
        assertNotNull(trace.preflopMatrix);
        assertEquals("vs3BetContinueAllow", trace.preflopMatrix.matrixKind);
        assertEquals("FACING_3BET", trace.preflopMatrix.spot);

        assertNotNull(trace.preflopMatrixSecondary);
        assertEquals("vs3Bet4BetValueAllow", trace.preflopMatrixSecondary.matrixKind);
        assertEquals("FACING_3BET", trace.preflopMatrixSecondary.spot);
        assertTrue(hasInRangeCell(trace.preflopMatrixSecondary.cells));

        assertNotNull(trace.raiseMeta);
        assertEquals("vs3Bet4BetValueAllow", trace.raiseMeta.matrixKind);
        assertTrue(trace.raiseMeta.baseRaiseProb > 0.0);
    }

    @Test
    void facing4Bet_midG3TraceMatrix_isCumulativeIntersectedWithVs3BetContinue() {
        DpRoomBO room = buildFacing4BetMiddleRoom("hearts_9", "diamonds_9");
        DpPlayer hero = room.getPlayers().get(0);
        int callAmount = room.getCurrentBetToCall() - hero.getBet();

        DpNpcTagDecisionTraceCollector.begin(room, hero);
        DpNpcEngine.BotAction action = DpNpcUnifiedPreflopStrategy.decide(
                room,
                hero,
                callAmount,
                0.25,
                0.55,
                0.15,
                0.72,
                0.12,
                new Random(1L),
                DpNpcEngine.BotType.CALL);
        DpNpcActionTrace trace = DpNpcTagDecisionTraceCollector.build(action);

        assertNotNull(trace);
        assertNotNull(trace.preflopMatrix);
        assertEquals("facing4BetMidG3Allow", trace.preflopMatrix.matrixKind);
        assertEquals("FACING_4BET", trace.preflopMatrix.spot);
        assertCellInRange(trace.preflopMatrix.cells, "AA");
        assertCellInRange(trace.preflopMatrix.cells, "99");
        assertCellOutOfRange(trace.preflopMatrix.cells, "A9s");
        assertTrue(trace.preflopMatrix.heroCell.inRange);
    }

    @Test
    void facing4Bet_belowMidG3Fold_heroOutOfRange() {
        DpRoomBO room = buildFacing4BetMiddleRoom("hearts_9", "spades_A");
        DpPlayer hero = room.getPlayers().get(0);
        int callAmount = room.getCurrentBetToCall() - hero.getBet();

        DpNpcTagDecisionTraceCollector.begin(room, hero);
        DpNpcEngine.BotAction action = DpNpcUnifiedPreflopStrategy.decide(
                room,
                hero,
                callAmount,
                0.25,
                TAG_VPIP,
                TAG_PFR,
                TAG_CALL_STATION,
                TAG_FOLD,
                new Random(1L),
                DpNpcEngine.BotType.TAG);
        DpNpcActionTrace trace = DpNpcTagDecisionTraceCollector.build(action);

        assertNotNull(action);
        assertEquals(DpNpcEngine.BotActionType.FOLD, action.getType());
        assertNotNull(trace.preflopMatrix);
        assertEquals("facing4BetMidG3Allow", trace.preflopMatrix.matrixKind);
        assertCellInRange(trace.preflopMatrix.cells, "AA");
        assertCellOutOfRange(trace.preflopMatrix.cells, "A9s");
        assertEquals(false, trace.preflopMatrix.heroCell.inRange);
    }

    @Test
    void facing4Bet_jamTraceMatrix_isCoherentPremiumRange() {
        DpRoomBO room = buildFacing4BetEarlyRoom("hearts_A", "diamonds_K");
        DpPlayer hero = room.getPlayers().get(0);
        int callAmount = room.getCurrentBetToCall() - hero.getBet();

        DpNpcTagDecisionTraceCollector.begin(room, hero);
        DpNpcEngine.BotAction action = DpNpcUnifiedPreflopStrategy.decide(
                room,
                hero,
                callAmount,
                0.15,
                TAG_VPIP,
                TAG_PFR,
                TAG_CALL_STATION,
                TAG_FOLD,
                new Random(99L),
                DpNpcEngine.BotType.TAG);
        DpNpcActionTrace trace = DpNpcTagDecisionTraceCollector.build(action);

        assertNotNull(trace.preflopMatrix);
        assertEquals("facing4BetJamAllow", trace.preflopMatrix.matrixKind);
        assertCellInRange(trace.preflopMatrix.cells, "AA");
        assertCellInRange(trace.preflopMatrix.cells, "AKo");
        assertTrue(trace.preflopMatrix.heroCell.inRange);
    }

    private static void assertCellInRange(int[][] cells, String handLabel) {
        int[] rc = displayIndexOf(handLabel);
        assertTrue(cells[rc[0]][rc[1]] != 0, handLabel + " should be in range");
    }

    private static void assertCellOutOfRange(int[][] cells, String handLabel) {
        int[] rc = displayIndexOf(handLabel);
        assertEquals(0, cells[rc[0]][rc[1]], handLabel + " should be out of range");
    }

    private static int[] displayIndexOf(String handLabel) {
        String[] labels = {"A", "K", "Q", "J", "T", "9", "8", "7", "6", "5", "4", "3", "2"};
        if (handLabel.length() == 2) {
            int row = indexOfLabel(labels, handLabel.substring(0, 1));
            int col = indexOfLabel(labels, handLabel.substring(1, 2));
            return new int[] {row, col};
        }
        String hi = handLabel.substring(0, 1);
        String lo = handLabel.substring(1, 2);
        char suffix = handLabel.charAt(2);
        int hiIdx = indexOfLabel(labels, hi);
        int loIdx = indexOfLabel(labels, lo);
        if (suffix == 's') {
            return new int[] {Math.min(hiIdx, loIdx), Math.max(hiIdx, loIdx)};
        }
        return new int[] {Math.max(hiIdx, loIdx), Math.min(hiIdx, loIdx)};
    }

    private static int indexOfLabel(String[] labels, String label) {
        for (int i = 0; i < labels.length; i++) {
            if (labels[i].equals(label)) {
                return i;
            }
        }
        return -1;
    }

    private static DpRoomBO buildFacing4BetLowRangeLevelRoom(String heroCard1, String heroCard2) {
        DpRoomBO room = buildSixMaxTable();
        for (DpPlayer p : room.getPlayers()) {
            p.setChips(160);
        }
        DpPlayer hero = room.getPlayers().get(0);
        hero.setHoleCards(List.of(heroCard1, heroCard2));
        hero.setBet(room.getBigBlindChips() * 2);

        DpPlayer fourBettor = room.getPlayers().get(3);
        fourBettor.setBet(room.getBigBlindChips() * 18);
        fourBettor.setChips(40);

        room.setRaiseLevel(3);
        room.setCurrentBetToCall(room.getBigBlindChips() * 18);
        return room;
    }

    private static DpRoomBO buildFacing4BetMiddleRoom(String heroCard1, String heroCard2) {
        DpRoomBO room = buildSixMaxTable();
        DpPlayer hero = room.getPlayers().get(0);
        hero.setHoleCards(List.of(heroCard1, heroCard2));
        hero.setChips(150);
        hero.setBet(room.getBigBlindChips() * 6);

        DpPlayer fourBettor = room.getPlayers().get(3);
        fourBettor.setBet(room.getBigBlindChips() * 18);
        fourBettor.setChips(4850);

        room.setRaiseLevel(3);
        room.setCurrentBetToCall(room.getBigBlindChips() * 18);
        return room;
    }

    private static DpRoomBO buildFacing4BetEarlyRoom(String heroCard1, String heroCard2) {
        DpRoomBO room = buildFacing4BetMiddleRoom(heroCard1, heroCard2);
        DpPlayer hero = room.getPlayers().get(0);
        hero.setBet(room.getBigBlindChips() * 2);
        room.setCurrentBetToCall(room.getBigBlindChips() * 18);
        return room;
    }

    private static boolean hasInRangeCell(int[][] cells) {
        if (cells == null) {
            return false;
        }
        for (int[] row : cells) {
            if (row == null) {
                continue;
            }
            for (int cell : row) {
                if (cell != 0) {
                    return true;
                }
            }
        }
        return false;
    }

    private static DpRoomBO buildFacing3BetEarlyRoom(String heroCard1, String heroCard2) {
        DpRoomBO room = buildSixMaxTable();
        DpPlayer hero = room.getPlayers().get(0);
        hero.setHoleCards(List.of(heroCard1, heroCard2));
        hero.setChips(5000);
        hero.setBet(room.getBigBlindChips() * 2);

        DpPlayer threeBettor = room.getPlayers().get(3);
        threeBettor.setBet(room.getBigBlindChips() * 6);
        threeBettor.setChips(4700);

        room.setRaiseLevel(2);
        room.setCurrentBetToCall(room.getBigBlindChips() * 6);
        return room;
    }

    @Test
    void fishPreflop_unopened_hasPrimaryMatrixAndFinalAction() {
        DpRoomBO room = buildUnopenedBtnRoom("hearts_A", "diamonds_K");
        DpPlayer hero = room.getPlayers().get(0);
        hero.setNickname("BOT_FISH_1");

        DpNpcTagDecisionTraceCollector.begin(room, hero);
        DpNpcEngine.BotAction action = DpNpcUnifiedPreflopStrategy.decide(
                room,
                hero,
                0,
                0.0,
                0.55,
                0.15,
                0.72,
                0.12,
                new Random(99L),
                DpNpcEngine.BotType.FISH);
        DpNpcActionTrace trace = DpNpcTagDecisionTraceCollector.build(action);

        assertNotNull(action);
        assertNotNull(trace);
        assertEquals("BOT_FISH_1", trace.actorNickname);
        assertNotNull(trace.finalAction);
        assertNotNull(trace.preflopMatrix);
        assertEquals("openAllow", trace.preflopMatrix.matrixKind);
        assertTrue(trace.steps.stream().anyMatch(s -> "PREFLOP_SPOT".equals(s.code)));
    }

    @Test
    void unopened_hasPrimaryMatrixOnly() {
        DpRoomBO room = buildUnopenedBtnRoom("hearts_A", "diamonds_K");
        DpPlayer hero = room.getPlayers().get(0);

        DpNpcTagDecisionTraceCollector.begin(room, hero);
        DpNpcEngine.BotAction action = DpNpcUnifiedPreflopStrategy.decide(
                room,
                hero,
                0,
                0.0,
                TAG_VPIP,
                TAG_PFR,
                TAG_CALL_STATION,
                TAG_FOLD,
                new Random(42L),
                DpNpcEngine.BotType.TAG);
        DpNpcActionTrace trace = DpNpcTagDecisionTraceCollector.build(action);

        assertNotNull(trace);
        assertNotNull(trace.preflopMatrix);
        assertEquals("openAllow", trace.preflopMatrix.matrixKind);
        assertNull(trace.preflopMatrixSecondary);
        assertNull(trace.raiseMeta);
    }

    private static DpRoomBO buildFacingMinRaiseBtnRoom(String heroCard1, String heroCard2) {
        DpRoomBO room = buildSixMaxTable();
        DpPlayer hero = room.getPlayers().get(0);
        hero.setHoleCards(List.of(heroCard1, heroCard2));
        hero.setChips(5000);
        hero.setBet(0);

        DpPlayer opener = room.getPlayers().get(3);
        opener.setBet(room.getBigBlindChips() * 2);
        opener.setChips(4800);

        room.setRaiseLevel(1);
        room.setCurrentBetToCall(room.getBigBlindChips() * 2);
        return room;
    }

    private static DpRoomBO buildUnopenedBtnRoom(String heroCard1, String heroCard2) {
        DpRoomBO room = buildSixMaxTable();
        DpPlayer hero = room.getPlayers().get(0);
        hero.setHoleCards(List.of(heroCard1, heroCard2));
        hero.setChips(5000);
        hero.setBet(0);
        room.setRaiseLevel(0);
        room.setCurrentBetToCall(0);
        return room;
    }

    private static DpRoomBO buildSixMaxTable() {
        DpRoomBO room = new DpRoomBO();
        room.setPlaying(true);
        room.setCurrentStage("preflop");
        room.setBigBlindChips(10);
        room.setSmallBlindChips(5);
        room.setPot(15);

        for (int i = 0; i < 6; i++) {
            DpPlayer p = new DpPlayer();
            p.setNickname("P" + i);
            p.setChips(5000);
            p.setBet(0);
            p.setFold(false);
            if (i == 0) {
                p.setDealer(true);
            }
            if (i == 1) {
                p.setBlind(1);
                p.setBet(5);
            }
            if (i == 2) {
                p.setBlind(2);
                p.setBet(10);
            }
            room.getPlayers().add(p);
        }
        return room;
    }
}
