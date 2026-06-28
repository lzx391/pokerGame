package com.example.mgdemoplus.room.impl;

import com.example.mgdemoplus.common.bo.DpRoomBO;
import com.example.mgdemoplus.common.entity.DpPlayer;
import com.example.mgdemoplus.common.mapper.DpUserMapper;
import com.example.mgdemoplus.history.DpHandHistoryObservedService;
import com.example.mgdemoplus.history.DpHandHistoryPersistService;
import com.example.mgdemoplus.leaderboard.impl.DpLeaderboardWeeklyWriteService;
import com.example.mgdemoplus.lobby.DpRoomHallService;
import com.example.mgdemoplus.npc.llm.DpLlmNpcDecisionService;
import com.example.mgdemoplus.npc.llm.LlmNpcGlobalHandConversationStore;
import com.example.mgdemoplus.npc.tabletalk.DpNpcTableTalkService;
import com.example.mgdemoplus.presence.DpFriendPresenceService;
import com.example.mgdemoplus.room.support.DpRoomHeartbeatScheduler;
import com.example.mgdemoplus.room.support.DpRoomRegistry;
import com.example.mgdemoplus.room.support.DpRoomTestSupport;
import com.example.mgdemoplus.room.support.DpSettlePersistenceDispatcher;
import com.example.mgdemoplus.roomchat.DpRoomChatPersistenceService;
import com.example.mgdemoplus.roomchat.buffer.RoomChatBuffer;
import com.example.mgdemoplus.user.mapper.DpUserStatsMapper;
import com.example.mgdemoplus.websocket.DpGameRoomPushService;
import com.example.mgdemoplus.websocket.DpQuickMatchPushService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * settled 阶段 1s tick 应对 busted NPC 自动 rebuy（与 {@link DpRoomHeartbeatScheduler} 口径一致）。
 */
class DpRoomNpcRebuyAfterSettleTest {

    private DpRoomServiceImpl svc;
    private DpRoomRegistry registry;
    private DpRoomHeartbeatScheduler scheduler;

    @BeforeEach
    void setUp() {
        DpRoomServiceImpl.suppressGlobalRoomTimerForTests = true;
        registry = DpRoomTestSupport.memoryRegistry();
        svc = new DpRoomServiceImpl(
                registry,
                mock(DpHandHistoryPersistService.class),
                mock(DpSettlePersistenceDispatcher.class),
                mock(DpLlmNpcDecisionService.class),
                mock(DpNpcTableTalkService.class),
                mock(com.example.mgdemoplus.npc.mood.DpNpcMoodProperties.class),
                mock(DpGameRoomPushService.class),
                mock(DpUserMapper.class),
                mock(DpUserStatsMapper.class),
                mock(DpLeaderboardWeeklyWriteService.class),
                mock(DpHandHistoryObservedService.class),
                new LlmNpcGlobalHandConversationStore(),
                mock(DpRoomHallService.class),
                new ObjectMapper(),
                mock(DpQuickMatchPushService.class),
                mock(DpFriendPresenceService.class),
                new RoomChatBuffer(),
                mock(DpRoomChatPersistenceService.class),
                mock(com.example.mgdemoplus.moderation.DpSensitiveWordService.class),
                null,
                mock(com.example.mgdemoplus.npc.trace.DpNpcTagDecisionTracePushService.class),
                mock(com.example.mgdemoplus.rbac.DpPermissionService.class),
                DpRoomTestSupport.defaultInstanceProperties(),
                DpRoomTestSupport.joinableQuickMatchRoomIndex(),
                DpRoomTestSupport.mockQuickMatchWaitQueue(),
                DpRoomTestSupport.mockQuickMatchPairingLock(),
                DpRoomTestSupport.mockQuickMatchEventPublisher(),
                null);
        scheduler = new DpRoomHeartbeatScheduler(
                registry,
                mock(DpLlmNpcDecisionService.class),
                mock(DpGameRoomPushService.class),
                mock(com.example.mgdemoplus.room.support.DpRoomLobbySync.class),
                svc,
                null);
    }

    @AfterEach
    void tearDown() {
        DpRoomServiceImpl.suppressGlobalRoomTimerForTests = false;
    }

    @Test
    void settledTickRebuysBustedNpcOnTable() {
        DpRoomBO r = new DpRoomBO();
        r.setRoomId("r-npc-rebuy");
        r.setPlaying(true);
        r.setCurrentStage("settled");
        r.setBigBlindChips(10);
        r.setStartingChips(500);
        r.setSettledAtMs(System.currentTimeMillis());
        DpPlayer bot = new DpPlayer();
        bot.setNickname("BOT_FISH_1");
        bot.setChips(2);
        r.getPlayers().add(bot);
        registry.put(r.getRoomId(), r);

        scheduler.runGlobalSecondTickForSingleRoom(r);

        assertEquals(500, bot.getChips(), "settled tick should rebuy busted NPC to starting stack");
        assertTrue(svc.rebuy(r.getRoomId(), "BOT_FISH_1") == false,
                "second rebuy is no-op when already above big blind");
    }
}
