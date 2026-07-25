package com.example.mgdemoplus.npc.trace;

import com.example.mgdemoplus.common.bo.DpRoomBO;
import com.example.mgdemoplus.common.entity.DpPlayer;
import com.example.mgdemoplus.npc.engine.DpNpcEngine;
import com.example.mgdemoplus.npc.trace.model.DpNpcActionTrace;
import com.example.mgdemoplus.npc.trace.model.DpNpcActionTraceSummary;
import com.example.mgdemoplus.npc.trace.model.DpNpcHandTraceBundle;
import com.example.mgdemoplus.npc.trace.model.DpNpcPreflopMatrixSnapshot;
import com.example.mgdemoplus.npc.trace.model.DpNpcPreflopRaiseMeta;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DpNpcTagDecisionTraceStoreTest {

    @AfterEach
    void tearDown() {
        DpNpcTagDecisionTraceStore.removeRoom("room-a");
        DpNpcTagDecisionTraceStore.ENABLED = true;
    }

    @Test
    void appendFishPreflopAction_queryableByStore() {
        DpRoomBO room = room("room-a", 700L);
        room.getPlayers().get(0).setNickname("BOT_FISH_1");
        DpNpcTagDecisionTraceStore.beginHand(room);
        appendDummyAction(room, "BOT_FISH_1");

        List<DpNpcHandTraceBundle> hands = DpNpcTagDecisionTraceStore.listHandSummaries("room-a", room);
        assertEquals(1, hands.size());
        assertEquals("BOT_FISH_1", hands.get(0).actions.get(0).actorNickname);

        DpNpcHandTraceBundle sealed = DpNpcTagDecisionTraceStore.sealHand(room);
        assertNotNull(sealed);
        assertEquals("BOT_FISH_1", sealed.actions.get(0).actorNickname);
    }

    @Test
    void sealHand_skipsEmptyActionsAndTrimsRingBuffer() {
        DpRoomBO room = room("room-a", 100L);
        DpNpcTagDecisionTraceStore.beginHand(room);

        DpNpcHandTraceBundle empty = DpNpcTagDecisionTraceStore.sealHand(room);
        assertNull(empty);
        assertTrue(DpNpcTagDecisionTraceStore.listHandSummaries("room-a").isEmpty());

        for (int i = 0; i < 105; i++) {
            room.setCurrentHandSeed(200L + i);
            DpNpcTagDecisionTraceStore.beginHand(room);
            appendDummyAction(room, "BOT_TAG_1");
            DpNpcHandTraceBundle bundle = DpNpcTagDecisionTraceStore.sealHand(room);
            assertNotNull(bundle);
            assertEquals(1, bundle.actionCount);
        }

        List<DpNpcHandTraceBundle> hands = DpNpcTagDecisionTraceStore.listHandSummaries("room-a");
        assertEquals(100, hands.size());
        assertTrue(hands.get(0).handSeed >= hands.get(99).handSeed);
    }

    @Test
    void removeRoom_clearsMutableAndSealed() {
        DpRoomBO room = room("room-a", 300L);
        DpNpcTagDecisionTraceStore.beginHand(room);
        appendDummyAction(room, "BOT_TAG_1");
        DpNpcHandTraceBundle bundle = DpNpcTagDecisionTraceStore.sealHand(room);
        assertNotNull(bundle);

        DpNpcTagDecisionTraceStore.removeRoom("room-a");
        assertTrue(DpNpcTagDecisionTraceStore.listHandSummaries("room-a").isEmpty());
        assertNull(DpNpcTagDecisionTraceStore.getHandBundle("room-a", 300L));
    }

    private static DpRoomBO room(String roomId, long handSeed) {
        DpRoomBO room = new DpRoomBO();
        room.setRoomId(roomId);
        room.setCurrentHandSeed(handSeed);
        room.setSmallBlindChips(5);
        room.setBigBlindChips(10);
        room.setCurrentStage("preflop");
        DpPlayer bot = new DpPlayer();
        bot.setNickname("BOT_TAG_1");
        bot.setHoleCards(new ArrayList<>(List.of("hearts_A", "spades_K")));
        room.setPlayers(new ArrayList<>(List.of(bot)));
        return room;
    }

    @Test
    void appendBeforeSeal_visibleInListAndGet() {
        DpRoomBO room = room("room-a", 400L);
        DpNpcTagDecisionTraceStore.beginHand(room);
        appendDummyAction(room, "BOT_TAG_1");

        List<DpNpcHandTraceBundle> hands = DpNpcTagDecisionTraceStore.listHandSummaries("room-a", room);
        assertEquals(1, hands.size());
        assertTrue(hands.get(0).inProgress);
        assertEquals(400L, hands.get(0).handSeed);
        assertEquals(1, hands.get(0).actionCount);

        DpNpcHandTraceBundle bundle = DpNpcTagDecisionTraceStore.getHandBundle("room-a", 400L, room);
        assertNotNull(bundle);
        assertTrue(bundle.inProgress);
        assertEquals(1, bundle.actionCount);

        DpNpcActionTrace detail = DpNpcTagDecisionTraceStore.getActionDetail("room-a", 400L, "x");
        assertNotNull(detail);
    }

    @Test
    void getActionDetail_preservesSecondaryMatrixAndRaiseMeta() {
        DpRoomBO room = room("room-a", 600L);
        DpNpcTagDecisionTraceStore.beginHand(room);

        DpPlayer bot = room.getPlayers().get(0);
        DpNpcActionTrace trace = new DpNpcActionTrace();
        trace.actionId = "facing3bet";
        trace.actionSeq = 1;
        trace.actorNickname = bot.getNickname();
        trace.street = "preflop";
        trace.finalAction = new DpNpcActionTraceSummary.FinalAction("RAISE", 120);
        trace.preflopMatrix = matrixSnapshot("vs3BetContinueAllow", "FACING_3BET", 1);
        trace.preflopMatrixSecondary = matrixSnapshot("vs3Bet4BetValueAllow", "FACING_3BET", 1);
        DpNpcPreflopRaiseMeta meta = new DpNpcPreflopRaiseMeta();
        meta.baseRaiseProb = 0.72;
        meta.matrixKind = "vs3Bet4BetValueAllow";
        meta.valueEligible = true;
        meta.pfrScale = 1.0;
        trace.raiseMeta = meta;
        DpNpcTagDecisionTraceStore.appendAction(
                room, bot, new DpNpcEngine.BotAction(DpNpcEngine.BotActionType.RAISE, 120), trace);

        DpNpcActionTrace detail = DpNpcTagDecisionTraceStore.getActionDetail("room-a", 600L, "facing3bet");
        assertNotNull(detail);
        assertNotNull(detail.preflopMatrixSecondary);
        assertEquals("vs3Bet4BetValueAllow", detail.preflopMatrixSecondary.matrixKind);
        assertNotNull(detail.raiseMeta);
        assertEquals(0.72, detail.raiseMeta.baseRaiseProb, 1e-9);
        assertEquals("vs3Bet4BetValueAllow", detail.raiseMeta.matrixKind);

        DpNpcHandTraceBundle sealed = DpNpcTagDecisionTraceStore.sealHand(room);
        assertNotNull(sealed);
        DpNpcActionTrace sealedDetail = DpNpcTagDecisionTraceStore.getActionDetail("room-a", 600L, "facing3bet");
        assertNotNull(sealedDetail);
        assertNotNull(sealedDetail.preflopMatrixSecondary);
        assertNotNull(sealedDetail.raiseMeta);
    }

    private static DpNpcPreflopMatrixSnapshot matrixSnapshot(String kind, String spot, int inRangeCell) {
        DpNpcPreflopMatrixSnapshot snap = new DpNpcPreflopMatrixSnapshot();
        snap.labels = new String[] {"A", "K", "Q", "J", "T", "9", "8", "7", "6", "5", "4", "3", "2"};
        snap.cells = new int[13][13];
        snap.cells[0][0] = inRangeCell;
        snap.matrixKind = kind;
        snap.spot = spot;
        snap.position = "EARLY";
        snap.rangeLevel = 6;
        return snap;
    }

    @Test
    void sealAfterAppend_stillCorrectAndNoLongerInProgress() {
        DpRoomBO room = room("room-a", 500L);
        DpNpcTagDecisionTraceStore.beginHand(room);
        appendDummyAction(room, "BOT_TAG_1");

        assertEquals(1, DpNpcTagDecisionTraceStore.listHandSummaries("room-a", room).size());

        DpNpcHandTraceBundle sealed = DpNpcTagDecisionTraceStore.sealHand(room);
        assertNotNull(sealed);
        assertEquals(false, sealed.inProgress);

        List<DpNpcHandTraceBundle> hands = DpNpcTagDecisionTraceStore.listHandSummaries("room-a", room);
        assertEquals(1, hands.size());
        assertEquals(false, hands.get(0).inProgress);
        assertEquals(500L, hands.get(0).handSeed);

        DpNpcHandTraceBundle bundle = DpNpcTagDecisionTraceStore.getHandBundle("room-a", 500L, room);
        assertNotNull(bundle);
        assertEquals(false, bundle.inProgress);
        assertNotNull(DpNpcTagDecisionTraceStore.getActionDetail("room-a", 500L, "x"));
    }

    private static void appendDummyAction(DpRoomBO room, String botNick) {
        DpPlayer bot = room.getPlayers().get(0);
        DpNpcActionTrace trace = new DpNpcActionTrace();
        trace.actionId = "x";
        trace.actionSeq = 1;
        trace.actorNickname = botNick;
        trace.street = "preflop";
        trace.finalAction = new DpNpcActionTraceSummary.FinalAction("CALL_OR_CHECK", 0);
        DpNpcTagDecisionTraceStore.appendAction(
                room, bot, new DpNpcEngine.BotAction(DpNpcEngine.BotActionType.CALL_OR_CHECK, 0), trace);
    }
}
