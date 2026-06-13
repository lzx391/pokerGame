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
