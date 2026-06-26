package com.example.mgdemoplus.room.support;

import com.example.mgdemoplus.common.bo.DpRoomBO;
import com.example.mgdemoplus.common.entity.DpPlayer;
import com.example.mgdemoplus.npc.llm.DpLlmNpcDecisionService;
import com.example.mgdemoplus.websocket.DpGameRoomPushService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * 真人行动超时使用房间 {@link DpRoomBO#getThinkTimeSeconds()}，而非固定 30s。
 */
class DpRoomThinkTimeTimeoutTest {

    @Test
    void actionTimeoutMs_scalesWithThinkTimeSeconds() {
        DpRoomBO room = new DpRoomBO();
        room.setThinkTimeSeconds(15);
        assertEquals(15_000L, room.getActionTimeoutMs());

        room.setThinkTimeSeconds(DpRoomBO.DEFAULT_THINK_TIME_SECONDS);
        assertEquals(30_000L, room.getActionTimeoutMs());
    }

    @Test
    void heartbeat_foldsHumanAfterThinkTimeElapsed() {
        DpRoomBO room = playingRoomWithHumanActor(15);
        DpPlayer actor = room.getPlayers().get(0);
        long now = System.currentTimeMillis();
        room.setLastActionTime(now - room.getActionTimeoutMs() - 1L);
        tickHumanActionTimeout(room);
        assertTrue(actor.isFold(), "15s room should fold after think time elapsed");
    }

    @Test
    void heartbeat_doesNotFoldHumanBeforeThinkTimeElapsed() {
        DpRoomBO room = playingRoomWithHumanActor(15);
        DpPlayer actor = room.getPlayers().get(0);
        long now = System.currentTimeMillis();
        room.setLastActionTime(now - room.getActionTimeoutMs() + 2_000L);
        tickHumanActionTimeout(room);
        assertFalse(actor.isFold(), "15s room must not fold while 2s remain");
    }

    @Test
    void heartbeat_uses30sWhenDefaultThinkTime() {
        DpRoomBO room = playingRoomWithHumanActor(DpRoomBO.DEFAULT_THINK_TIME_SECONDS);
        DpPlayer actor = room.getPlayers().get(0);
        long now = System.currentTimeMillis();
        room.setLastActionTime(now - 20_000L);
        tickHumanActionTimeout(room);
        assertFalse(actor.isFold(), "default 30s room must not fold at 20s elapsed");

        room.setLastActionTime(now - room.getActionTimeoutMs() - 1L);
        tickHumanActionTimeout(room);
        assertTrue(actor.isFold(), "default 30s room should fold after 30s elapsed");
    }

    private static DpRoomBO playingRoomWithHumanActor(int thinkTimeSeconds) {
        DpRoomBO room = new DpRoomBO();
        room.setRoomId("test-room");
        room.setThinkTimeSeconds(thinkTimeSeconds);
        room.setPlaying(true);
        room.setCurrentActorIndex(0);
        DpPlayer human = new DpPlayer();
        human.setNickname("alice");
        human.setLastHeartBeat(System.currentTimeMillis());
        human.setFold(false);
        room.getPlayers().add(human);
        return room;
    }

    private static void tickHumanActionTimeout(DpRoomBO room) {
        DpRoomHeartbeatScheduler scheduler = new DpRoomHeartbeatScheduler(
                mock(DpRoomRegistry.class),
                mock(DpLlmNpcDecisionService.class),
                mock(DpGameRoomPushService.class),
                mock(DpRoomLobbySync.class),
                mock(DpRoomServiceCallbacks.class),
                null);
        scheduler.runGlobalSecondTickForSingleRoom(room);
    }
}
